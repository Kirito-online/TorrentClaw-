package com.example

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.KeystoreCredentialVault
import com.example.data.model.MediaType
import com.example.data.model.parseImdbOrTmdbUrl
import com.example.data.remote.TorrentClawJsonParser
import com.example.util.TorrentIntentHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
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
  fun `shared imdb and tmdb urls parse cleanly for search routing`() {
    val sharedImdb = parseImdbOrTmdbUrl("https://www.imdb.com/title/tt1375666/")
    assertNotNull(sharedImdb)
    assertEquals("tt1375666", sharedImdb?.imdbId)

    val sharedTmdb = parseImdbOrTmdbUrl("https://www.themoviedb.org/tv/95396")
    assertNotNull(sharedTmdb)
    assertEquals(95396, sharedTmdb?.tmdbId)
    assertEquals(MediaType.SHOW, sharedTmdb?.mediaType)

    val magnetUri = Uri.parse("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567")
    assertEquals("magnet", magnetUri.scheme)
  }

  @Test
  fun `json parser handles popular catalog and stats payloads`() {
    val catalog =
      TorrentClawJsonParser.parseSearchResponse(
        """
        {
          "items": [
            {
              "id": "the-odyssey",
              "tmdb_id": 1368337,
              "title": "The Odyssey",
              "media_type": "movie",
              "year": 2026,
              "torrent_count": 12
            }
          ]
        }
        """
          .trimIndent()
      )
    assertEquals(1, catalog.mediaItems.size)
    assertEquals("The Odyssey", catalog.mediaItems.first().title)

    val stats =
      TorrentClawJsonParser.parseStatsResponse(
        """{"total_movies": 42000, "total_shows": 12000, "total_torrents": 950000, "active_sources": 38}"""
      )
    assertEquals(42000, stats.totalMovies)
    assertEquals(38, stats.activeSources)
  }

  @Test
  fun `credential vault stores and clears entries cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vault = KeystoreCredentialVault(context)
    vault.putSecret("test_secret", "token_abc")
    assertEquals("token_abc", vault.getSecret("test_secret"))
    vault.clearAll()
    assertNull(vault.getSecret("test_secret"))
  }

  private fun registerMagnetHandler(packageName: String, activityClass: String) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val shadowPm = Shadows.shadowOf(context.packageManager)
    val component = ComponentName(packageName, activityClass)
    shadowPm.addActivityIfNotPresent(component)
    val filter =
      IntentFilter(Intent.ACTION_VIEW).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        addCategory(Intent.CATEGORY_BROWSABLE)
        addDataScheme("magnet")
      }
    shadowPm.addIntentFilterForActivity(component, filter)
  }

  @Test
  fun `openMagnetLink returns false when only self handles magnet or no external client exists`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Test"

    val launched = TorrentIntentHelper.openMagnetLink(app, magnet)
    assertFalse(launched)
    assertNull(Shadows.shadowOf(app).nextStartedActivity)
  }

  @Test
  fun `openMagnetLink launches single external torrent client directly while excluding self`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val externalPkg = "org.libre.torrent"
    registerMagnetHandler(externalPkg, "$externalPkg.AddTorrentActivity")

    val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Test"
    val launched = TorrentIntentHelper.openMagnetLink(app, magnet)

    assertTrue(launched)
    val started = Shadows.shadowOf(app).nextStartedActivity
    assertNotNull(started)
    assertEquals(Intent.ACTION_VIEW, started.action)
    assertEquals(externalPkg, started.`package`)
    assertEquals(magnet, started.dataString)
  }

  @Test
  fun `openMagnetLink opens chooser excluding self when multiple external torrent clients exist`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    registerMagnetHandler("org.libre.torrent", "org.libre.torrent.AddTorrentActivity")
    registerMagnetHandler("com.deluge.client", "com.deluge.client.MagnetActivity")

    val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Test"
    val launched = TorrentIntentHelper.openMagnetLink(app, magnet)

    assertTrue(launched)
    val started = Shadows.shadowOf(app).nextStartedActivity
    assertNotNull(started)
    assertEquals(Intent.ACTION_CHOOSER, started.action)
    @Suppress("DEPRECATION")
    val excluded =
      started.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)
        ?.filterIsInstance<ComponentName>()
        .orEmpty()
    assertTrue(excluded.any { it.packageName == app.packageName })
  }
}
