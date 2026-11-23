package com.example.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.data.local.CachedDiscoveryDao
import com.example.data.local.CachedDiscoveryEntity
import com.example.data.model.ApiHealthState
import com.example.data.model.AutocompleteSuggestion
import com.example.data.model.CollectionSummary
import com.example.data.model.CreditPerson
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.PlatformStats
import com.example.data.model.SearchFilter
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecReport
import com.example.data.model.WatchProvider
import com.example.data.model.deduplicateTorrents
import com.example.data.model.parseImdbOrTmdbUrl
import com.example.data.paging.SearchPagingSource
import com.example.data.remote.DebridService
import com.example.data.remote.NetworkModule
import com.example.data.remote.SearchParseResult
import com.example.data.remote.TorrentClawJsonParser
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class DiscoverySnapshot(
  val trending: List<MediaItem> = emptyList(),
  val popularMovies: List<MediaItem> = emptyList(),
  val popularShows: List<MediaItem> = emptyList(),
  val recentTorrents: List<MediaItem> = emptyList(),
  val collections: List<CollectionSummary> = emptyList(),
  val genres: List<String> = emptyList(),
  val stats: PlatformStats? = null,
)

data class MediaDetailBundle(
  val media: MediaItem,
  val credits: List<CreditPerson> = emptyList(),
  val watchProviders: List<WatchProvider> = emptyList(),
  val torrents: List<TorrentRelease> = emptyList(),
)

