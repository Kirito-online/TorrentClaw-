package com.example.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.core.security.PersistentCookieJar
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Locked-down, offscreen DOM snapshot bridge used ONLY as a contingency when torrentclaw.com
 * serves a minimal client-side JS shell instead of server-rendered HTML.
 * Enforces NavigationGuard popup/redirect blocking and shares cookies with PersistentCookieJar.
 */
class OffscreenDomExtractor(
  private val context: Context,
  private val cookieJar: PersistentCookieJar,
) {

  @SuppressLint("SetJavaScriptEnabled")
  suspend fun captureRenderedHtml(url: String): String? =
    withContext(Dispatchers.Main) {
      val safeDecision = NavigationGuard.classify(url) as? UrlDecision.InternalPage ?: return@withContext null
      cookieJar.syncToWebKitCookieManager(safeDecision.normalizedUrl)

      withTimeoutOrNull(MAX_RENDER_WAIT_MS) {
        suspendCancellableCoroutine { continuation ->
          val webView =
            runCatching { WebView(context.applicationContext) }.getOrNull()
          if (webView == null) {
            if (continuation.isActive) continuation.resume(null)
            return@suspendCancellableCoroutine
          }

          var finished = false
          fun finishWith(html: String?) {
            if (finished) return
            finished = true
            cookieJar.importFromWebKitCookieManager(safeDecision.normalizedUrl)
            runCatching {
              webView.stopLoading()
              webView.destroy()
            }
            if (continuation.isActive) continuation.resume(html)
          }

          webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
          }

          webView.webViewClient =
            object : WebViewClient() {
              override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
              ): Boolean {
                val target = request?.url?.toString() ?: return true
                return when (NavigationGuard.classify(target, safeDecision.normalizedUrl)) {
                  is UrlDecision.InternalPage -> false
                  else -> true // Block all external redirects, popups, and non-HTTP schemes
                }
              }

              override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
              ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return emptyBlockedResponse()
                if (!NavigationGuard.isAllowedAssetUrl(reqUrl)) {
                  return emptyBlockedResponse()
                }
                return super.shouldInterceptRequest(view, request)
              }

              override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                if (finished || view == null) return
                pollDomReady(view, attemptsRemaining = 8, onReady = ::finishWith)
              }
            }

          continuation.invokeOnCancellation {
            runCatching {
              webView.stopLoading()
              webView.destroy()
            }
          }

          webView.loadUrl(safeDecision.normalizedUrl)
        }
      }
    }

  private fun pollDomReady(
    webView: WebView,
    attemptsRemaining: Int,
    onReady: (String?) -> Unit,
  ) {
    webView.evaluateJavascript(
      "(function(){var t=(document.body&&document.body.innerText)?document.body.innerText.trim():'';return JSON.stringify({len:t.length,html:document.documentElement?document.documentElement.outerHTML:''});})()",
    ) { raw ->
      val parsedLen = extractLen(raw)
      val html = extractHtml(raw)
      if (parsedLen >= 100 || attemptsRemaining <= 0) {
        onReady(html?.takeIf { it.isNotBlank() })
      } else {
        webView.postDelayed(
          { pollDomReady(webView, attemptsRemaining - 1, onReady) },
          350L,
        )
      }
    }
  }

  private fun extractLen(jsResult: String?): Int {
    if (jsResult.isNullOrBlank() || jsResult == "null") return 0
    val unescaped = unescapeJsString(jsResult)
    val match = Regex("\"len\"\\s*:\\s*(\\d+)").find(unescaped) ?: return 0
    return match.groupValues[1].toIntOrNull() ?: 0
  }

  private fun extractHtml(jsResult: String?): String? {
    if (jsResult.isNullOrBlank() || jsResult == "null") return null
    val unescaped = unescapeJsString(jsResult)
    val marker = "\"html\":\""
    val start = unescaped.indexOf(marker)
    if (start < 0) return null
    val content = unescaped.substring(start + marker.length)
    val end = content.lastIndexOf("\"}")
    if (end <= 0) return null
    return content
      .substring(0, end)
      .replace("\\u003C", "<")
      .replace("\\u003c", "<")
      .replace("\\u003E", ">")
      .replace("\\u003e", ">")
      .replace("\\\"", "\"")
      .replace("\\n", "\n")
      .replace("\\t", "\t")
      .replace("\\\\", "\\")
  }

  private fun unescapeJsString(raw: String): String {
    var trimmed = raw.trim()
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
      trimmed =
        trimmed
          .substring(1, trimmed.length - 1)
          .replace("\\\"", "\"")
          .replace("\\\\", "\\")
    }
    return trimmed
  }

  private fun emptyBlockedResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

  companion object {
    private const val MAX_RENDER_WAIT_MS = 5500L
  }
}
