package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.KeystoreCredentialVault
import com.example.core.security.PersistentCookieJar
import com.example.data.web.HtmlPageParser
import com.example.data.web.NavigationGuard
import com.example.data.web.UrlDecision
import com.example.data.web.WebPageKind
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches TorrentClaw`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TorrentClaw", appName)
  }

  @Test
  fun `incoming intent normalizes imdb links, deep links, and magnets`() {
    val deepLink = NavigationGuard.normalizeIncomingIntent("torrentclaw://search?q=inception", null)
    assertTrue(deepLink is UrlDecision.InternalPage)
    assertEquals("https://torrentclaw.com/search?q=inception", (deepLink as UrlDecision.InternalPage).normalizedUrl)

    val sharedImdb = NavigationGuard.normalizeIncomingIntent(null, "https://www.imdb.com/title/tt1375666/")
    assertTrue(sharedImdb is UrlDecision.InternalPage)
    assertTrue((sharedImdb as UrlDecision.InternalPage).normalizedUrl.startsWith("https://torrentclaw.com/search?q="))

    val magnet = NavigationGuard.normalizeIncomingIntent("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", null)
    assertTrue(magnet is UrlDecision.MagnetAction)
  }

  @Test
  fun `html parser parses listing page and pagination`() {
    val html =
      """
      <html><body><main>
        <h1>Search Results</h1>
        <div class="pagination">
          <a href="/search?q=dune&page=2">Next</a>
        </div>
        <article class="card">
          <a href="/movie/693134">
            <img src="https://image.tmdb.org/t/p/w500/dune.jpg" alt="Dune: Part Two" />
            <h3>Dune: Part Two</h3>
          </a>
        </article>
      </main></body></html>
      """
        .trimIndent()
    val parsed = HtmlPageParser.parse("https://torrentclaw.com/search?q=dune", html)
    val listing = parsed.kind as? WebPageKind.Listing
    assertNotNull(listing)
    assertEquals(1, listing?.cards?.size)
    assertEquals("https://torrentclaw.com/search?q=dune&page=2", listing?.pagination?.nextUrl)
  }

  @Test
  fun `credential vault and cookie jar persist torrentclaw session cookies`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vault = KeystoreCredentialVault(context)
    val jar = PersistentCookieJar(vault)
    val cookie =
      Cookie.Builder()
        .name("tc_auth")
        .value("session_123")
        .domain("torrentclaw.com")
        .path("/")
        .secure()
        .build()
    jar.saveFromResponse("https://torrentclaw.com/login".toHttpUrl(), listOf(cookie))
    assertTrue(jar.hasSessionCookies())
    jar.clearSessionCookies()
    vault.clearAll()
  }
}
