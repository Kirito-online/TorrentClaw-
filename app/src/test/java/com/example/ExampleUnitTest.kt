package com.example

import com.example.core.preferences.UserPreferencesState
import com.example.data.model.FavoriteMediaEntry
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.STANDARD_RESOLUTION_OPTIONS
import com.example.data.model.TorrentListFilterState
import com.example.data.model.TorrentListSort
import com.example.data.model.TorrentRelease
import com.example.data.model.TorrentSizeFilter
import com.example.data.model.TrueSpecStatus
import com.example.data.model.WatchCategory
import com.example.data.model.buildMagnetUri
import com.example.data.model.computeSmartRecommendation
import com.example.data.model.deduplicateTorrents
import com.example.data.model.extractAvailableResolutions
import com.example.data.model.filterAndSortTorrents
import com.example.data.model.formatFileSize
import com.example.data.model.isSameFavoriteMedia
import com.example.data.model.matchesSeasonAndEpisode
import com.example.data.model.matchesTargetMediaItem
import com.example.data.model.matchesTargetMediaRelease
import com.example.data.model.mergeFacetOptionsWithDefaults
import com.example.data.model.parseImdbOrTmdbUrl
import com.example.data.model.synthesizeCleanReleaseName
import com.example.data.model.toFavoriteEntry
import com.example.data.model.toMediaItem
import com.example.data.network.TorrentClawHttpException
import com.example.data.network.TorrentClawNetworkModule
import com.example.data.network.TorrentClawNetworkService
import com.example.data.remote.TorrentClawJsonParser
import com.example.data.repository.TorrentClawRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun `parseImdbOrTmdbUrl extracts imdb ids and tmdb movie or tv urls`() {
    val imdbUrl = parseImdbOrTmdbUrl("https://www.imdb.com/title/tt1375666/?ref_=nv_sr_srsg_0")
    assertNotNull(imdbUrl)
    assertEquals("tt1375666", imdbUrl?.imdbId)
    assertNull(imdbUrl?.tmdbId)

    val rawImdb = parseImdbOrTmdbUrl("  TT0816692 ")
    assertEquals("tt0816692", rawImdb?.imdbId)

    val tmdbMovie = parseImdbOrTmdbUrl("https://www.themoviedb.org/movie/693134-dune-part-two")
    assertEquals(693134, tmdbMovie?.tmdbId)
    assertEquals(MediaType.MOVIE, tmdbMovie?.mediaType)

    val tmdbShow = parseImdbOrTmdbUrl("https://www.themoviedb.org/tv/95396-severance")
    assertEquals(95396, tmdbShow?.tmdbId)
    assertEquals(MediaType.SHOW, tmdbShow?.mediaType)

    assertNull(parseImdbOrTmdbUrl("Dune Part Two"))
  }

  @Test
  fun `json parser parses search, detail, credits, watch providers, collections, and avoids raw hashes`() {
    val searchJson =
      """
      {
        "page": 1,
        "total_pages": 3,
        "total_results": 48,
        "results": [
          {
            "id": "dune-2",
            "tmdb_id": 693134,
            "imdb_id": "tt15239678",
            "title": "Dune: Part Two",
            "media_type": "movie",
            "release_date": "2024-02-27",
            "vote_average": 8.3,
            "poster_path": "/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            "torrent_count": 24
          }
        ]
      }
      """
        .trimIndent()

    val search = TorrentClawJsonParser.parseSearchResponse(searchJson)
    assertEquals(1, search.mediaItems.size)
    assertEquals(48, search.total)
    assertTrue(search.hasMore)
    val dune = search.mediaItems.first()
    assertEquals("Dune: Part Two", dune.title)
    assertEquals(2024, dune.year)
    assertEquals(MediaType.MOVIE, dune.mediaType)
    assertEquals("https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", dune.posterUrl)

    val hash = "5f3b50774263a22272ba670943a07d132f91a06e"
    val detailJson =
      """
      {
        "content": {
          "id": "dune-2",
          "tmdb_id": 693134,
          "title": "Dune: Part Two",
          "media_type": "movie",
          "year": 2024,
          "runtime_minutes": 166,
          "genres": [{"name": "Sci-Fi"}, {"name": "Adventure"}],
          "overview": "Paul Atreides unites with Chani and the Fremen."
        },
        "torrents": [
          {
            "info_hash": "$hash",
            "release_name": "$hash",
            "magnet": "magnet:?xt=urn:btih:$hash&dn=Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.DV.HEVC",
            "resolution": "4k",
            "video_codec": "x265",
            "audio_codec": "DDP",
            "audio_channels": 6,
            "hdr": "Dolby Vision",
            "size_bytes": 21474836480,
            "seeders": 320,
            "leechers": 19,
            "quality_score": 96,
            "truespec": {"status": "verified"}
          },
          {
            "info_hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "resolution": "1080p",
            "source_type": "BluRay",
            "video_codec": "H.264",
            "size_bytes": 8589934592,
            "seeders": 140,
            "quality_score": 85
          }
        ]
      }
      """
        .trimIndent()

    val detail = TorrentClawJsonParser.parseDetailResponse(detailJson)
    assertNotNull(detail)
    assertEquals(2, detail?.torrents?.size)
    val firstRel = detail?.torrents?.first()
    assertEquals("Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.DV.HEVC", firstRel?.releaseName)
    assertEquals("2160p", firstRel?.resolution)
    assertEquals("5.1", firstRel?.audioChannels)
    assertEquals(TrueSpecStatus.VERIFIED, firstRel?.trueSpecStatus)

    val secondRel = detail?.torrents?.last()
    assertFalse(secondRel?.hasExplicitReleaseName ?: true)
    assertTrue(secondRel?.releaseName?.contains("Dune: Part Two") == true)
    assertTrue(secondRel?.releaseName?.contains("1080p") == true)

    val credits =
      TorrentClawJsonParser.parseCreditsResponse(
        """{"cast":[{"id":"1","name":"Timothée Chalamet","character":"Paul Atreides","profile_path":"/p.jpg"}]}"""
      )
    assertEquals(1, credits.size)
    assertEquals("Timothée Chalamet", credits.first().name)
    assertEquals("https://image.tmdb.org/t/p/w185/p.jpg", credits.first().profileUrl)

    val providers =
      TorrentClawJsonParser.parseWatchProvidersResponse(
        """{"results":{"US":{"flatrate":[{"provider_id":"8","provider_name":"Netflix","logo_path":"/n.jpg"}],"rent":[{"provider_id":"2","provider_name":"Apple TV"}]}}}"""
      )
    assertEquals(2, providers.size)
    assertEquals(WatchCategory.STREAM, providers[0].category)
    assertEquals(WatchCategory.RENT, providers[1].category)

    val collections =
      TorrentClawJsonParser.parseCollectionsResponse(
        """{"collections":[{"id":"top-sci-fi","name":"Sci-Fi Greats","item_count":15,"poster_paths":["/a.jpg"]}]}"""
      )
    assertEquals(1, collections.size)
    assertEquals("Sci-Fi Greats", collections.first().name)
  }

  @Test
  fun `synthesizeCleanReleaseName ignores raw hashes and falls back cleanly`() {
    val rawHash = "0123456789abcdef0123456789abcdef01234567"
    val (fromMagnetDn, isExplicit) =
      synthesizeCleanReleaseName(
        rawCandidates = listOf(rawHash, ""),
        infoHash = rawHash,
        magnetUri = "magnet:?xt=urn:btih:$rawHash&dn=Arrival.2016.1080p.BluRay.x264",
        fallbackTitle = "Arrival",
        resolution = "1080p",
        sourceType = "bluray",
        videoCodec = "H.264",
        audioCodec = "DTS",
        audioChannels = "5.1",
        hdrFormat = null,
      )
    assertTrue(isExplicit)
    assertEquals("Arrival.2016.1080p.BluRay.x264", fromMagnetDn)

    val (synthesized, explicitFlag) =
      synthesizeCleanReleaseName(
        rawCandidates = listOf(rawHash),
        infoHash = rawHash,
        magnetUri = null,
        fallbackTitle = "Arrival",
        resolution = "1080p",
        sourceType = "bluray",
        videoCodec = "HEVC",
        audioCodec = "AAC",
        audioChannels = "5.1",
        hdrFormat = "HDR10",
      )
    assertFalse(explicitFlag)
    assertFalse(synthesized.contains(rawHash))
    assertTrue(synthesized.contains("Arrival"))
    assertTrue(synthesized.contains("1080p"))
  }

  @Test
  fun `strict media scoping matches target media and rejects unrelated titles`() {
    val target =
      MediaItem(
        id = "odyssey-2026",
        tmdbId = 1368337,
        imdbId = "tt33764258",
        title = "The Odyssey",
        year = 2026,
      )

    val matchingByTmdb =
      TorrentRelease(
        infoHash = "1111",
        releaseName = "The.Odyssey.2026.2160p.WEB-DL",
        tmdbId = 1368337,
      )
    val wrongTmdb =
      TorrentRelease(
        infoHash = "2222",
        releaseName = "The.Odyssey.2026.1080p.FAKE",
        tmdbId = 999999,
      )
    val spaceOdyssey =
      TorrentRelease(
        infoHash = "3333",
        releaseName = "2001.A.Space.Odyssey.1968.2160p.UHD",
      )
    val matchingByNameAndYear =
      TorrentRelease(
        infoHash = "4444",
        releaseName = "The.Odyssey.2026.1080p.WEB-DL.HEVC",
      )

    assertTrue(matchesTargetMediaRelease(matchingByTmdb, target))
    assertFalse(matchesTargetMediaRelease(wrongTmdb, target))
    assertFalse(matchesTargetMediaRelease(spaceOdyssey, target))
    assertTrue(matchesTargetMediaRelease(matchingByNameAndYear, target))

    val sameTitleOtherYear = MediaItem(id = "other", title = "The Odyssey", year = 1997)
    assertFalse(matchesTargetMediaItem(sameTitleOtherYear, target))
  }

  @Test
  fun `filterAndSortTorrents preserves all qualities and sorts tv shows by release date and episode`() {
    val r4k =
      TorrentRelease(
        infoHash = "h1",
        releaseName = "Severance.S02E03.2160p.WEB-DL.HEVC.DV",
        resolution = "2160p",
        videoCodec = "HEVC",
        hdrFormat = "Dolby Vision",
        sizeBytes = 6_000_000_000L,
        seeders = 120,
        qualityScore = 95,
        trueSpecStatus = TrueSpecStatus.VERIFIED,
        seasonNumber = 2,
        episodeNumber = 3,
        releaseDate = "2025-01-31",
      )
    val r1080 =
      TorrentRelease(
        infoHash = "h2",
        releaseName = "Severance.S02E02.1080p.WEB-DL.x264",
        resolution = "1080p",
        videoCodec = "H.264",
        sizeBytes = 2_500_000_000L,
        seeders = 300,
        qualityScore = 84,
        seasonNumber = 2,
        episodeNumber = 2,
        releaseDate = "2025-01-24",
      )
    val r720 =
      TorrentRelease(
        infoHash = "h3",
        releaseName = "Severance.S02E01.720p.WEB-DL.x264",
        resolution = "720p",
        videoCodec = "H.264",
        sizeBytes = 1_200_000_000L,
        seeders = 210,
        qualityScore = 72,
        seasonNumber = 2,
        episodeNumber = 1,
        releaseDate = "2025-01-17",
      )

    val all = listOf(r720, r4k, r1080)
    val available = extractAvailableResolutions(all)
    assertEquals(listOf("2160p" to 1, "1080p" to 1, "720p" to 1), available)

    val merged = mergeFacetOptionsWithDefaults(STANDARD_RESOLUTION_OPTIONS, available)
    assertEquals(listOf("2160p" to 1, "1080p" to 1, "720p" to 1, "480p" to 0), merged)

    val sortedByDateAndEp =
      filterAndSortTorrents(
        all,
        TorrentListFilterState(sortBy = TorrentListSort.RELEASE_DATE_AND_EPISODE),
      )
    assertEquals(listOf("h1", "h2", "h3"), sortedByDateAndEp.map { it.infoHash })

    val sortedByEpAsc =
      filterAndSortTorrents(
        all,
        TorrentListFilterState(sortBy = TorrentListSort.EPISODE_ASC),
      )
    assertEquals(listOf("h3", "h2", "h1"), sortedByEpAsc.map { it.infoHash })

    val under2GbOnly =
      filterAndSortTorrents(
        all,
        TorrentListFilterState(sizeFilter = TorrentSizeFilter.UNDER_2_GB),
      )
    assertEquals(listOf("h3"), under2GbOnly.map { it.infoHash })

    val verifiedOnly =
      filterAndSortTorrents(
        all,
        TorrentListFilterState(verifiedOnly = true),
      )
    assertEquals(listOf("h1"), verifiedOnly.map { it.infoHash })

    assertTrue(matchesSeasonAndEpisode(r1080, seasonFilter = 2, episodeFilter = 2))
    assertFalse(matchesSeasonAndEpisode(r1080, seasonFilter = 1, episodeFilter = null))
  }

  @Test
  fun `deduplication, smart recommendation, magnet builder, and size formatting work properly`() {
    val low = TorrentRelease(infoHash = "ABC", releaseName = "Movie.1080p", qualityScore = 70, seeders = 10)
    val high = TorrentRelease(infoHash = "abc", releaseName = "Movie.1080p.PROPER", qualityScore = 88, seeders = 50)
    val deduped = deduplicateTorrents(listOf(low, high))
    assertEquals(1, deduped.size)
    assertEquals(88, deduped.first().qualityScore)

    val rec =
      computeSmartRecommendation(
        torrents = listOf(low, high),
        prefs = UserPreferencesState(preferredResolutions = setOf("2160p"), preferredCodecs = setOf("HEVC")),
      )
    assertEquals("abc", rec?.release?.infoHash)

    val magnet = buildMagnetUri("0123456789abcdef0123456789abcdef01234567", "Dune 2")
    assertTrue(magnet.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
    assertTrue(magnet.contains("&dn=Dune+2"))
    assertTrue(magnet.contains("&tr="))

    assertEquals("—", formatFileSize(0L))
    assertEquals("2.00 GB", formatFileSize(2L * 1024L * 1024L * 1024L))
  }

  @Test
  fun `json parser handles camelCase TorrentClaw go-client schema for covers, torrents, providers, credits, and stats`() {
    val popularJson =
      """
      {
        "items": [
          {
            "id": 42,
            "title": "Inception",
            "year": 2010,
            "contentType": "movie",
            "posterUrl": "http://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg",
            "ratingTmdb": "8.4",
            "ratingImdb": "8.8"
          }
        ],
        "total": 40,
        "page": 1,
        "pageSize": 20
      }
      """
        .trimIndent()

    val popular = TorrentClawJsonParser.parseSearchResponse(popularJson)
    assertEquals(1, popular.mediaItems.size)
    assertTrue(popular.hasMore)
    val popItem = popular.mediaItems.first()
    assertEquals("42", popItem.id)
    assertNull(popItem.tmdbId)
    assertEquals("https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg", popItem.posterUrl)
    assertEquals(8.4, popItem.rating ?: 0.0, 0.01)

    val searchJson =
      """
      {
        "total": 1,
        "page": 1,
        "pageSize": 25,
        "results": [
          {
            "id": 42,
            "imdbId": "tt1375666",
            "tmdbId": 27205,
            "contentType": "movie",
            "title": "Inception",
            "titleOriginal": "Inception",
            "year": 2010,
            "overview": "A thief who steals corporate secrets through dream-sharing technology.",
            "posterUrl": "https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg",
            "backdropUrl": "https://image.tmdb.org/t/p/w780/s3TBrRGB1ij7gFOCJDRKNSfZNfL.jpg",
            "ratingTmdb": "8.4",
            "genres": ["Action", "Sci-Fi"],
            "torrents": [
              {
                "infoHash": "1111222233334444555566667777888899990000",
                "rawTitle": "Inception.2010.2160p.UHD.BluRay.x265.HDR10-GROUP",
                "quality": "2160p",
                "codec": "HEVC",
                "sourceType": "BluRay",
                "audioCodec": "DTS-HD",
                "hdrType": "HDR10",
                "sizeBytes": 16106127360,
                "seeders": 420,
                "leechers": 18,
                "source": "yts",
                "magnet": "magnet:?xt=urn:btih:1111222233334444555566667777888899990000&dn=Inception.2010.2160p",
                "score": 93,
                "scanStatus": "verified"
              }
            ]
          }
        ]
      }
      """
        .trimIndent()

    val search = TorrentClawJsonParser.parseSearchResponse(searchJson)
    assertEquals(1, search.mediaItems.size)
    val searchItem = search.mediaItems.first()
    assertTrue(matchesTargetMediaItem(searchItem, popItem))
    assertEquals(1, searchItem.torrents.size)
    val rel = searchItem.torrents.first()
    assertEquals("Inception.2010.2160p.UHD.BluRay.x265.HDR10-GROUP", rel.releaseName)
    assertEquals("2160p", rel.resolution)
    assertEquals("HEVC", rel.videoCodec)
    assertEquals("HDR10", rel.hdrFormat)
    assertEquals("yts", rel.trackerSource)
    assertEquals(93, rel.qualityScore)
    assertEquals(TrueSpecStatus.VERIFIED, rel.trueSpecStatus)
    assertTrue(matchesTargetMediaRelease(rel, searchItem))

    val providers =
      TorrentClawJsonParser.parseWatchProvidersResponse(
        """{"contentId":42,"country":"US","providers":{"flatrate":[{"providerId":8,"name":"Netflix","logo":"https://image.tmdb.org/t/p/w92/n.jpg"}]}}"""
      )
    assertEquals(1, providers.size)
    assertEquals("Netflix", providers.first().providerName)
    assertEquals("https://image.tmdb.org/t/p/w92/n.jpg", providers.first().logoUrl)

    val credits =
      TorrentClawJsonParser.parseCreditsResponse(
        """{"contentId":42,"director":"Christopher Nolan","cast":[{"tmdbId":6193,"name":"Leonardo DiCaprio","character":"Cobb","profileUrl":"https://image.tmdb.org/t/p/w185/leo.jpg"}]}"""
      )
    assertEquals(2, credits.size)
    assertEquals("Christopher Nolan", credits.first().name)
    assertEquals("Leonardo DiCaprio", credits.last().name)

    val stats =
      TorrentClawJsonParser.parseStatsResponse(
        """{"content":{"movies":50000,"shows":14000,"tmdbEnriched":63000},"torrents":{"total":800000,"withSeeders":650000,"bySource":{"yts":100,"eztv":200}}}"""
      )
    assertEquals(50000, stats.totalMovies)
    assertEquals(14000, stats.totalShows)
    assertEquals(800000, stats.totalTorrents)
    assertEquals(2, stats.activeSources)
  }

  @Test
  fun `network module creates distinct OkHttpClients for Retrofit API and Coil images`() {
    val apiClient = TorrentClawNetworkModule.createApiOkHttpClient()
    val imageClient = TorrentClawNetworkModule.createImageOkHttpClient(apiClient)
    assertEquals(1, apiClient.interceptors.size)
    assertEquals(1, imageClient.interceptors.size)
    assertEquals(apiClient.connectionPool, imageClient.connectionPool)

    val retrofit =
      TorrentClawNetworkModule.createRetrofit(
        TorrentClawNetworkModule.PRIMARY_BASE_URL,
        apiClient,
      )
    assertEquals(TorrentClawNetworkModule.PRIMARY_BASE_URL, retrofit.baseUrl().toString())

    val service = TorrentClawNetworkModule.createApiService(okHttpClient = apiClient)
    assertNotNull(service)
    val wrapper = TorrentClawNetworkService(apiClient)
    assertNotNull(wrapper.okHttpClient)
  }

  @Test
  fun `json parser handles nested data envelopes, flat torrent entries, torrentUrl hashes, and formatted sizes`() {
    val flatJson =
      """
      {
        "data": {
          "results": [
            {
              "id": "dune-flat",
              "title": "Dune",
              "year": 2021,
              "posterUrl": "https://image.tmdb.org/t/p/w500/dune.jpg",
              "torrentUrl": "https://torrentclaw.com/api/v1/torrent/0123456789abcdef0123456789abcdef01234567",
              "quality": "1080p",
              "codec": "x264",
              "sizeFormatted": "2.5 GB",
              "seeders": 99,
              "score": 88
            }
          ]
        }
      }
      """
        .trimIndent()

    val parsed = TorrentClawJsonParser.parseSearchResponse(flatJson)
    assertEquals(1, parsed.mediaItems.size)
    val item = parsed.mediaItems.first()
    assertEquals(1, item.torrents.size)
    val rel = item.torrents.first()
    assertEquals("0123456789abcdef0123456789abcdef01234567", rel.infoHash)
    assertEquals("1080p", rel.resolution)
    assertEquals(2_684_354_560L, rel.sizeBytes)
    assertEquals(99, rel.seeders)
  }

  @Test
  fun `json parser handles nested data content envelopes, epoch dates, hdr normalization, and audio channels`() {
    val detailJson =
      """
      {
        "success": true,
        "data": {
          "content": {
            "id": 777,
            "tmdbId": 95396,
            "title": "Severance",
            "contentType": "show",
            "releaseDate": "2022-02-18",
            "tagline": "We're all different people.",
            "status": "Returning Series",
            "originalLanguage": "en",
            "ratingTmdb": 8.4,
            "ratingImdb": 8.7,
            "numberOfSeasons": 2,
            "numberOfEpisodes": 19
          },
          "torrents": [
            {
              "infoHash": "fedcba9876543210fedcba9876543210fedcba98",
              "rawTitle": "[EZTV] Severance.S02E05.2160p.WEB-DL.x265.DV-FLUX",
              "quality": "2160p",
              "codec": "x265",
              "hdr": "dolby_vision",
              "audio": "TrueHD Atmos 7.1",
              "language": "en",
              "fileCount": 2,
              "sizeBytes": 5368709120,
              "seeders": 240,
              "leechers": 15,
              "qualityScore": 95,
              "uploadedAt": 1739491200,
              "source": "eztv"
            }
          ]
        }
      }
      """
        .trimIndent()

    val media = TorrentClawJsonParser.parseDetailResponse(detailJson)
    assertNotNull(media)
    assertEquals("Severance", media?.title)
    assertEquals(MediaType.SHOW, media?.mediaType)
    assertEquals("Returning Series", media?.status)
    assertEquals("We're all different people.", media?.tagline)
    assertEquals(8.7, media?.imdbRating ?: 0.0, 0.01)
    assertEquals(2, media?.numberOfSeasons)
    assertEquals(19, media?.numberOfEpisodes)
    assertEquals("Feb 18, 2022", com.example.data.model.formatDisplayDate(media?.releaseDate))

    assertEquals(1, media?.torrents?.size)
    val rel = media?.torrents?.first()!!
    assertEquals("Dolby Vision", rel.hdrFormat)
    assertEquals("TrueHD Atmos", rel.audioCodec)
    assertEquals("7.1", rel.audioChannels)
    assertEquals("en", rel.language)
    assertEquals("FLUX", rel.releaseGroup)
    assertEquals(2, rel.fileCount)
    assertEquals("2025-02-14", rel.releaseDate)
    assertEquals("Feb 14, 2025", com.example.data.model.formatDisplayDate(rel.releaseDate))
    assertEquals("S02E05", com.example.data.model.formatSeasonEpisodeLabel(rel.seasonNumber, rel.episodeNumber))
    assertTrue(matchesTargetMediaRelease(rel, media))
  }

  @Test
  fun `json parser handles scrapedAt, hasTorrents, editionTags, and TorrentClawHttpException preserves status code`() {
    val searchJson =
      """
      {
        "results": [
          {
            "id": 9001,
            "imdbId": "tt22084616",
            "title": "Spider-Man: Brand New Day",
            "year": 2026,
            "contentType": "movie",
            "hasTorrents": false,
            "torrents": []
          },
          {
            "id": 9002,
            "imdbId": "tt15239678",
            "title": "Dune: Part Two",
            "year": 2024,
            "contentType": "movie",
            "hasTorrents": true,
            "torrents": [
              {
                "infoHash": "1234567890abcdef1234567890abcdef12345678",
                "rawTitle": "Dune.Part.Two.2024.2160p.WEB-DL.PROPER.REPACK",
                "quality": "2160p",
                "codec": "HEVC",
                "sizeBytes": 10737418240,
                "seeders": 500,
                "leechers": 20,
                "scrapedAt": "2025-03-01T12:00:00Z",
                "isProper": true,
                "isRepack": true,
                "isRemastered": false
              }
            ]
          }
        ]
      }
      """
        .trimIndent()

    val parsed = TorrentClawJsonParser.parseSearchResponse(searchJson)
    assertEquals(2, parsed.mediaItems.size)
    assertEquals(false, parsed.mediaItems[0].hasTorrents)
    assertEquals(true, parsed.mediaItems[1].hasTorrents)

    val rel = parsed.mediaItems[1].torrents.first()
    assertEquals("Mar 1, 2025", com.example.data.model.formatDisplayDate(rel.releaseDate))
    assertEquals(listOf("PROPER", "REPACK"), rel.editionTags)

    val httpErr = com.example.data.network.TorrentClawHttpException(429, "Rate limit reached")
    assertEquals(429, httpErr.statusCode)
    assertEquals("Rate limit reached", httpErr.message)
  }

  @Test
  fun `json parser keeps anonymous TorrentClaw torrents without infoHash and parses streaming and seasons`() {
    val liveAnonymousSearchJson =
      """
      {
        "total": 1,
        "page": 1,
        "pageSize": 5,
        "_notice": "Magnet links, info hashes, and torrent downloads require a free API key. Register at https://torrentclaw.com/register",
        "results": [
          {
            "id": 73277,
            "imdbId": "tt22084616",
            "tmdbId": 969681,
            "contentType": "movie",
            "title": "Spider-Man: Brand New Day",
            "year": 2026,
            "contentUrl": "/movies/spider-man-brand-new-day-2026-73277",
            "hasTorrents": true,
            "streaming": {
              "flatrate": [],
              "rent": [],
              "buy": [
                {
                  "providerId": 7,
                  "name": "Fandango At Home",
                  "logo": "https://image.tmdb.org/t/p/w92/oIXE9vJdkilIxCLAtlbtiqewgvn.jpg"
                }
              ]
            },
            "seasons": [
              {"season": 1, "episodeCount": 9},
              {"season": 2, "episodeCount": 10}
            ],
            "torrents": [
              {
                "rawTitle": "Spider-Man Brand New Day 2026 2160p HDR10Plus DV BluRay 8CH x265 HEVC",
                "quality": "2160p",
                "codec": "hevc",
                "sourceType": "bluray",
                "sizeBytes": 5991479377,
                "seeders": 112,
                "leechers": 76,
                "source": "nexarr:LimeTorrents",
                "qualityScore": 93,
                "uploadedAt": "2026-11-24T17:42:14.000Z",
                "languages": ["en"],
                "audioCodec": "aac",
                "audioTracks": [{"lang": "en", "codec": "aac", "channels": 8}],
                "trueSpec": {"resolution": "2160p", "videoCodec": "hevc", "hdr": "DV+HDR10"}
              },
              {
                "rawTitle": "Spider-Man.Brand.New.Day.2026.1080p.WEB-DL.DDP5.1.H.264",
                "quality": "1080p",
                "codec": "h264",
                "sourceType": "web-dl",
                "sizeBytes": 3200000000,
                "seeders": 240,
                "leechers": 18,
                "source": "nexarr:YTS",
                "qualityScore": 84,
                "uploadedAt": "2026-11-23T12:00:00.000Z",
                "audioCodec": "5.1",
                "audioChannels": 6,
                "audioTracks": [{"lang": "en", "codec": "eac3", "channels": 6}]
              },
              {
                "rawTitle": "Spider-Man.Brand.New.Day.2026.1080p.WEB-DL.DDP5.1.H.264",
                "quality": "1080p",
                "codec": "h264",
                "sourceType": "web-dl",
                "sizeBytes": 3200000000,
                "seeders": 245,
                "leechers": 18,
                "source": "nexarr:1337x",
                "qualityScore": 85,
                "uploadedAt": "2026-11-23T12:00:00.000Z"
              }
            ]
          }
        ]
      }
      """
        .trimIndent()

    val parsed = TorrentClawJsonParser.parseSearchResponse(liveAnonymousSearchJson)
    assertEquals(1, parsed.mediaItems.size)
    val item = parsed.mediaItems.first()
    assertEquals("https://torrentclaw.com/movies/spider-man-brand-new-day-2026-73277", item.resolvedContentWebUrl)
    assertEquals(2, item.numberOfSeasons)
    assertEquals(19, item.numberOfEpisodes)
    assertEquals(1, item.watchProviders.size)
    assertEquals("Fandango At Home", item.watchProviders.first().providerName)
    assertEquals(WatchCategory.BUY, item.watchProviders.first().category)

    // All 3 anonymous torrents (missing infoHash/magnetUrl) are preserved
    assertEquals(3, item.torrents.size)

    val first = item.torrents[0]
    assertFalse(first.hasDirectMagnet)
    assertEquals("", first.resolvedMagnetUrl)
    assertEquals("https://torrentclaw.com/movies/spider-man-brand-new-day-2026-73277", first.resolvedContentWebUrl)
    assertEquals("2160p", first.resolution)
    assertEquals("aac", first.audioCodec)
    assertEquals("7.1", first.audioChannels)
    assertEquals("DV+HDR10", first.hdrFormat)
    assertEquals(TrueSpecStatus.VERIFIED, first.trueSpecStatus)

    val second = item.torrents[1]
    assertEquals("1080p", second.resolution)
    assertEquals("eac3", second.audioCodec)
    assertEquals("5.1", second.audioChannels)

    // Deduplication keeps distinct tracker sources even when infoHash is blank
    val deduped = deduplicateTorrents(item.torrents)
    assertEquals(3, deduped.size)
  }

  @Test
  fun `filterAndSortTorrents supports multi-token search across release name, codec, source, and episode`() {
    val t1 =
      TorrentRelease(
        infoHash = "t1",
        releaseName = "Severance.S02E05.2160p.WEB-DL.x265-FLUX",
        resolution = "2160p",
        videoCodec = "HEVC",
        trackerSource = "nexarr:EZTV",
        releaseGroup = "FLUX",
        seasonNumber = 2,
        episodeNumber = 5,
        qualityScore = 94,
      )
    val t2 =
      TorrentRelease(
        infoHash = "t2",
        releaseName = "Severance.S02E05.1080p.WEB-DL.H264-NTb",
        resolution = "1080p",
        videoCodec = "H.264",
        trackerSource = "nexarr:1337x",
        releaseGroup = "NTb",
        seasonNumber = 2,
        episodeNumber = 5,
        qualityScore = 84,
      )
    val t3 =
      TorrentRelease(
        infoHash = "t3",
        releaseName = "Severance.S02E04.1080p.WEB-DL.x265-FLUX",
        resolution = "1080p",
        videoCodec = "HEVC",
        trackerSource = "nexarr:YTS",
        releaseGroup = "FLUX",
        seasonNumber = 2,
        episodeNumber = 4,
        qualityScore = 88,
      )

    val all = listOf(t1, t2, t3)
    val byCodecAndGroup = filterAndSortTorrents(all, TorrentListFilterState(keyword = "hevc flux"))
    assertEquals(listOf("t1", "t3"), byCodecAndGroup.map { it.infoHash })

    val bySourceAndEpisode = filterAndSortTorrents(all, TorrentListFilterState(keyword = "1337x s02e05"))
    assertEquals(listOf("t2"), bySourceAndEpisode.map { it.infoHash })
  }

  @Test
  fun `loadMediaDetail and loadMoreMediaTorrents expand capped buckets round-robin and recover cleanly from 429`() =
    runBlocking {
      val requestedParams = mutableListOf<Map<String, String?>>()
      var simulate429OnCodec = "x264"

      fun makeTorrentJson(id: String, quality: String, codec: String, score: Int): String =
        """
        {
          "rawTitle": "Test.Movie.2026.$id.$quality.WEB-DL.$codec-GRP",
          "quality": "$quality",
          "codec": "$codec",
          "sourceType": "webdl",
          "sizeBytes": 3000000000,
          "seeders": ${100 + score},
          "leechers": 10,
          "source": "nexarr:1337x",
          "qualityScore": $score
        }
        """.trimIndent()

      fun wrapSearchJson(torrentsJson: List<String>): String =
        """
        {
          "total": 1,
          "page": 1,
          "pageSize": 50,
          "results": [
            {
              "id": 501,
              "imdbId": "tt9999501",
              "tmdbId": 999501,
              "contentType": "movie",
              "title": "Test Movie",
              "year": 2026,
              "hasTorrents": true,
              "torrents": [${torrentsJson.joinToString(",")}]
            }
          ]
        }
        """.trimIndent()

      val fakeNetwork =
        object : TorrentClawNetworkService() {
          override suspend fun search(params: Map<String, String?>): Result<String> {
            requestedParams.add(params)
            val codec = params["codec"]
            if (codec == simulate429OnCodec) {
              return Result.failure(TorrentClawHttpException(429, "Rate limit reached briefly."))
            }
            val quality = params["quality"]
            val source = params["source"]
            val list =
              when {
                quality == null ->
                  (1..15).map { idx -> makeTorrentJson("init_$idx", "2160p", "x265", 90 - idx) }
                quality == "2160p" && codec == null && source == null && params["size_max_gb"] == null && params["size_min_gb"] == null ->
                  (1..15).map { idx -> makeTorrentJson("2160_top_$idx", "2160p", "x265", 95 - idx) }
                quality == "1080p" && codec == null && source == null && params["size_max_gb"] == null && params["size_min_gb"] == null ->
                  (1..15).map { idx -> makeTorrentJson("1080_top_$idx", "1080p", "x264", 88 - idx) }
                quality == "720p" && codec == null && source == null ->
                  (1..4).map { idx -> makeTorrentJson("720_only_$idx", "720p", "x264", 70 - idx) }
                quality == "480p" && codec == null && source == null ->
                  emptyList()
                quality == "2160p" && codec == "x265" ->
                  (16..20).map { idx -> makeTorrentJson("2160_x265_$idx", "2160p", "x265", 80 - idx) }
                quality == "1080p" && codec == "x265" ->
                  (16..22).map { idx -> makeTorrentJson("1080_x265_$idx", "1080p", "x265", 82 - idx) }
                quality == "2160p" && codec == "x264" ->
                  (21..23).map { idx -> makeTorrentJson("2160_x264_$idx", "2160p", "x264", 75 - idx) }
                quality == "1080p" && codec == "x264" ->
                  (23..28).map { idx -> makeTorrentJson("1080_x264_$idx", "1080p", "x264", 78 - idx) }
                else -> emptyList()
              }
            return Result.success(wrapSearchJson(list))
          }
        }

      val repo = TorrentClawRepository(android.content.ContextWrapper(null), fakeNetwork)
      val target =
        MediaItem(
          id = "501",
          imdbId = "tt9999501",
          tmdbId = 999501,
          title = "Test Movie",
          year = 2026,
          mediaType = MediaType.MOVIE,
        )

      val detail = repo.loadMediaDetail("501", target).getOrThrow()
      // Initial 15 + 15(2160) + 15(1080) + 4(720) + Wave 2 x265 (5 in 2160 + 7 in 1080) -> all unique across qualities
      // Note: initial 2160_top overlapped init_? No, distinct names init_1..15 + 2160_top_1..15 + 1080_top_1..15 + 720_only_1..4 + 2160_x265_16..20 + 1080_x265_16..22 = 61
      assertTrue("Expected > 50 torrents after Wave 1+2, got ${detail.torrents.size}", detail.torrents.size >= 55)
      assertTrue(detail.hasMoreTorrents)

      // During loadMoreMediaTorrents, simulate 429 on x264: should preserve prior torrents & warn
      val morePaused = repo.loadMoreMediaTorrents(detail.media, detail.torrents).getOrThrow()
      assertNotNull(morePaused.warningMessage)
      assertTrue(morePaused.hasMoreTorrents)
      assertTrue(morePaused.torrents.size >= detail.torrents.size)

      // Clear 429 and tap Load More again: previously 429'd x264 queries must run and add 2160_x264 + 1080_x264!
      simulate429OnCodec = "NONE"
      val moreResumed = repo.loadMoreMediaTorrents(detail.media, morePaused.torrents).getOrThrow()
      assertTrue(moreResumed.torrents.any { it.releaseName.contains("1080_x264_28") })
      assertTrue(moreResumed.torrents.any { it.releaseName.contains("2160_x264_23") })
      assertTrue(moreResumed.torrents.size > morePaused.torrents.size)
    }

  @Test
  fun `loadMediaDetail and loadMoreMediaTorrents fetch multi-season TV shows and focusSeason`() =
    runBlocking {
      fun wrapShowJson(torrentsJson: List<String>): String =
        """
        {
          "total": 1,
          "page": 1,
          "pageSize": 50,
          "results": [
            {
              "id": 777,
              "imdbId": "tt11280740",
              "tmdbId": 95396,
              "contentType": "show",
              "title": "Severance",
              "year": 2022,
              "hasTorrents": true,
              "seasons": [
                {"season": 1, "episodeCount": 9},
                {"season": 2, "episodeCount": 10},
                {"season": 3, "episodeCount": 8}
              ],
              "torrents": [${torrentsJson.joinToString(",")}]
            }
          ]
        }
        """.trimIndent()

      fun epJson(season: Int, ep: Int, q: String): String =
        """
        {
          "rawTitle": "Severance.S0${season}E0$ep.$q.WEB-DL.x265-FLUX",
          "quality": "$q",
          "codec": "x265",
          "season": $season,
          "episode": $ep,
          "sizeBytes": 2000000000,
          "seeders": 90,
          "qualityScore": 85
        }
        """.trimIndent()

      val fakeNetwork =
        object : TorrentClawNetworkService() {
          override suspend fun search(params: Map<String, String?>): Result<String> {
            val s = params["season"]?.toIntOrNull()
            val q = params["quality"]
            val list =
              when {
                s == 1 && q == null -> listOf(epJson(1, 1, "1080p"), epJson(1, 2, "720p"))
                s == 2 && q == null -> listOf(epJson(2, 1, "2160p"))
                s == 3 && q == null -> listOf(epJson(3, 1, "1080p"))
                s == null && q == "2160p" -> listOf(epJson(2, 5, "2160p"))
                s == null && q == "1080p" -> listOf(epJson(2, 4, "1080p"))
                s == null -> listOf(epJson(2, 3, "2160p"))
                else -> emptyList()
              }
            return Result.success(wrapShowJson(list))
          }
        }

      val repo = TorrentClawRepository(android.content.ContextWrapper(null), fakeNetwork)
      val show =
        MediaItem(
          id = "777",
          imdbId = "tt11280740",
          tmdbId = 95396,
          title = "Severance",
          year = 2022,
          mediaType = MediaType.SHOW,
          numberOfSeasons = 3,
        )

      val detail = repo.loadMediaDetail("777", show).getOrThrow()
      // Wave 2 fetches all 3 seasons (3, 2, 1) since maxQueries=3!
      assertTrue(detail.torrents.any { it.seasonNumber == 1 && it.episodeNumber == 1 })
      assertTrue(detail.torrents.any { it.seasonNumber == 2 })
      assertTrue(detail.torrents.any { it.seasonNumber == 3 })
    }

  @Test
  fun `favorite entries serialize round-trip and categorize cleanly by movie vs tv show`() {
    val movie =
      MediaItem(
        id = "693134",
        tmdbId = 693134,
        imdbId = "tt15239678",
        title = "Dune: Part Two",
        year = 2024,
        mediaType = MediaType.MOVIE,
        rating = 8.3,
        runtimeMinutes = 166,
        genres = listOf("Sci-Fi", "Adventure"),
        posterUrl = "https://image.tmdb.org/t/p/w500/dune2.jpg",
        torrentCount = 42,
      )
    val show =
      MediaItem(
        id = "95396",
        tmdbId = 95396,
        imdbId = "tt11280740",
        title = "Severance",
        year = 2022,
        mediaType = MediaType.SHOW,
        rating = 8.7,
        numberOfSeasons = 2,
        numberOfEpisodes = 19,
        genres = listOf("Drama", "Mystery"),
        posterUrl = "https://image.tmdb.org/t/p/w500/severance.jpg",
        torrentCount = 68,
      )

    val entries =
      listOf(
        movie.toFavoriteEntry(savedAtMs = 1000L),
        show.toFavoriteEntry(savedAtMs = 2000L),
      )
    val encoded = kotlinx.serialization.json.Json.encodeToString(entries)
    val decoded =
      kotlinx.serialization.json.Json.decodeFromString<List<com.example.data.model.FavoriteMediaEntry>>(
        encoded
      )
    val restored = decoded.map { it.toMediaItem() }

    assertEquals(2, restored.size)
    assertEquals(MediaType.MOVIE, restored[0].mediaType)
    assertEquals("Dune: Part Two", restored[0].title)
    assertEquals(166, restored[0].runtimeMinutes)
    assertEquals(MediaType.SHOW, restored[1].mediaType)
    assertEquals("Severance", restored[1].title)
    assertEquals(2, restored[1].numberOfSeasons)
    assertEquals(19, restored[1].numberOfEpisodes)

    val movies = restored.filter { it.mediaType == MediaType.MOVIE }
    val shows = restored.filter { it.mediaType == MediaType.SHOW }
    assertEquals(listOf("Dune: Part Two"), movies.map { it.title })
    assertEquals(listOf("Severance"), shows.map { it.title })

    // Identity matching works across IMDb and TMDB + MediaType, while distinguishing Movie vs Show
    val sameMovieByImdb = MediaItem(id = "other", imdbId = "TT15239678", title = "Dune 2", mediaType = MediaType.MOVIE)
    assertTrue(com.example.data.model.isSameFavoriteMedia(movie, sameMovieByImdb))

    val differentTypeSameTmdb = MediaItem(id = "95396", tmdbId = 95396, title = "Severance", mediaType = MediaType.MOVIE)
    assertFalse(com.example.data.model.isSameFavoriteMedia(show, differentTypeSameTmdb))
  }
}
