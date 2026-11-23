package com.example

import com.example.core.preferences.UserPreferencesState
import com.example.data.model.MediaType
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecStatus
import com.example.data.model.buildMagnetUri
import com.example.data.model.computeSmartRecommendation
import com.example.data.model.deduplicateTorrents
import com.example.data.model.formatFileSize
import com.example.data.model.parseImdbOrTmdbUrl
import com.example.data.remote.TorrentClawJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun `imdb and tmdb url parser handles standard urls`() {
    val imdb = parseImdbOrTmdbUrl("https://www.imdb.com/title/tt1375666/?ref_=nv_sr_srsg_0")
    assertNotNull(imdb)
    assertEquals("tt1375666", imdb?.imdbId)

    val tmdbMovie = parseImdbOrTmdbUrl("https://www.themoviedb.org/movie/27205-inception")
    assertNotNull(tmdbMovie)
    assertEquals(27205, tmdbMovie?.tmdbId)
    assertEquals(MediaType.MOVIE, tmdbMovie?.mediaType)

    val tmdbShow = parseImdbOrTmdbUrl("https://www.themoviedb.org/tv/1396-breaking-bad")
    assertNotNull(tmdbShow)
    assertEquals(1396, tmdbShow?.tmdbId)
    assertEquals(MediaType.SHOW, tmdbShow?.mediaType)
  }

  @Test
  fun `json parser extracts media, torrents, and truespec flags accurately`() {
    val samplePayload =
      """
      {
        "total": 1,
        "results": [
          {
            "id": "27205",
            "tmdb_id": 27205,
            "imdb_id": "tt1375666",
            "title": "Inception",
            "year": 2010,
            "type": "movie",
            "rating": 8.4,
            "genres": ["Action", "Sci-Fi"],
            "poster_path": "/9gk7adHYeDvHkCSEqAvQNLV5Uge.jpg",
            "torrents": [
              {
                "info_hash": "0123456789abcdef0123456789abcdef01234567",
                "release_name": "Inception.2010.2160p.UHD.BluRay.REMUX.DV.HEVC.Atmos-TEST",
                "resolution": "2160p",
                "quality_score": 95,
                "seeders": 120,
                "leechers": 12,
                "size_bytes": 54000000000,
                "truespec": {
                  "status": "verified",
                  "video_codec": "HEVC",
                  "resolution": "2160p"
                }
              }
            ]
          }
        ]
      }
      """
        .trimIndent()

    val parsed = TorrentClawJsonParser.parseSearchResponse(samplePayload)
    assertEquals(1, parsed.mediaItems.size)
    val media = parsed.mediaItems.first()
    assertEquals("Inception", media.title)
    assertEquals(1, media.torrents.size)
    val rel = media.torrents.first()
    assertEquals(TrueSpecStatus.VERIFIED, rel.trueSpecStatus)
    assertEquals("HEVC", rel.videoCodec)
    assertEquals(95, rel.qualityScore)
  }

  @Test
  fun `deduplicateTorrents keeps highest quality release per infoHash`() {
    val hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    val low = TorrentRelease(infoHash = hash, releaseName = "Low", qualityScore = 40)
    val high = TorrentRelease(infoHash = hash, releaseName = "High", qualityScore = 88)
    val deduped = deduplicateTorrents(listOf(low, high))
    assertEquals(1, deduped.size)
    assertEquals(88, deduped.first().qualityScore)
  }

  @Test
  fun `smart recommendation honors user resolution and TrueSpec preferences`() {
    val prefs =
      UserPreferencesState(
        preferredResolutions = setOf("1080p"),
        preferredCodecs = setOf("HEVC"),
        preferTrueSpecVerified = true,
      )
    val unverified4k =
      TorrentRelease(
        infoHash = "1111111111111111111111111111111111111111",
        releaseName = "Title.2160p.H264",
        resolution = "2160p",
        videoCodec = "H.264",
        qualityScore = 76,
        seeders = 200,
        trueSpecStatus = TrueSpecStatus.UNVERIFIED,
      )
    val verified1080 =
      TorrentRelease(
        infoHash = "2222222222222222222222222222222222222222",
        releaseName = "Title.1080p.HEVC",
        resolution = "1080p",
        videoCodec = "HEVC",
        qualityScore = 74,
        seeders = 45,
        trueSpecStatus = TrueSpecStatus.VERIFIED,
      )
    val rec = computeSmartRecommendation(listOf(unverified4k, verified1080), prefs)
    assertNotNull(rec)
    assertEquals(verified1080.infoHash, rec?.release?.infoHash)
    assertTrue(rec?.reasons?.contains("TrueSpec Verified") == true)
  }

  @Test
  fun `magnet builder and size formatter produce valid outputs`() {
    val magnet = buildMagnetUri("0123456789abcdef0123456789abcdef01234567", "Inception 2010")
    assertTrue(magnet.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
    assertTrue(magnet.contains("dn=Inception+2010"))
    assertEquals("1.50 GB", formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
  }
}
