package com.example.core.security

import android.webkit.CookieManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class PersistedCookie(
  val name: String,
  val value: String,
  val expiresAt: Long,
  val domain: String,
  val path: String,
  val secure: Boolean,
  val httpOnly: Boolean,
  val hostOnly: Boolean,
) {
  fun toOkHttpCookie(): Cookie {
    val builder =
      Cookie.Builder()
        .name(name)
        .value(value)
        .expiresAt(expiresAt)
        .path(path)
    if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
    if (secure) builder.secure()
    if (httpOnly) builder.httpOnly()
    return builder.build()
  }

  companion object {
    fun fromOkHttpCookie(cookie: Cookie): PersistedCookie {
      // Persist session cookies (Long.MAX_VALUE / unspecified max-age) across app restarts
      // with a bounded 30-day rolling TTL unless server explicitly sets expiresAt.
      val effectiveExpiry =
        if (cookie.persistent) {
          cookie.expiresAt
        } else {
          System.currentTimeMillis() + THIRTY_DAYS_MS
        }
      return PersistedCookie(
        name = cookie.name,
        value = cookie.value,
        expiresAt = effectiveExpiry,
        domain = cookie.domain,
        path = cookie.path,
        secure = cookie.secure,
        httpOnly = cookie.httpOnly,
        hostOnly = cookie.hostOnly,
      )
    }

    private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
  }
}

/**
 * Keystore-backed persistent OkHttp CookieJar with safe WebKit CookieManager sync
 * so torrentclaw.com login sessions persist reliably across app restarts.
 */
class PersistentCookieJar(
  private val vault: KeystoreCredentialVault?,
  private val clockMs: () -> Long = { System.currentTimeMillis() },
) : CookieJar {

  private val json = Json { ignoreUnknownKeys = true }
  private val lock = Any()
  private var cookies: MutableList<PersistedCookie> = loadPersistedCookies().toMutableList()

  override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
    if (!isAllowedDomain(url.host)) return
    synchronized(lock) {
      val now = clockMs()
      val updated = this.cookies.filter { it.expiresAt > now }.toMutableList()
      for (incoming in cookies) {
        if (!isAllowedDomain(incoming.domain)) continue
        updated.removeAll { existing ->
          existing.name == incoming.name &&
            existing.domain.equals(incoming.domain, ignoreCase = true) &&
            existing.path == incoming.path
        }
        if (incoming.value.isNotEmpty() && incoming.expiresAt > now) {
          updated.add(PersistedCookie.fromOkHttpCookie(incoming))
        }
      }
      this.cookies = updated
      persistCookiesLocked()
    }
    syncToWebKitCookieManager(url.toString())
  }

  override fun loadForRequest(url: HttpUrl): List<Cookie> {
    if (!isAllowedDomain(url.host)) return emptyList()
    synchronized(lock) {
      val now = clockMs()
      val active = cookies.filter { it.expiresAt > now }
      if (active.size != cookies.size) {
        cookies = active.toMutableList()
        persistCookiesLocked()
      }
      return active
        .map { it.toOkHttpCookie() }
        .filter { it.matches(url) }
    }
  }

  fun hasSessionCookies(host: String = "torrentclaw.com"): Boolean {
    val httpUrl = "https://$host/".toHttpUrlOrNull() ?: return false
    return loadForRequest(httpUrl).isNotEmpty()
  }

  fun clearSessionCookies() {
    synchronized(lock) {
      cookies.clear()
      persistCookiesLocked()
    }
    runCatching {
      CookieManager.getInstance().removeAllCookies(null)
      CookieManager.getInstance().flush()
    }
  }

  fun syncToWebKitCookieManager(targetUrl: String = "https://torrentclaw.com/") {
    val httpUrl = targetUrl.toHttpUrlOrNull() ?: return
    if (!isAllowedDomain(httpUrl.host)) return
    val matching = loadForRequest(httpUrl)
    runCatching {
      val cm = CookieManager.getInstance()
      cm.setAcceptCookie(true)
      for (cookie in matching) {
        val header = buildString {
          append("${cookie.name}=${cookie.value}; Path=${cookie.path}")
          if (!cookie.hostOnly) append("; Domain=${cookie.domain}")
          if (cookie.secure) append("; Secure")
          if (cookie.httpOnly) append("; HttpOnly")
        }
        cm.setCookie(targetUrl, header)
      }
      cm.flush()
    }
  }

  fun importFromWebKitCookieManager(targetUrl: String = "https://torrentclaw.com/") {
    val httpUrl = targetUrl.toHttpUrlOrNull() ?: return
    if (!isAllowedDomain(httpUrl.host)) return
    val rawHeader = runCatching { CookieManager.getInstance().getCookie(targetUrl) }.getOrNull()
    if (rawHeader.isNullOrBlank()) return
    val pairs =
      rawHeader.split(";").mapNotNull { part ->
        val idx = part.indexOf('=')
        if (idx <= 0) null
        else {
          val k = part.substring(0, idx).trim()
          val v = part.substring(idx + 1).trim()
          if (k.isNotEmpty() && v.isNotEmpty()) k to v else null
        }
      }
    if (pairs.isEmpty()) return
    synchronized(lock) {
      val now = clockMs()
      val updated = cookies.filter { it.expiresAt > now }.toMutableList()
      for ((name, value) in pairs) {
        updated.removeAll {
          it.name == name && it.domain.equals(httpUrl.host, ignoreCase = true)
        }
        updated.add(
          PersistedCookie(
            name = name,
            value = value,
            expiresAt = now + 30L * 24L * 60L * 60L * 1000L,
            domain = httpUrl.host,
            path = "/",
            secure = true,
            httpOnly = false,
            hostOnly = true,
          )
        )
      }
      cookies = updated
      persistCookiesLocked()
    }
  }

  private fun loadPersistedCookies(): List<PersistedCookie> {
    val raw = vault?.getSecret(KEY_COOKIE_JAR) ?: return emptyList()
    return runCatching {
        val now = clockMs()
        json.decodeFromString<List<PersistedCookie>>(raw).filter { it.expiresAt > now }
      }
      .getOrDefault(emptyList())
  }

  private fun persistCookiesLocked() {
    val encoded = json.encodeToString(cookies)
    vault?.putSecret(KEY_COOKIE_JAR, encoded)
  }

  companion object {
    private const val KEY_COOKIE_JAR = "tc_web_cookie_jar_v1"

    private fun isAllowedDomain(host: String): Boolean {
      val lower = host.lowercase()
      return lower == "torrentclaw.com" || lower.endsWith(".torrentclaw.com")
    }
  }
}
