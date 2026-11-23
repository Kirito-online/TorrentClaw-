package com.example

import com.example.core.preferences.UserPreferencesState
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.TorrentListFilterState
import com.example.data.model.TorrentListSort
import com.example.data.model.TorrentRelease
import com.example.data.model.TorrentSizeFilter
import com.example.data.model.TrueSpecStatus
import com.example.data.model.buildMagnetUri
import com.example.data.model.computeSmartRecommendation
import com.example.data.model.deduplicateTorrents
import com.example.data.model.extractAvailableResolutions
import com.example.data.model.extractTorrentFilterFacets
import com.example.data.model.filterAndSortTorrents
import com.example.data.model.formatFileSize
import com.example.data.model.matchesSeasonAndEpisode
import com.example.data.model.matchesTargetMediaItem
import com.example.data.model.matchesTargetMediaRelease
import com.example.data.model.parseImdbOrTmdbUrl
import com.example.data.remote.TorrentClawJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `json parser avoids raw info hashes as torrent titles and resolves alternative fields`() {
    val hash1 = "5f3b50774263a22272ba670943a07d132f91a06e"
    val hash2 = "ac643ba9c4318b5a2bf107cd79df358e7bea2da1"
    val hash3 = "baf93326d33672646e9b79afe877652b85d69b9f"
    val payload =
      """
      {
        "results": [
          {
            "info_hash": "$hash1",
            "name": "$hash1",
            "resolution": "2160p",
            "source_type": "bluray",
            "video_codec": "x265",
            "audio_codec": "truehd",
            "audio_channels": 8,
            "size_bytes": 14506250000,
            "seeders": 127,
            "tracker": "torrents-csv"
          },
          {
            "info_hash": "$hash2",
            "raw_name": "The.Odyssey.2026.2160p.BluRay.HEVC.TrueHD.7.1-GRP",
            "resolution": "2160p",
            "quality_score": 89
          },
          {
            "info_hash": "$hash3",
            "magnet": "magnet:?xt=urn:btih:$hash3&dn=The.Odyssey.2026.1080p.BluRay.x264.DTS-SCENE",
            "resolution": "1080p",
            "quality_score": 87
          }
        ]
      }
      """
        .trimIndent()

    val parsed = TorrentClawJsonParser.parseSearchResponse(payload, fallbackMediaTitle = "The Odyssey")
    assertEquals(3, parsed.torrents.size)

    val t1 = parsed.torrents[0]
    assertFalse("Release name must not show raw hash", t1.releaseName.equals(hash1, ignoreCase = true))
    assertTrue(t1.releaseName.contains("The Odyssey"))
    assertTrue(t1.releaseName.contains("2160p"))
    assertTrue(t1.releaseName.contains("BluRay"))
    assertEquals("7.1", t1.audioChannels)
    assertFalse(t1.hasExplicitReleaseName)

    val t2 = parsed.torrents[1]
    assertEquals("The.Odyssey.2026.2160p.BluRay.HEVC.TrueHD.7.1-GRP", t2.releaseName)
    assertTrue(t2.hasExplicitReleaseName)

    val t3 = parsed.torrents[2]
    assertEquals("The.Odyssey.2026.1080p.BluRay.x264.DTS-SCENE", t3.releaseName)
  }

  @Test
  fun `filterAndSortTorrents filters by quality and size bucket and sorts accurately`() {
    val gb = 1024L * 1024L * 1024L
    val list =
      listOf(
        TorrentRelease(
          infoHash = "1111111111111111111111111111111111111111",
          releaseName = "Small 720p",
          resolution = "720p",
          sizeBytes = (1.4 * gb).toLong(),
          qualityScore = 60,
          seeders = 40,
        ),
        TorrentRelease(
          infoHash = "2222222222222222222222222222222222222222",
          releaseName = "Mid 1080p",
          resolution = "1080p",
          sizeBytes = 6L * gb,
          qualityScore = 82,
          seeders = 90,
        ),
        TorrentRelease(
          infoHash = "3333333333333333333333333333333333333333",
          releaseName = "Big 4K",
          resolution = "2160p",
          sizeBytes = 25L * gb,
          qualityScore = 95,
          seeders = 25,
        ),
        TorrentRelease(
          infoHash = "4444444444444444444444444444444444444444",
          releaseName = "Compact 4K",
          resolution = "2160p",
          sizeBytes = 12L * gb,
          qualityScore = 91,
          seeders = 150,
        ),
      )

    val resCounts = extractAvailableResolutions(list)
    assertEquals(listOf("2160p" to 2, "1080p" to 1, "720p" to 1), resCounts)

    val only4kSmallestFirst =
      filterAndSortTorrents(list, selectedResolution = "2160p", sizeFilter = TorrentSizeFilter.ALL, sortBy = TorrentListSort.SMALLEST_SIZE)
    assertEquals(listOf("Compact 4K", "Big 4K"), only4kSmallestFirst.map { it.releaseName })

    val over20Gb =
      filterAndSortTorrents(list, selectedResolution = null, sizeFilter = TorrentSizeFilter.OVER_20_GB, sortBy = TorrentListSort.BEST_QUALITY)
    assertEquals(listOf("Big 4K"), over20Gb.map { it.releaseName })

    val under2Gb =
      filterAndSortTorrents(list, selectedResolution = null, sizeFilter = TorrentSizeFilter.UNDER_2_GB, sortBy = TorrentListSort.BEST_QUALITY)
    assertEquals(listOf("Small 720p"), under2Gb.map { it.releaseName })

    val mostSeeded =
      filterAndSortTorrents(list, selectedResolution = null, sizeFilter = TorrentSizeFilter.ALL, sortBy = TorrentListSort.MOST_SEEDED)
    assertEquals("Compact 4K", mostSeeded.first().releaseName)
  }

  @Test
  fun `unified torrent filter facets and advanced filters work cleanly`() {
    val gb = 1024L * 1024L * 1024L
    val releases =
      listOf(
        TorrentRelease(
          infoHash = "aaaa",
          releaseName = "Movie.2026.2160p.BluRay.x265.TrueHD",
          resolution = "2160p",
          sourceType = "bluray",
          videoCodec = "x265",
          audioCodec = "truehd",
          hdrFormat = "Dolby Vision",
          sizeBytes = 14L * gb,
          seeders = 120,
          qualityScore = 96,
          trueSpecStatus = TrueSpecStatus.VERIFIED,
        ),
        TorrentRelease(
          infoHash = "bbbb",
          releaseName = "Movie.2026.2160p.WEB-DL.HEVC.Atmos",
          resolution = "2160p",
          sourceType = "web-dl",
          videoCodec = "hevc",
          audioCodec = "atmos",
          hdrFormat = "HDR10",
          sizeBytes = 9L * gb,
          seeders = 45,
          qualityScore = 88,
          trueSpecStatus = TrueSpecStatus.UNVERIFIED,
        ),
        TorrentRelease(
          infoHash = "cccc",
          releaseName = "Movie.2026.1080p.BluRay.x264.DTS",
          resolution = "1080p",
          sourceType = "bluray",
          videoCodec = "x264",
          audioCodec = "dts",
          sizeBytes = 6L * gb,
          seeders = 0,
          qualityScore = 80,
          trueSpecStatus = TrueSpecStatus.VERIFIED,
        ),
        TorrentRelease(
          infoHash = "dddd",
          releaseName = "Movie.2026.720p.WEBRip.x264.AAC",
          resolution = "720p",
          sourceType = "webrip",
          videoCodec = "x264",
          audioCodec = "aac",
          sizeBytes = 2L * gb,
          seeders = 18,
          qualityScore = 65,
        ),
      )

    val facets = extractTorrentFilterFacets(releases)
    assertEquals(listOf("HEVC" to 2, "H.264" to 2), facets.codecs)
    assertEquals(2, facets.verifiedCount)

    // Multi-resolution + unified HEVC codec + seeded > 0
    val multiResHevc =
      filterAndSortTorrents(
        releases,
        TorrentListFilterState(
          resolutions = setOf("2160p", "1080p"),
          codecs = setOf("HEVC"),
          minSeeders = 1,
          sortBy = TorrentListSort.LARGEST_SIZE,
        ),
      )
    assertEquals(listOf("aaaa", "bbbb"), multiResHevc.map { it.infoHash })

    // Custom GB range + keyword + TrueSpec verified
    val customRange =
      filterAndSortTorrents(
        releases,
        TorrentListFilterState(
          customMinSizeGb = 5f,
          customMaxSizeGb = 12f,
          verifiedOnly = true,
          keyword = "dts",
        ),
      )
    assertEquals(listOf("cccc"), customRange.map { it.infoHash })
  }

  @Test
  fun `media and torrent matching excludes unrelated titles, wrong years, and mismatched IDs`() {
    val targetMovie =
      MediaItem(
        id = "1368337",
        tmdbId = 1368337,
        imdbId = "tt33764258",
        title = "The Odyssey",
        year = 2026,
        mediaType = MediaType.MOVIE,
      )

    val exactCandidate =
      MediaItem(
        id = "1368337",
        tmdbId = 1368337,
        imdbId = "tt33764258",
        title = "The Odyssey",
        year = 2026,
        mediaType = MediaType.MOVIE,
      )
    val oldOdyssey =
      MediaItem(
        id = "8888",
        tmdbId = 8888,
        imdbId = "tt0118414",
        title = "The Odyssey",
        year = 1997,
        mediaType = MediaType.SHOW,
      )
    val unrelatedMovie =
      MediaItem(
        id = "99999",
        tmdbId = 99999,
        title = "2001: A Space Odyssey",
        year = 1968,
        mediaType = MediaType.MOVIE,
      )

    assertTrue(matchesTargetMediaItem(exactCandidate, targetMovie))
    assertFalse(matchesTargetMediaItem(oldOdyssey, targetMovie))
    assertFalse(matchesTargetMediaItem(unrelatedMovie, targetMovie))

    // Matching by TMDB ID
    val idMatchRelease =
      TorrentRelease(
        infoHash = "1111",
        releaseName = "2160p BluRay",
        tmdbId = 1368337,
        hasExplicitReleaseName = false,
      )
    assertTrue(matchesTargetMediaRelease(idMatchRelease, targetMovie))

    // Reject different TMDB ID even if title matches
    val wrongIdRelease =
      TorrentRelease(
        infoHash = "2222",
        releaseName = "The.Odyssey.2026.1080p.BluRay",
        tmdbId = 999999,
        hasExplicitReleaseName = true,
      )
    assertFalse(matchesTargetMediaRelease(wrongIdRelease, targetMovie))

    // Reject synthesized non-explicit name without IDs
    val synthesizedNoId =
      TorrentRelease(
        infoHash = "3333",
        releaseName = "The Odyssey 2160p BluRay",
        hasExplicitReleaseName = false,
      )
    assertFalse(matchesTargetMediaRelease(synthesizedNoId, targetMovie))

    // Reject different movie sharing partial words or wrong year
    val spaceOdyssey =
      TorrentRelease(
        infoHash = "4444",
        releaseName = "2001.A.Space.Odyssey.1968.2160p.BluRay",
        hasExplicitReleaseName = true,
      )
    assertFalse(matchesTargetMediaRelease(spaceOdyssey, targetMovie))

    // Accept explicit matching title + year without IDs
    val validTitleRelease =
      TorrentRelease(
        infoHash = "5555",
        releaseName = "The.Odyssey.2026.2160p.WEB-DL.HEVC",
        hasExplicitReleaseName = true,
      )
    assertTrue(matchesTargetMediaRelease(validTitleRelease, targetMovie))
  }

  @Test
  fun `season and episode matching scopes show torrents cleanly`() {
    val s1e5 = TorrentRelease(infoHash = "a", releaseName = "Severance.S01E05.1080p.WEB-DL")
    val s1e2 = TorrentRelease(infoHash = "b", releaseName = "Severance.S01E02.1080p.WEB-DL")
    val s2e5 = TorrentRelease(infoHash = "c", releaseName = "Severance.S02E05.1080p.WEB-DL")
    val s1Pack = TorrentRelease(infoHash = "d", releaseName = "Severance.S01.COMPLETE.1080p.BluRay")

    assertTrue(matchesSeasonAndEpisode(s1e5, seasonFilter = 1, episodeFilter = 5))
    assertFalse(matchesSeasonAndEpisode(s1e2, seasonFilter = 1, episodeFilter = 5))
    assertFalse(matchesSeasonAndEpisode(s2e5, seasonFilter = 1, episodeFilter = 5))
    assertTrue(matchesSeasonAndEpisode(s1Pack, seasonFilter = 1, episodeFilter = 5))
    assertFalse(matchesSeasonAndEpisode(s1Pack, seasonFilter = 2, episodeFilter = null))
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
