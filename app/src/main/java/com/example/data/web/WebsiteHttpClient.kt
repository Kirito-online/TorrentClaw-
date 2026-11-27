package com.example.data.web

import com.example.core.security.PersistentCookieJar
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface HttpFetchResult {
  data class HtmlSuccess(
    val finalUrl: String,
    val html: String,
    val statusCode: Int,
  ) : HttpFetchResult

  data class MagnetRedirect(val magnetUri: String, val displayName: String?) : HttpFetchResult

  data class TorrentRedirect(val torrentUrl: String, val fileName: String?) : HttpFetchResult

  data class BlockedRedirect(val blockedUrl: String, val reason: String) : HttpFetchResult

  data class HttpError(val url: String, val message: String, val code: Int? = null) :
    HttpFetchResult
}

/**
 * Pure HTTP page fetcher using PersistentCookieJar and strict manual redirect inspection
 * so no 3xx redirect can silently bounce users off torrentclaw.com to external popups.
 */
class WebsiteHttpClient(
  val cookieJar: PersistentCookieJar,
) {

  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .cookieJar(cookieJar)
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()

  suspend fun fetchPage(url: String): HttpFetchResult =
    withContext(Dispatchers.IO) {
      executeWithRedirectGuard(
        initialUrl = url,
        method = "GET",
        formParams = emptyList(),
        referer = NavigationGuard.HOME_URL,
      )
    }

  suspend fun submitForm(
    actionUrl: String,
    method: String,
    params: List<Pair<String, String>>,
    referer: String,
  ): HttpFetchResult =
    withContext(Dispatchers.IO) {
      val upperMethod = method.uppercase()
      if (upperMethod == "POST") {
        executeWithRedirectGuard(
          initialUrl = actionUrl,
          method = "POST",
          formParams = params,
          referer = referer,
        )
      } else {
        val queryString =
          params
            .filter { (k, _) -> k.isNotBlank() }
            .joinToString("&") { (k, v) ->
              "${URLEncoder.encode(k, StandardCharsets.UTF_8.name())}=${
                URLEncoder.encode(v, StandardCharsets.UTF_8.name())
              }"
            }
        val baseClean = actionUrl.substringBefore('#').substringBefore('?')
        val target = if (queryString.isNotBlank()) "$baseClean?$queryString" else baseClean
        executeWithRedirectGuard(
          initialUrl = target,
          method = "GET",
          formParams = emptyList(),
          referer = referer,
        )
      }
    }

  suspend fun downloadTorrentFile(
    torrentUrl: String,
    cacheDir: File,
    suggestedName: String?,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val checked = NavigationGuard.classify(torrentUrl)
        val safeUrl =
          when (checked) {
            is UrlDecision.TorrentFileAction -> checked.torrentUrl
            is UrlDecision.InternalPage -> checked.normalizedUrl
            is UrlDecision.BlockedExternal -> error(checked.reason)
            is UrlDecision.MagnetAction -> error("Target is a magnet URI, not a .torrent file")
          }

        val request =
          Request.Builder()
            .url(safeUrl)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "application/x-bittorrent, application/octet-stream, */*")
            .build()

        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            error("Download failed (HTTP ${response.code})")
          }
          val dir = File(cacheDir, "torrents").apply { mkdirs() }
          val cleanName =
            (suggestedName ?: "release_${System.currentTimeMillis()}.torrent")
              .replace(Regex("[^a-zA-Z0-9._-]"), "_")
              .let { if (it.endsWith(".torrent", ignoreCase = true)) it else "$it.torrent" }
          val outFile = File(dir, cleanName)
          val bytes = response.body?.bytes() ?: error("Empty .torrent file")
          outFile.writeBytes(bytes)
          outFile
        }
      }
    }

  private fun executeWithRedirectGuard(
    initialUrl: String,
    method: String,
    formParams: List<Pair<String, String>>,
    referer: String,
  ): HttpFetchResult {
    var currentUrl = initialUrl
    var currentMethod = method
    var currentReferer = referer
    var hops = 0

    while (hops < MAX_REDIRECTS) {
      when (val decision = NavigationGuard.classify(currentUrl, currentReferer)) {
        is UrlDecision.BlockedExternal ->
          return HttpFetchResult.BlockedRedirect(decision.rawUrl, decision.reason)
        is UrlDecision.MagnetAction ->
          return HttpFetchResult.MagnetRedirect(decision.magnetUri, decision.displayName)
        is UrlDecision.TorrentFileAction ->
          return HttpFetchResult.TorrentRedirect(decision.torrentUrl, decision.fileName)
        is UrlDecision.InternalPage -> currentUrl = decision.normalizedUrl
      }

      val reqBuilder =
        Request.Builder()
          .url(currentUrl)
          .header("User-Agent", MOBILE_USER_AGENT)
          .header(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          )
          .header("Accept-Language", "en-US,en;q=0.9")
          .header("Referer", currentReferer)

      if (currentMethod == "POST") {
        val bodyBuilder = FormBody.Builder(StandardCharsets.UTF_8)
        for ((k, v) in formParams) {
          if (k.isNotBlank()) bodyBuilder.add(k, v)
        }
        reqBuilder.header("Origin", NavigationGuard.BASE_ORIGIN)
        reqBuilder.post(bodyBuilder.build())
      } else {
        reqBuilder.get()
      }

      val result =
        runCatching {
            client.newCall(reqBuilder.build()).execute().use { response ->
              val code = response.code
              if (code in 300..399) {
                val location = response.header("Location")
                if (location.isNullOrBlank()) {
                  return@use HttpFetchResult.HttpError(
                    currentUrl,
                    "Server returned incomplete redirect ($code)",
                    code,
                  )
                }
                currentReferer = currentUrl
                currentUrl = location
                if (code == 301 || code == 302 || code == 303) {
                  currentMethod = "GET"
                }
                hops++
                null
              } else {
                val bodyString = response.body?.string().orEmpty()
                if (code >= 400 && bodyString.isBlank()) {
                  HttpFetchResult.HttpError(
                    currentUrl,
                    "Website returned HTTP $code",
                    code,
                  )
                } else {
                  HttpFetchResult.HtmlSuccess(
                    finalUrl = currentUrl,
                    html = bodyString,
                    statusCode = code,
                  )
                }
              }
            }
          }
          .getOrElse { err ->
            return HttpFetchResult.HttpError(
              currentUrl,
              err.localizedMessage ?: "Unable to connect to torrentclaw.com",
            )
          }

      if (result != null) return result
    }

    return HttpFetchResult.HttpError(currentUrl, "Too many redirects")
  }

  companion object {
    private const val MAX_REDIRECTS = 5
    private const val MOBILE_USER_AGENT =
      "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
  }
}
