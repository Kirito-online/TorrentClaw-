package com.example

import com.example.core.security.PersistentCookieJar
import com.example.data.web.HtmlPageParser
import com.example.data.web.NavigationGuard
import com.example.data.web.UrlDecision
import com.example.data.web.WebPageKind
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun `navigation guard allows torrentclaw urls, intercepts magnet and torrent links, and blocks external popups`() {
    val home = NavigationGuard.classify("http://torrentclaw.com/search?q=dune")
    assertTrue(home is UrlDecision.InternalPage)
    assertEquals("https://torrentclaw.com/search?q=dune", (home as UrlDecision.InternalPage).normalizedUrl)

    val relative = NavigationGuard.classify("/movie/12345", "https://torrentclaw.com/search?q=dune")
    assertTrue(relative is UrlDecision.InternalPage)
    assertEquals("https://torrentclaw.com/movie/12345", (relative as UrlDecision.InternalPage).normalizedUrl)

    val magnet = NavigationGuard.classify("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Dune.Part.Two.2024")
    assertTrue(magnet is UrlDecision.MagnetAction)
    assertEquals("Dune.Part.Two.2024", (magnet as UrlDecision.MagnetAction).displayName)

    val torrentFile = NavigationGuard.classify("/torrent/0123456789abcdef0123456789abcdef01234567", "https://torrentclaw.com/")
    assertTrue(torrentFile is UrlDecision.TorrentFileAction)

    val popupAd = NavigationGuard.classify("https://evil-popup.example.com/offer")
    assertTrue(popupAd is UrlDecision.BlockedExternal)

    val xssScheme = NavigationGuard.classify("javascript:alert(1)")
    assertTrue(xssScheme is UrlDecision.BlockedExternal)

    val dataScheme = NavigationGuard.classify("data:text/html,<script>alert(1)</script>")
    assertTrue(dataScheme is UrlDecision.BlockedExternal)
  }

  @Test
  fun `html parser parses home sections, search forms, and media cards cleanly`() {
    val html =
      """
      <!doctype html>
      <html>
        <head><title>TorrentClaw - Media Index</title></head>
        <body>
          <header>
            <nav>
              <a href="/">Home</a>
              <a href="/search?type=movie">Movies</a>
              <a href="/search?type=tv">TV Shows</a>
              <a href="/login">Sign In</a>
            </nav>
            <form action="/search" method="GET">
              <input type="search" name="q" placeholder="Search titles..." />
              <button type="submit">Search</button>
            </form>
          </header>
          <main>
            <section class="hero">
              <h1>Discover Verified Torrents</h1>
              <p>Fast multi-source index with TrueSpec quality metrics.</p>
            </section>
            <section>
              <h2>Trending Movies</h2>
              <article class="card">
                <a href="/movie/693134">
                  <img src="https://image.tmdb.org/t/p/w500/dune.jpg" alt="Dune: Part Two" />
                  <h3>Dune: Part Two</h3>
                  <span>2024 • ★ 8.3</span>
                </a>
              </article>
            </section>
          </main>
        </body>
      </html>
      """
        .trimIndent()

    val page = HtmlPageParser.parse("https://torrentclaw.com/", html)
    assertEquals("TorrentClaw - Media Index", page.pageTitle)
    assertFalse(page.isSignedIn)
    assertEquals(4, page.headerLinks.size)
    assertNotNull(page.searchForm)
    assertEquals("https://torrentclaw.com/search", page.searchForm?.actionUrl)

    val home = page.kind as? WebPageKind.Home
    assertNotNull(home)
    assertEquals("Discover Verified Torrents", home?.heroTitle)
    assertEquals(1, home?.sections?.size)
    val card = home?.sections?.first()?.cards?.first()
    assertEquals("Dune: Part Two", card?.title)
    assertEquals("https://torrentclaw.com/movie/693134", card?.url)
    assertEquals("2024", card?.year)
    assertEquals("8.3", card?.rating)
  }

  @Test
  fun `html parser parses detail page torrents, magnet dn names, and season filters`() {
    val html =
      """
      <!doctype html>
      <html>
        <head><title>Severance (2022) - TorrentClaw</title></head>
        <body>
          <main>
            <div class="hero">
              <div class="poster"><img src="https://image.tmdb.org/t/p/w500/sev.jpg" /></div>
              <h1>Severance (2022)</h1>
              <p class="overview">Mark leads a team of office workers whose memories have been surgically divided.</p>
              <span class="genre"><a href="/search?genre=Sci-Fi">Sci-Fi</a></span>
            </div>
            <div class="season-tabs">
              <a href="/tv/95396?season=1" class="active">Season 1</a>
              <a href="/tv/95396?season=2">Season 2</a>
            </div>
            <table>
              <tr>
                <td class="title">5f3b50774263a22272ba670943a07d132f91a06e</td>
                <td>2160p • HEVC • Dolby Vision • TrueSpec Verified • 14.2 GB • ▲ 184 ▼ 12 • Score 94</td>
                <td>
                  <a href="magnet:?xt=urn:btih:5f3b50774263a22272ba670943a07d132f91a06e&dn=Severance.S01.2160p.ATVP.WEB-DL.DDP5.1.DV.HEVC">Magnet</a>
                  <a href="/torrent/5f3b50774263a22272ba670943a07d132f91a06e">Download .torrent</a>
                </td>
              </tr>
            </table>
          </main>
        </body>
      </html>
      """
        .trimIndent()

    val page = HtmlPageParser.parse("https://torrentclaw.com/tv/95396", html)
    val detail = page.kind as? WebPageKind.Detail
    assertNotNull(detail)
    assertEquals("Severance", detail?.title)
    assertEquals("2022", detail?.year)
    assertEquals(2, detail?.seasonLinks?.size)
    assertEquals(1, detail?.torrents?.size)

    val torrent = detail?.torrents?.first()
    assertEquals("Severance.S01.2160p.ATVP.WEB-DL.DDP5.1.DV.HEVC", torrent?.title)
    assertEquals("2160p", torrent?.resolution)
    assertEquals("14.2 GB", torrent?.size)
    assertEquals("184", torrent?.seeders)
    assertEquals("12", torrent?.leechers)
    assertEquals("94", torrent?.qualityScore)
    assertEquals("TrueSpec Verified", torrent?.trueSpecBadge)
    assertNotNull(torrent?.magnetUrl)
    assertEquals(
      "https://torrentclaw.com/torrent/5f3b50774263a22272ba670943a07d132f91a06e",
      torrent?.torrentDownloadUrl,
    )
  }

  @Test
  fun `html parser extracts auth form with hidden csrf token and detects spa shells`() {
    val loginHtml =
      """
      <html><body><main>
        <h1>Sign in to TorrentClaw</h1>
        <form action="/login" method="POST">
          <input type="hidden" name="_csrf" value="abc123token" />
          <label for="email">Email</label>
          <input id="email" type="email" name="email" required />
          <label for="pwd">Password</label>
          <input id="pwd" type="password" name="password" required />
          <button type="submit">Sign In</button>
        </form>
      </main></body></html>
      """
        .trimIndent()

    val authPage = HtmlPageParser.parse("https://torrentclaw.com/login", loginHtml)
    val auth = authPage.kind as? WebPageKind.Auth
    assertNotNull(auth)
    assertEquals("POST", auth?.form?.method)
    assertEquals(3, auth?.form?.fields?.size)
    assertTrue(auth?.form?.fields?.any { it.isHidden && it.name == "_csrf" && it.value == "abc123token" } == true)

    val spaShell = """<html><body><div id="__next"></div><script src="/app.js"></script></body></html>"""
    assertTrue(HtmlPageParser.needsClientDomSnapshot(spaShell))
    assertFalse(HtmlPageParser.needsClientDomSnapshot(loginHtml))
  }

  @Test
  fun `persistent cookie jar stores torrentclaw cookies and isolates external domains`() {
    var now = 1_000_000L
    val jar = PersistentCookieJar(vault = null, clockMs = { now })

    val tcUrl = "https://torrentclaw.com/login".toHttpUrl()
    val externalUrl = "https://evil-tracker.example.com/".toHttpUrl()

    val sessionCookie =
      Cookie.Builder()
        .name("tc_session")
        .value("secret_session_value")
        .domain("torrentclaw.com")
        .path("/")
        .secure()
        .httpOnly()
        .expiresAt(now + 60_000L)
        .build()

    val foreignCookie =
      Cookie.Builder()
        .name("ad_id")
        .value("xyz")
        .domain("evil-tracker.example.com")
        .path("/")
        .expiresAt(now + 60_000L)
        .build()

    jar.saveFromResponse(tcUrl, listOf(sessionCookie, foreignCookie))
    assertTrue(jar.hasSessionCookies())
    assertEquals(1, jar.loadForRequest("https://torrentclaw.com/account".toHttpUrl()).size)
    assertEquals(0, jar.loadForRequest(externalUrl).size)

    // Advance clock past expiry
    now += 120_000L
    assertFalse(jar.hasSessionCookies())
  }
}
