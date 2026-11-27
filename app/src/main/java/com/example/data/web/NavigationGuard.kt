package com.example.data.web

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface UrlDecision {
  data class InternalPage(val normalizedUrl: String) : UrlDecision

  data class MagnetAction(val magnetUri: String, val displayName: String?) : UrlDecision

  data class TorrentFileAction(val torrentUrl: String, val fileName: String?) : UrlDecision

  data class BlockedExternal(
    val rawUrl: String,
    val host: String?,
    val reason: String,
  ) : UrlDecision
}

/**
 * Security boundary enforcing strict torrentclaw.com navigation,
 * native interception for magnet/.torrent links, and blocking of external popups/redirects.
 */
object NavigationGuard {

  const val BASE_ORIGIN = "https://torrentclaw.com"
  const val HOME_URL = "https://torrentclaw.com/"

  private val blockedSchemes =
    setOf("javascript", "data", "file", "vbscript", "content", "intent", "about", "blob")

  fun isAllowedHost(host: String?): Boolean {
    val clean = host?.trim()?.lowercase() ?: return false
    return clean == "torrentclaw.com" ||
      clean == "www.torrentclaw.com" ||
      clean.endsWith(".torrentclaw.com")
  }

  fun classify(rawUrl: String, baseUrl: String = HOME_URL): UrlDecision {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty() || trimmed == "#") {
      return UrlDecision.BlockedExternal(rawUrl, null, "Ignored empty navigation target")
    }

    if (trimmed.startsWith("magnet:", ignoreCase = true)) {
      val dn = extractMagnetDisplayName(trimmed)
      return UrlDecision.MagnetAction(magnetUri = trimmed, displayName = dn)
    }

    val lower = trimmed.lowercase()
    val colonIdx = lower.indexOf(':')
    val slashIdx = lower.indexOf('/')
    if (colonIdx > 0 && (slashIdx < 0 || colonIdx < slashIdx)) {
      val scheme = lower.substring(0, colonIdx)
      if (scheme in blockedSchemes) {
        return UrlDecision.BlockedExternal(
          rawUrl = trimmed,
          host = null,
          reason = "Blocked unsafe '$scheme:' link",
        )
      }
      if (scheme != "http" && scheme != "https" && scheme != "torrentclaw") {
        return UrlDecision.BlockedExternal(
          rawUrl = trimmed,
          host = null,
          reason = "Blocked external scheme '$scheme:'",
        )
      }
    }

    val resolvedString =
      if (trimmed.startsWith("torrentclaw://", ignoreCase = true)) {
        val tail = trimmed.substring("torrentclaw://".length).trimStart('/')
        "$BASE_ORIGIN/$tail"
      } else {
        resolveUrl(baseUrl, trimmed)
      }
        ?: return UrlDecision.BlockedExternal(trimmed, null, "Malformed URL")

    val httpUrl =
      resolvedString.toHttpUrlOrNull()
        ?: return UrlDecision.BlockedExternal(resolvedString, null, "Unsupported URL")

    if (!isAllowedHost(httpUrl.host)) {
      return UrlDecision.BlockedExternal(
        rawUrl = resolvedString,
        host = httpUrl.host,
        reason = "Blocked external redirect/popup to ${httpUrl.host}",
      )
    }

    // Upgrade http://torrentclaw.com -> https://torrentclaw.com
    val normalized =
      httpUrl.newBuilder().scheme("https").fragment(null).build().toString()

    if (isTorrentDownloadUrl(httpUrl.encodedPath, normalized)) {
      val fileName =
        httpUrl.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }?.let { seg ->
          if (seg.endsWith(".torrent", ignoreCase = true)) seg else "$seg.torrent"
        }
      return UrlDecision.TorrentFileAction(torrentUrl = normalized, fileName = fileName)
    }

    return UrlDecision.InternalPage(normalizedUrl = normalized)
  }

  fun isTorrentDownloadUrl(encodedPath: String, fullUrl: String): Boolean {
    val cleanPath = encodedPath.lowercase()
    if (cleanPath.endsWith(".torrent")) return true
    if (cleanPath.startsWith("/torrent/") || cleanPath.contains("/download-torrent")) return true
    if (fullUrl.contains(".torrent?", ignoreCase = true)) return true
    return false
  }

  fun isAllowedAssetUrl(rawUrl: String): Boolean {
    val httpUrl = rawUrl.toHttpUrlOrNull() ?: return false
    if (isAllowedHost(httpUrl.host)) return true
    val path = httpUrl.encodedPath.lowercase()
    val host = httpUrl.host.lowercase()
    // Allow TMDB poster images and standard stylesheet/font CDNs if loaded by page DOM fallback,
    // never tracking/popup scripts or external HTML documents.
    val isTrustedPosterHost = host == "image.tmdb.org" || host.endsWith(".tmdb.org")
    val isStaticStyleOrFont =
      path.endsWith(".css") || path.endsWith(".woff2") || path.endsWith(".woff") || path.endsWith(".png") ||
        path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") || path.endsWith(".svg")
    return isTrustedPosterHost || isStaticStyleOrFont
  }

  fun formatDisplayPath(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return "torrentclaw.com/"
    val path = parsed.encodedPath.ifBlank { "/" }
    val query = parsed.encodedQuery?.let { "?$it" }.orEmpty()
    return "${parsed.host}$path$query"
  }

  fun normalizeIncomingIntent(uriString: String?, sharedText: String?): UrlDecision? {
    val candidate = uriString?.takeIf { it.isNotBlank() } ?: sharedText?.trim() ?: return null
    if (candidate.startsWith("magnet:", ignoreCase = true)) {
      return classify(candidate)
    }
    if (candidate.startsWith("torrentclaw://", ignoreCase = true) ||
      candidate.contains("torrentclaw.com", ignoreCase = true)
    ) {
      return classify(candidate)
    }
    // Shared IMDb/TMDB URL or plain title text -> open website search URL directly
    val encoded = URLEncoder.encode(candidate, StandardCharsets.UTF_8.name())
    return UrlDecision.InternalPage("$BASE_ORIGIN/search?q=$encoded")
  }

  fun extractMagnetDisplayName(magnetUri: String): String? {
    val queryPart = magnetUri.substringAfter('?', "")
    if (queryPart.isBlank()) return null
    for (pair in queryPart.split('&')) {
      val idx = pair.indexOf('=')
      if (idx > 0 && pair.substring(0, idx).equals("dn", ignoreCase = true)) {
        val raw = pair.substring(idx + 1)
        return runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
          }
          .getOrNull()
          ?.takeIf { it.isNotBlank() }
      }
    }
    return null
  }

  private fun resolveUrl(baseUrl: String, target: String): String? {
    return runCatching {
        val base = URI(baseUrl)
        val resolved = base.resolve(target)
        resolved.toString()
      }
      .getOrNull()
  }
}
