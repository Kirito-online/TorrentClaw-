package com.example.data.network

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response

class TorrentClawHttpException(val statusCode: Int, message: String) : IllegalStateException(message)

/**
 * Retrofit-backed networking service wrapping primary and mirror TorrentClaw hosts
 * with automatic failover, rate-limit messaging, and streaming .torrent downloads.
 */
open class TorrentClawNetworkService(
  val okHttpClient: OkHttpClient = TorrentClawNetworkModule.createApiOkHttpClient(),
) {

  private val primaryService: TorrentClawApiService =
    TorrentClawNetworkModule.createApiService(TorrentClawNetworkModule.PRIMARY_BASE_URL, okHttpClient)

  private val mirrorService: TorrentClawApiService =
    TorrentClawNetworkModule.createApiService(TorrentClawNetworkModule.MIRROR_BASE_URL, okHttpClient)

  private val services = listOf(primaryService, mirrorService)

  suspend fun fetchPopular(type: String? = null, page: Int? = null, limit: Int? = null): Result<String> =
    executeWithMirrorFallback { it.getPopular(type = type, page = page, limit = limit) }

  suspend fun fetchRecent(type: String? = null, page: Int? = null, limit: Int? = null): Result<String> =
    executeWithMirrorFallback { it.getRecent(type = type, page = page, limit = limit) }

  open suspend fun search(params: Map<String, String?>): Result<String> {
    val cleanParams = params.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    return executeWithMirrorFallback { it.search(cleanParams) }
  }

  suspend fun autocomplete(query: String, limit: Int = 8): Result<String> =
    executeWithMirrorFallback { it.autocomplete(query = query, limit = limit) }

  suspend fun fetchGenres(): Result<String> =
    executeWithMirrorFallback { it.getGenres() }

  suspend fun fetchCollections(page: Int? = null, limit: Int? = null): Result<String> =
    executeWithMirrorFallback { it.getCollections(page = page, limit = limit) }

  suspend fun fetchCollectionDetail(collectionId: String): Result<String> =
    executeWithMirrorFallback { it.getCollectionDetail(collectionId) }

  suspend fun fetchStats(): Result<String> =
    executeWithMirrorFallback { it.getStats() }

  suspend fun fetchContentDetail(id: String, params: Map<String, String?> = emptyMap()): Result<String> {
    val cleanParams = params.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    return executeWithMirrorFallback { it.getContentDetail(id, cleanParams) }
  }

  suspend fun fetchContentTorrents(id: String, params: Map<String, String?> = emptyMap()): Result<String> {
    val cleanParams = params.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    return executeWithMirrorFallback { it.getContentTorrents(id, cleanParams) }
  }

  suspend fun fetchContentCredits(id: String): Result<String> =
    executeWithMirrorFallback { it.getContentCredits(id) }

  suspend fun fetchContentWatchProviders(id: String, country: String = "US"): Result<String> =
    executeWithMirrorFallback { it.getContentWatchProviders(id, country) }

  suspend fun fetchWatchProvidersByTmdb(tmdbId: String, country: String = "US"): Result<String> =
    executeWithMirrorFallback { it.getWatchProviders(tmdbId, country) }

  open suspend fun fetchPublicContentPage(contentPath: String): Result<String> =
    withContext(Dispatchers.IO) {
      val trimmed = contentPath.trim()
      if (trimmed.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Blank content path"))
      }
      val normalizedPath =
        when {
          trimmed.startsWith("https://torrentclaw.com", ignoreCase = true) ->
            trimmed.substringAfter("torrentclaw.com")
          trimmed.startsWith("https://torrentclaw.to", ignoreCase = true) ->
            trimmed.substringAfter("torrentclaw.to")
          trimmed.startsWith("http", ignoreCase = true) -> null
          trimmed.startsWith("/") -> trimmed
          else -> "/$trimmed"
        }
      val candidateUrls =
        if (normalizedPath != null) {
          listOf(
            "https://torrentclaw.to$normalizedPath",
            "https://torrentclaw.com$normalizedPath",
          )
        } else {
          listOf(trimmed)
        }

      var lastError: Throwable? = null
      for (targetUrl in candidateUrls) {
        val attempt =
          runCatching {
            val response = primaryService.fetchHtmlPage(targetUrl)
            val body = response.body()?.use { it.string() }.orEmpty()
            if (!response.isSuccessful) {
              response.errorBody()?.close()
              throw TorrentClawHttpException(response.code(), "Page returned HTTP ${response.code()}")
            }
            if (body.isBlank()) error("Empty HTML page")
            body
          }
        if (attempt.isSuccess) return@withContext attempt
        lastError = attempt.exceptionOrNull()
      }
      Result.failure(lastError ?: IllegalStateException("Unable to load public content page"))
    }

  suspend fun downloadTorrentFile(
    infoHash: String,
    directUrl: String?,
    cacheDir: File,
    suggestedName: String,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val candidates =
        buildList {
          if (!directUrl.isNullOrBlank() && directUrl.startsWith("https://", ignoreCase = true)) {
            add(directUrl)
          }
          if (infoHash.isNotBlank()) {
            add("${TorrentClawNetworkModule.PRIMARY_BASE_URL}torrent/${infoHash.trim()}")
            add("${TorrentClawNetworkModule.MIRROR_BASE_URL}torrent/${infoHash.trim()}")
          }
        }
      if (candidates.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("No .torrent source available"))
      }

      var lastError: Throwable? = null
      for (targetUrl in candidates) {
        val attempt =
          runCatching {
            val response = primaryService.downloadFile(targetUrl)
            if (!response.isSuccessful) {
              error("Download failed (HTTP ${response.code()})")
            }
            val bytes = response.body()?.use { it.bytes() } ?: error("Empty .torrent file")
            val dir = File(cacheDir, "torrents").apply { mkdirs() }
            val safeName =
              suggestedName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).let {
                if (it.endsWith(".torrent", ignoreCase = true)) it else "$it.torrent"
              }
            val out = File(dir, safeName)
            out.writeBytes(bytes)
            out
          }
        if (attempt.isSuccess) return@withContext attempt
        lastError = attempt.exceptionOrNull()
      }
      Result.failure(lastError ?: IllegalStateException("Failed to download .torrent file"))
    }

  private suspend fun executeWithMirrorFallback(
    call: suspend (TorrentClawApiService) -> Response<ResponseBody>,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      var lastError: Throwable? = null
      for (service in services) {
        val attempt =
          runCatching {
            val response = call(service)
            val bodyText = response.body()?.use { it.string() }.orEmpty()
            if (!response.isSuccessful) {
              response.errorBody()?.close()
              val code = response.code()
              if (code == 429) {
                throw TorrentClawHttpException(429, "Rate limit reached briefly. Pull to retry in a moment.")
              }
              throw TorrentClawHttpException(code, "Service returned HTTP $code")
            }
            val trimmed = bodyText.trimStart()
            if (trimmed.startsWith("<")) {
              error("Unexpected HTML response")
            }
            bodyText
          }
        if (attempt.isSuccess) return@withContext attempt
        val err = attempt.exceptionOrNull()
        lastError = err
        // Do not amplify 4xx/429 responses by retrying the mirror
        if (err is TorrentClawHttpException && err.statusCode in 400..499) {
          return@withContext Result.failure(err)
        }
      }
      Result.failure(lastError ?: IllegalStateException("Unable to reach TorrentClaw"))
    }
}
