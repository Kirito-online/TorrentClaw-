package com.example.data.web

import java.io.File

sealed interface PageLoadOutcome {
  data class Success(val page: ParsedWebsitePage) : PageLoadOutcome

  data class MagnetIntercepted(val target: MagnetTarget) : PageLoadOutcome

  data class TorrentFileIntercepted(val target: MagnetTarget) : PageLoadOutcome

  data class Blocked(val blockedUrl: String, val reason: String) : PageLoadOutcome

  data class Failure(val url: String, val message: String) : PageLoadOutcome
}

/**
 * Loads torrentclaw.com HTML pages through WebsiteHttpClient (+ offscreen DOM fallback if SPA shell),
 * validates redirects via NavigationGuard, and produces ParsedWebsitePage models for Compose UI.
 */
class WebsitePageRepository(
  private val httpClient: WebsiteHttpClient,
  private val domExtractor: OffscreenDomExtractor?,
) {

  suspend fun loadUrl(rawUrl: String, currentUrl: String = NavigationGuard.HOME_URL): PageLoadOutcome {
    return when (val decision = NavigationGuard.classify(rawUrl, currentUrl)) {
      is UrlDecision.MagnetAction ->
        PageLoadOutcome.MagnetIntercepted(
          MagnetTarget(
            title = decision.displayName ?: "Magnet Link",
            magnetUrl = decision.magnetUri,
          )
        )
      is UrlDecision.TorrentFileAction ->
        PageLoadOutcome.TorrentFileIntercepted(
          MagnetTarget(
            title = decision.fileName ?: "Torrent File",
            torrentDownloadUrl = decision.torrentUrl,
          )
        )
      is UrlDecision.BlockedExternal ->
        PageLoadOutcome.Blocked(decision.rawUrl, decision.reason)
      is UrlDecision.InternalPage ->
        handleHttpResult(httpClient.fetchPage(decision.normalizedUrl))
    }
  }

  suspend fun submitForm(
    form: WebFormModel,
    fieldValues: Map<String, String>,
    currentUrl: String,
  ): PageLoadOutcome {
    val actionDecision = NavigationGuard.classify(form.actionUrl, currentUrl)
    if (actionDecision is UrlDecision.BlockedExternal) {
      return PageLoadOutcome.Blocked(actionDecision.rawUrl, actionDecision.reason)
    }
    val safeAction = (actionDecision as? UrlDecision.InternalPage)?.normalizedUrl ?: currentUrl

    val orderedParams =
      form.fields.map { field ->
        val submitted =
          if (field.isHidden) field.value else (fieldValues[field.name] ?: field.value)
        field.name to submitted
      }
    return handleHttpResult(
      httpClient.submitForm(
        actionUrl = safeAction,
        method = form.method,
        params = orderedParams,
        referer = currentUrl,
      )
    )
  }

  suspend fun downloadTorrentFile(
    torrentUrl: String,
    cacheDir: File,
    suggestedName: String?,
  ): Result<File> = httpClient.downloadTorrentFile(torrentUrl, cacheDir, suggestedName)

  fun hasSessionCookies(): Boolean = httpClient.cookieJar.hasSessionCookies()

  fun clearSessionCookies() = httpClient.cookieJar.clearSessionCookies()

  private suspend fun handleHttpResult(result: HttpFetchResult): PageLoadOutcome {
    return when (result) {
      is HttpFetchResult.BlockedRedirect ->
        PageLoadOutcome.Blocked(result.blockedUrl, result.reason)
      is HttpFetchResult.MagnetRedirect ->
        PageLoadOutcome.MagnetIntercepted(
          MagnetTarget(
            title = result.displayName ?: "Magnet Link",
            magnetUrl = result.magnetUri,
          )
        )
      is HttpFetchResult.TorrentRedirect ->
        PageLoadOutcome.TorrentFileIntercepted(
          MagnetTarget(
            title = result.fileName ?: "Torrent File",
            torrentDownloadUrl = result.torrentUrl,
          )
        )
      is HttpFetchResult.HttpError ->
        PageLoadOutcome.Failure(result.url, result.message)
      is HttpFetchResult.HtmlSuccess -> {
        val htmlToParse =
          if (HtmlPageParser.needsClientDomSnapshot(result.html) && domExtractor != null) {
            domExtractor.captureRenderedHtml(result.finalUrl) ?: result.html
          } else {
            result.html
          }
        val parsed = HtmlPageParser.parse(result.finalUrl, htmlToParse)
        PageLoadOutcome.Success(parsed)
      }
    }
  }
}