class MediaRepository(
  private val context: Context,
  private val networkModule: NetworkModule,
  private val cachedDiscoveryDao: CachedDiscoveryDao,
  private val authRepository: AuthRepository,
  private val debridService: DebridService,
) {
  private val api = networkModule.torrentClawApi
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val _apiHealth = MutableStateFlow<ApiHealthState>(ApiHealthState.Connected)
  val apiHealth: StateFlow<ApiHealthState> = _apiHealth.asStateFlow()

  private val mediaMemoryCache = LinkedHashMap<String, MediaItem>(64, 0.75f, true)

  private fun cacheMediaItems(items: List<MediaItem>) {
    synchronized(mediaMemoryCache) {
      items.forEach { item ->
        if (item.id.isNotBlank()) mediaMemoryCache[item.id] = item
      }
    }
  }

  fun getCachedMedia(id: String): MediaItem? = synchronized(mediaMemoryCache) { mediaMemoryCache[id] }

  suspend fun loadDiscovery(forceRefresh: Boolean = false): DiscoverySnapshot =
    withContext(Dispatchers.IO) {
      coroutineScope {
        val trendingDeferred = async { fetchMediaBucket("trending") { api.getTrending(limit = 16) } }
        val popMoviesDeferred =
          async { fetchMediaBucket("popular_movie") { api.getPopular(type = "movie", limit = 16) } }
        val popShowsDeferred =
          async { fetchMediaBucket("popular_show") { api.getPopular(type = "show", limit = 16) } }
        val recentDeferred = async { fetchMediaBucket("recent") { api.getRecent(limit = 16) } }
        val collectionsDeferred = async { fetchCollectionsBucket() }
        val genresDeferred = async { fetchGenresBucket() }
        val statsDeferred = async { fetchStats() }

        val trending = trendingDeferred.await()
        val popMovies = popMoviesDeferred.await()
        val popShows = popShowsDeferred.await()
        val recent = recentDeferred.await()
        val collections = collectionsDeferred.await()
        val genres = genresDeferred.await()
        val stats = statsDeferred.await()

        cacheMediaItems(trending + popMovies + popShows + recent)

        DiscoverySnapshot(
          trending = trending,
          popularMovies = popMovies,
          popularShows = popShows,
          recentTorrents = recent,
          collections = collections,
          genres = genres,
          stats = stats,
        )
      }
    }

  private suspend fun fetchMediaBucket(
    bucketKey: String,
    call: suspend () -> retrofit2.Response<okhttp3.ResponseBody>,
  ): List<MediaItem> {
    return try {
      val response = call()
      if (response.isSuccessful) {
        val raw = response.body()?.string().orEmpty()
        val items = TorrentClawJsonParser.parseMediaList(raw)
        if (raw.isNotBlank()) {
          cachedDiscoveryDao.upsert(CachedDiscoveryEntity(bucketKey, raw))
        }
        _apiHealth.value = ApiHealthState.Connected
        items
      } else if (response.code() == 429) {
        val retryAfter = response.headers()["Retry-After"]?.toIntOrNull() ?: 30
        _apiHealth.value = ApiHealthState.RateLimited(retryAfter)
        loadCachedMediaBucket(bucketKey)
      } else {
        val cached = loadCachedMediaBucket(bucketKey)
        _apiHealth.value =
          if (cached.isNotEmpty()) {
            ApiHealthState.OfflineCached("Showing cached catalog (HTTP ${response.code()})")
          } else {
            ApiHealthState.Error("TorrentClaw returned HTTP ${response.code()}")
          }
        cached
      }
    } catch (e: Exception) {
      val cached = loadCachedMediaBucket(bucketKey)
      _apiHealth.value =
        if (cached.isNotEmpty()) {
          ApiHealthState.OfflineCached("Offline — showing cached catalog")
        } else {
          ApiHealthState.Error(e.localizedMessage ?: "Network request failed")
        }
      cached
    }
  }

  private suspend fun loadCachedMediaBucket(bucketKey: String): List<MediaItem> {
    val cached = cachedDiscoveryDao.getBucket(bucketKey)?.payloadJson ?: return emptyList()
    return TorrentClawJsonParser.parseMediaList(cached)
  }

  private suspend fun fetchCollectionsBucket(): List<CollectionSummary> {
    return try {
      val response = api.getCollections(page = 1, limit = 16)
      if (response.isSuccessful) {
        val raw = response.body()?.string().orEmpty()
        if (raw.isNotBlank()) cachedDiscoveryDao.upsert(CachedDiscoveryEntity("collections", raw))
        TorrentClawJsonParser.parseCollections(raw)
      } else {
        cachedDiscoveryDao.getBucket("collections")?.payloadJson?.let(TorrentClawJsonParser::parseCollections)
          ?: emptyList()
      }
    } catch (_: Exception) {
      cachedDiscoveryDao.getBucket("collections")?.payloadJson?.let(TorrentClawJsonParser::parseCollections)
        ?: emptyList()
    }
  }

  private suspend fun fetchGenresBucket(): List<String> {
    return try {
      val response = api.getGenres()
      if (response.isSuccessful) {
        val raw = response.body()?.string().orEmpty()
        if (raw.isNotBlank()) cachedDiscoveryDao.upsert(CachedDiscoveryEntity("genres", raw))
        TorrentClawJsonParser.parseGenres(raw)
      } else {
        cachedDiscoveryDao.getBucket("genres")?.payloadJson?.let(TorrentClawJsonParser::parseGenres)
          ?: defaultGenres()
      }
    } catch (_: Exception) {
      cachedDiscoveryDao.getBucket("genres")?.payloadJson?.let(TorrentClawJsonParser::parseGenres)
        ?: defaultGenres()
    }
  }

  private fun defaultGenres(): List<String> =
    listOf(
      "Action",
      "Adventure",
      "Animation",
      "Comedy",
      "Crime",
      "Documentary",
      "Drama",
      "Fantasy",
      "Horror",
      "Mystery",
      "Sci-Fi",
      "Thriller",
    )

  private suspend fun fetchStats(): PlatformStats? {
    return runCatching {
      val resp = api.getStats()
      if (resp.isSuccessful) {
        val raw = resp.body()?.string().orEmpty()
        TorrentClawJsonParser.parseStats(raw)
      } else null
    }.getOrNull()
  }

  suspend fun autocomplete(query: String): List<AutocompleteSuggestion> =
    withContext(Dispatchers.IO) {
      if (query.trim().length < 2) return@withContext emptyList()
      runCatching {
        val response = api.autocomplete(query = query.trim(), limit = 8)
        if (response.isSuccessful) {
          TorrentClawJsonParser.parseAutocomplete(response.body()?.string().orEmpty())
        } else emptyList()
      }.getOrDefault(emptyList())
    }

  fun createSearchPager(filter: SearchFilter): Flow<PagingData<MediaItem>> {
    return Pager(
      config = PagingConfig(pageSize = 20, enablePlaceholders = false, initialLoadSize = 20),
      pagingSourceFactory = { SearchPagingSource(this, filter) },
    ).flow
  }

  suspend fun searchPage(filter: SearchFilter, page: Int, limit: Int): SearchParseResult =
    withContext(Dispatchers.IO) {
      val parsedUrl = parseImdbOrTmdbUrl(filter.query)
      val effectiveQuery =
        if (parsedUrl != null) null else filter.query.trim().takeIf { it.isNotEmpty() }
      val effectiveType =
        parsedUrl?.mediaType?.apiValue
          ?: filter.mediaType.apiValue.takeIf { it.isNotEmpty() }

      val response =
        api.search(
          query = effectiveQuery,
          type = effectiveType,
          genre = filter.genre?.takeIf { it.isNotBlank() },
          yearMin = filter.yearMin,
          yearMax = filter.yearMax,
          minRating = filter.minRating,
          quality = filter.resolutions.firstOrNull(),
          codec = filter.codecs.firstOrNull(),
          audio = filter.audioFormats.firstOrNull(),
          hdr = filter.hdrFormats.firstOrNull(),
          lang = filter.languages.firstOrNull(),
          season = filter.season,
          episode = filter.episode,
          minSeeders = filter.minSeeders,
          minScore = filter.minQualityScore,
          sort = filter.sortBy.apiValue,
          tmdbId = parsedUrl?.tmdbId,
          imdbId = parsedUrl?.imdbId,
          page = page,
          limit = limit,
        )

      if (response.code() == 429) {
        val retryAfter = response.headers()["Retry-After"]?.toIntOrNull() ?: 30
        _apiHealth.value = ApiHealthState.RateLimited(retryAfter)
        throw IllegalStateException("Rate limit exceeded. Retry in ${retryAfter}s.")
      }
      if (!response.isSuccessful) {
        val err = "Search failed (HTTP ${response.code()})"
        _apiHealth.value = ApiHealthState.Error(err)
        throw IllegalStateException(err)
      }

      _apiHealth.value = ApiHealthState.Connected
      val raw = response.body()?.string().orEmpty()
      val parsed = TorrentClawJsonParser.parseSearchResponse(raw)

      // Apply multi-value client filters when multiple resolutions/codecs/HDR/size caps are active
      val filteredMedia =
        parsed.mediaItems.map { item ->
          val deduped = filterAndEnrichTorrents(deduplicateTorrents(item.torrents), filter)
          item.copy(torrents = deduped)
        }
      val filteredTorrents = filterAndEnrichTorrents(deduplicateTorrents(parsed.torrents), filter)

      cacheMediaItems(filteredMedia)
      SearchParseResult(
        mediaItems = filteredMedia,
        torrents = filteredTorrents,
        total = parsed.total,
      )
    }

  private fun filterAndEnrichTorrents(
    torrents: List<TorrentRelease>,
    filter: SearchFilter,
  ): List<TorrentRelease> {
    return torrents.filter { t ->
      val resOk =
        filter.resolutions.isEmpty() ||
          filter.resolutions.any { it.equals(t.resolution, ignoreCase = true) }
      val sourceOk =
        filter.sourceQualities.isEmpty() ||
          filter.sourceQualities.any { t.sourceType?.contains(it, ignoreCase = true) == true }
      val codecOk =
        filter.codecs.isEmpty() ||
          filter.codecs.any { t.videoCodec?.contains(it, ignoreCase = true) == true }
      val audioOk =
        filter.audioFormats.isEmpty() ||
          filter.audioFormats.any { t.audioCodec?.contains(it, ignoreCase = true) == true }
      val hdrOk =
        filter.hdrFormats.isEmpty() ||
          filter.hdrFormats.any { t.hdrFormat?.contains(it, ignoreCase = true) == true }
      val sizeOk =
        filter.maxSizeGb == null ||
          t.sizeBytes <= 0L ||
          t.sizeBytes <= (filter.maxSizeGb * 1024f * 1024f * 1024f).toLong()
      val seedersOk = filter.minSeeders == null || t.seeders >= filter.minSeeders
      val scoreOk = filter.minQualityScore == null || t.qualityScore >= filter.minQualityScore
      resOk && sourceOk && codecOk && audioOk && hdrOk && sizeOk && seedersOk && scoreOk
    }
  }

  suspend fun loadMediaDetail(
    id: String,
    seasonFilter: Int? = null,
    episodeFilter: Int? = null,
    region: String = "US",
  ): Result<MediaDetailBundle> =
    withContext(Dispatchers.IO) {
      runCatching {
        coroutineScope {
          val detailDeferred = async {
            runCatching {
              val r = api.getContentDetail(id)
              if (r.isSuccessful) {
                TorrentClawJsonParser.parseMediaDetail(r.body()?.string().orEmpty(), id)
              } else null
            }.getOrNull()
          }
          val creditsDeferred = async {
            runCatching {
              val r = api.getContentCredits(id)
              if (r.isSuccessful) TorrentClawJsonParser.parseCredits(r.body()?.string().orEmpty())
              else emptyList()
            }.getOrDefault(emptyList())
          }

          val baseMedia =
            detailDeferred.await()
              ?: getCachedMedia(id)
              ?: throw IllegalStateException("Unable to load details for this title.")

          val watchProvidersDeferred = async {
            runCatching {
              val r =
                api.getWatchProviders(
                  tmdbId = baseMedia.tmdbId,
                  imdbId = baseMedia.imdbId,
                  country = region,
                )
              if (r.isSuccessful) TorrentClawJsonParser.parseWatchProviders(r.body()?.string().orEmpty())
              else emptyList()
            }.getOrDefault(emptyList())
          }

          val torrentsDeferred = async {
            runCatching {
              val r =
                api.search(
                  query = if (baseMedia.tmdbId == null && baseMedia.imdbId == null) baseMedia.title else null,
                  tmdbId = baseMedia.tmdbId,
                  imdbId = baseMedia.imdbId,
                  type = baseMedia.mediaType.apiValue,
                  season = seasonFilter,
                  episode = episodeFilter,
                  sort = "quality_score",
                  limit = 50,
                )
              if (r.isSuccessful) {
                val parsed =
                  TorrentClawJsonParser.parseSearchResponse(
                    raw = r.body()?.string().orEmpty(),
                    fallbackTitleId = baseMedia.id,
                    fallbackMediaTitle = baseMedia.title,
                    fallbackPoster = baseMedia.posterUrl,
                  )
                val combined =
                  parsed.torrents + parsed.mediaItems.flatMap { it.torrents } + baseMedia.torrents
                deduplicateTorrents(combined)
              } else {
                deduplicateTorrents(baseMedia.torrents)
              }
            }.getOrDefault(deduplicateTorrents(baseMedia.torrents))
          }

          val enrichedTorrents = enrichWithDebridCache(torrentsDeferred.await())
          val finalMedia =
            baseMedia.copy(
              torrents = enrichedTorrents,
              torrentCount = maxOf(baseMedia.torrentCount, enrichedTorrents.size),
            )
          cacheMediaItems(listOf(finalMedia))

          MediaDetailBundle(
            media = finalMedia,
            credits = creditsDeferred.await(),
            watchProviders = watchProvidersDeferred.await(),
            torrents = enrichedTorrents,
          )
        }
      }
    }

  private suspend fun enrichWithDebridCache(torrents: List<TorrentRelease>): List<TorrentRelease> {
    val activeDebrid = authRepository.debridAccounts.value.firstOrNull { it.isConnected } ?: return torrents
    val token = authRepository.getDebridToken(activeDebrid.provider) ?: return torrents
    return coroutineScope {
      torrents.take(15).mapIndexed { index, release ->
        async {
          val isCached =
            debridService.checkInstantCache(activeDebrid.provider, token, release.infoHash)
          if (isCached) release.copy(debridCached = true, debridProvider = activeDebrid.provider)
          else release
        }
      }.map { it.await() } + torrents.drop(15)
    }
  }

  suspend fun submitTrueSpecScan(infoHash: String): Result<TrueSpecReport?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val payload = """{"info_hash":"${infoHash.lowercase(Locale.US)}"}"""
        val response = api.submitTrueSpecScan(payload.toRequestBody(jsonMediaType))
        if (!response.isSuccessful) {
          throw IllegalStateException("TrueSpec scan request returned HTTP ${response.code()}")
        }
        TorrentClawJsonParser.parseTrueSpecScanResponse(response.body()?.string().orEmpty())
      }
    }

  suspend fun getTrueSpecScanStatus(infoHash: String): Result<TrueSpecReport?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = api.getTrueSpecScanStatus(infoHash.lowercase(Locale.US))
        if (!response.isSuccessful) {
          throw IllegalStateException("TrueSpec status returned HTTP ${response.code()}")
        }
        TorrentClawJsonParser.parseTrueSpecScanResponse(response.body()?.string().orEmpty())
      }
    }

  suspend fun downloadTorrentFile(infoHash: String, releaseName: String): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val response = api.downloadTorrentFile(infoHash.lowercase(Locale.US))
        if (!response.isSuccessful) {
          throw IllegalStateException("Unable to download .torrent file (HTTP ${response.code()})")
        }
        val bytes = response.body()?.bytes() ?: throw IllegalStateException("Empty .torrent response")
        val dir = File(context.cacheDir, "torrents").apply { mkdirs() }
        val safeName = releaseName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val outFile = File(dir, "${safeName}_${infoHash.take(8)}.torrent")
        outFile.writeBytes(bytes)
        outFile
      }
    }

  suspend fun trackInteraction(infoHash: String, action: String) {
    withContext(Dispatchers.IO) {
      runCatching {
        val payload = """{"infoHash":"${infoHash.lowercase(Locale.US)}","action":"$action"}"""
        api.trackInteraction(payload.toRequestBody(jsonMediaType))
      }
    }
  }
}
