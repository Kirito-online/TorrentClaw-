package com.example.data.repository

import android.content.Context
import com.example.data.model.CastMember
import com.example.data.model.CatalogStats
import com.example.data.model.CollectionSummary
import com.example.data.model.DiscoverySnapshot
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SearchFilter
import com.example.data.model.TorrentRelease
import com.example.data.model.WatchProvider
import com.example.data.model.deduplicateTorrents
import com.example.data.model.extractSeasonEpisodeFromName
import com.example.data.model.matchesTargetMediaItem
import com.example.data.model.matchesTargetMediaRelease
import com.example.data.model.parseImdbOrTmdbUrl
import com.example.data.network.TorrentClawHttpException
import com.example.data.network.TorrentClawNetworkService
import com.example.data.remote.ParsedCatalogResponse
import com.example.data.remote.TorrentClawJsonParser
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

data class MediaDetailBundle(
  val media: MediaItem,
  val torrents: List<TorrentRelease>,
  val cast: List<CastMember>,
  val watchProviders: List<WatchProvider>,
  val hasMoreTorrents: Boolean = false,
  val warningError: String? = null,
)

data class ExpandTorrentsResult(
  val torrents: List<TorrentRelease>,
  val hasMoreTorrents: Boolean,
  val warningMessage: String? = null,
)

private data class CachedDetailEntry(
  val bundle: MediaDetailBundle,
  val timestampMs: Long,
)

private data class SubQuerySpec(
  val key: String,
  val params: Map<String, String>,
  val qualityTag: String? = null,
  val seasonTag: Int? = null,
  val episodeTag: Int? = null,
)

private data class MediaExpansionState(
  var baseMedia: MediaItem,
  val activeSearchParams: Map<String, String>,
  val executedQueryKeys: MutableSet<String> = mutableSetOf(),
  val cappedQualities: MutableSet<String> = mutableSetOf(),
  val cappedSeasons: MutableSet<Int> = mutableSetOf(),
  val queriedSeasons: MutableSet<Int> = mutableSetOf(),
  val knownSeasons: MutableSet<Int> = mutableSetOf(),
  val pendingQueue: ArrayDeque<SubQuerySpec> = ArrayDeque(),
)

class TorrentClawRepository(
  private val appContext: Context,
  private val network: TorrentClawNetworkService,
) {

  private var cachedDiscover: DiscoverySnapshot? = null
  private val detailCache = mutableMapOf<String, CachedDetailEntry>()
  private val expansionStates = mutableMapOf<String, MediaExpansionState>()
  private val detailCacheTtlMs = 180_000L

  private val qualityBuckets = listOf("2160p", "1080p", "720p", "480p")

  private val subFilterSlices: List<Map<String, String>> =
    listOf(
      mapOf("codec" to "x265"),
      mapOf("codec" to "x264"),
      mapOf("codec" to "av1"),
      mapOf("source" to "webdl,webrip"),
      mapOf("source" to "bluray,remux"),
      mapOf("size_max_gb" to "2"),
      mapOf("size_min_gb" to "2", "size_max_gb" to "5"),
      mapOf("size_min_gb" to "5", "size_max_gb" to "12"),
      mapOf("size_min_gb" to "12"),
      mapOf("hdr" to "dv,dv+hdr10"),
      mapOf("hdr" to "hdr10,hdr10+"),
      mapOf("audio_codec" to "atmos,truehd"),
      mapOf("audio_codec" to "dts,eac3,aac"),
      mapOf("codec" to "hevc"),
    )

  private val defaultOpenApiGenres =
    listOf(
      "Action",
      "Adventure",
      "Animation",
      "Anime",
      "Comedy",
      "Crime",
      "Documentary",
      "Drama",
      "Family",
      "Fantasy",
      "History",
      "Horror",
      "Music",
      "Mystery",
      "Romance",
      "Science Fiction",
      "Thriller",
      "War",
      "Western",
    )

  fun clearDetailCache() {
    detailCache.clear()
    expansionStates.clear()
  }

  suspend fun loadDiscovery(forceRefresh: Boolean = false): Result<DiscoverySnapshot> =
    coroutineScope {
      if (!forceRefresh && cachedDiscover != null) {
        return@coroutineScope Result.success(cachedDiscover!!)
      }
      val popularAllDef = async { network.fetchPopular(limit = 24) }
      val popMoviesDef = async { network.fetchPopular(type = "movie", limit = 18) }
      val popShowsDef = async { network.fetchPopular(type = "show", limit = 18) }
      val recentDef = async { network.fetchRecent(limit = 24) }
      val collectionsDef = async { network.fetchCollections(limit = 12) }
      val statsDef = async { network.fetchStats() }

      val popularAllRes = popularAllDef.await()
      val popMoviesRes = popMoviesDef.await()
      val popShowsRes = popShowsDef.await()
      val recentRes = recentDef.await()

      val firstFailure =
        listOf(popularAllRes, popMoviesRes, popShowsRes, recentRes)
          .firstOrNull { it.isFailure }
          ?.exceptionOrNull()

      val popularAll =
        popularAllRes.getOrNull()?.let { TorrentClawJsonParser.parseSearchResponse(it).mediaItems }.orEmpty()
      val rawMovies =
        popMoviesRes.getOrNull()?.let { TorrentClawJsonParser.parseSearchResponse(it).mediaItems }.orEmpty()
      val rawShows =
        popShowsRes.getOrNull()?.let { TorrentClawJsonParser.parseSearchResponse(it).mediaItems }.orEmpty()
      val recentParsed =
        recentRes.getOrNull()?.let { TorrentClawJsonParser.parseSearchResponse(it) } ?: ParsedCatalogResponse()
      val collections =
        collectionsDef.await().getOrNull()?.let { TorrentClawJsonParser.parseCollectionsResponse(it) }.orEmpty()
      val stats =
        statsDef.await().getOrNull()?.let { TorrentClawJsonParser.parseStatsResponse(it) } ?: CatalogStats()

      val popMovies =
        rawMovies
          .filter { it.mediaType == MediaType.MOVIE }
          .ifEmpty { popularAll.filter { it.mediaType == MediaType.MOVIE } }
          .ifEmpty { recentParsed.mediaItems.filter { it.mediaType == MediaType.MOVIE } }

      val popShows =
        rawShows
          .filter { it.mediaType == MediaType.SHOW }
          .ifEmpty { popularAll.filter { it.mediaType == MediaType.SHOW } }
          .ifEmpty { recentParsed.mediaItems.filter { it.mediaType == MediaType.SHOW } }

      val trending =
        popularAll
          .ifEmpty { (popMovies + popShows).distinctBy { it.id } }
          .ifEmpty { recentParsed.mediaItems }

      if (trending.isEmpty() && popMovies.isEmpty() && popShows.isEmpty() && recentParsed.mediaItems.isEmpty() && firstFailure != null) {
        return@coroutineScope Result.failure(firstFailure)
      }

      val genres =
        (trending + popMovies + popShows + recentParsed.mediaItems)
          .flatMap { it.genres }
          .distinct()
          .ifEmpty { defaultOpenApiGenres }

      val snapshot =
        DiscoverySnapshot(
          trending = trending,
          popularMovies = popMovies,
          popularShows = popShows,
          recentMedia = recentParsed.mediaItems,
          recentTorrents = recentParsed.torrents,
          genres = genres,
          collections = collections,
          stats = stats,
        )
      cachedDiscover = snapshot
      Result.success(snapshot)
    }

  suspend fun loadPopular(mediaType: MediaType, page: Int): Result<ParsedCatalogResponse> {
    return network.fetchPopular(
      type = mediaType.apiValue,
      page = page,
      limit = 24,
    ).map { raw ->
      val parsed = TorrentClawJsonParser.parseSearchResponse(raw)
      if (mediaType == MediaType.ALL) parsed
      else {
        val filtered = parsed.mediaItems.filter { it.mediaType == mediaType }
        parsed.copy(mediaItems = filtered.ifEmpty { parsed.mediaItems })
      }
    }
  }

  suspend fun loadRecent(mediaType: MediaType, page: Int): Result<ParsedCatalogResponse> {
    return network.fetchRecent(
      type = mediaType.apiValue,
      page = page,
      limit = 24,
    ).map { raw ->
      val parsed = TorrentClawJsonParser.parseSearchResponse(raw)
      if (mediaType == MediaType.ALL) parsed
      else {
        val filtered = parsed.mediaItems.filter { it.mediaType == mediaType }
        parsed.copy(mediaItems = filtered.ifEmpty { parsed.mediaItems })
      }
    }
  }

  suspend fun loadCollections(page: Int): Result<List<CollectionSummary>> {
    return network.fetchCollections(page = page, limit = 48)
      .map { TorrentClawJsonParser.parseCollectionsResponse(it) }
  }

  suspend fun loadCollectionItems(collectionId: String): Result<ParsedCatalogResponse> {
    return network.fetchCollectionDetail(collectionId)
      .map { TorrentClawJsonParser.parseSearchResponse(it) }
  }

  suspend fun autocomplete(query: String): List<MediaItem> {
    if (query.trim().length < 2) return emptyList()
    return network.autocomplete(query = query.trim(), limit = 8)
      .getOrNull()
      ?.let { TorrentClawJsonParser.parseAutocompleteResponse(it) }
      .orEmpty()
  }

  suspend fun search(filter: SearchFilter, page: Int): Result<ParsedCatalogResponse> {
    val trimmed = filter.query.trim()
    val parsedUrl = parseImdbOrTmdbUrl(trimmed)
    val effectiveQuery =
      when {
        parsedUrl?.imdbId != null -> parsedUrl.imdbId
        parsedUrl?.tmdbId != null -> parsedUrl.tmdbId.toString()
        trimmed.isNotBlank() -> trimmed
        else -> null
      }
    val params =
      mutableMapOf<String, String?>(
        "q" to effectiveQuery,
        "type" to (parsedUrl?.mediaType?.apiValue ?: filter.mediaType.apiValue),
        "genre" to filter.genre,
        "year_min" to filter.yearMin?.toString(),
        "year_max" to filter.yearMax?.toString(),
        "min_rating" to filter.minRating?.toString(),
        "quality" to filter.resolution,
        "codec" to filter.codec,
        "hdr" to filter.hdr,
        "sort" to filter.sortBy.apiValue,
        "page" to page.toString(),
        "limit" to "50",
      )
    if (parsedUrl?.imdbId != null) params["imdbid"] = parsedUrl.imdbId
    if (parsedUrl?.tmdbId != null) params["tmdbid"] = parsedUrl.tmdbId.toString()

    return network.search(params).map { TorrentClawJsonParser.parseSearchResponse(it) }
  }

  suspend fun loadMediaDetail(
    mediaId: String,
    initialMedia: MediaItem?,
    season: Int? = null,
    episode: Int? = null,
    forceRefresh: Boolean = false,
  ): Result<MediaDetailBundle> = coroutineScope {
    val cacheKey = "${mediaId}_${season ?: -1}_${episode ?: -1}"
    val now = System.currentTimeMillis()
    if (forceRefresh) {
      detailCache.remove(cacheKey)
      expansionStates.remove(cacheKey)
    } else {
      val cached = detailCache[cacheKey]
      if (cached != null && (now - cached.timestampMs) < detailCacheTtlMs) {
        return@coroutineScope Result.success(cached.bundle)
      }
    }

    var baseItem: MediaItem? = initialMedia
    val collectedTorrents = mutableListOf<TorrentRelease>()
    if (initialMedia != null && initialMedia.torrents.isNotEmpty()) {
      collectedTorrents.addAll(initialMedia.torrents)
    }

    val primaryQuery =
      initialMedia?.title?.takeIf { it.isNotBlank() }
        ?: initialMedia?.imdbId?.takeIf { it.isNotBlank() }
        ?: mediaId

    val exactImdb = initialMedia?.imdbId?.takeIf { it.isNotBlank() }
    val exactTmdb = initialMedia?.tmdbId?.takeIf { it > 0 }
    val hasExactId = exactImdb != null || exactTmdb != null

    val typedSearchParams =
      buildMap {
        if (exactImdb != null) {
          put("imdbid", exactImdb)
        } else if (exactTmdb != null) {
          put("tmdbid", exactTmdb.toString())
        } else {
          put("q", primaryQuery)
          initialMedia?.mediaType?.apiValue?.let { put("type", it) }
        }
        if (initialMedia?.mediaType == MediaType.SHOW) {
          put("include", "seasons")
        }
        season?.let { put("season", it.toString()) }
        episode?.let { put("episode", it.toString()) }
        put("page", "1")
        put("limit", "50")
      }

    var searchError: Throwable? = null
    val executedKeys = mutableSetOf<String>()
    val cappedQualities = mutableSetOf<String>()
    val cappedSeasons = mutableSetOf<Int>()
    val queriedSeasons = mutableSetOf<Int>()
    val knownSeasons = mutableSetOf<Int>()
    season?.let { queriedSeasons.add(it) }

    fun collectFromSearchPage(parsed: ParsedCatalogResponse): Pair<Int, Int> {
      val beforeSize = deduplicateTorrents(collectedTorrents).size
      val currentTarget = baseItem
      val matchedItems =
        if (currentTarget != null) {
          parsed.mediaItems.filter { candidate -> matchesTargetMediaItem(candidate, currentTarget) }
        } else {
          parsed.mediaItems.take(1)
        }

      var rawMatchedCount = 0
      matchedItems.forEach { richMatch ->
        baseItem = mergeMediaMetadata(baseItem, richMatch)
        collectedTorrents.addAll(richMatch.torrents)
        rawMatchedCount += richMatch.torrents.size
      }

      val targetForScope = baseItem
      if (targetForScope != null) {
        val secondaryMatches =
          parsed.mediaItems
            .filterNot { it in matchedItems }
            .flatMap { it.torrents }
            .filter { matchesTargetMediaRelease(it, targetForScope) }
        collectedTorrents.addAll(secondaryMatches)
        rawMatchedCount += secondaryMatches.size

        val flatMatches = parsed.torrents.filter { matchesTargetMediaRelease(it, targetForScope) }
        collectedTorrents.addAll(flatMatches)
        rawMatchedCount += flatMatches.size
      } else {
        collectedTorrents.addAll(parsed.torrents)
        rawMatchedCount += parsed.torrents.size
      }
      val afterSize = deduplicateTorrents(collectedTorrents).size
      return ((afterSize - beforeSize).coerceAtLeast(0) to rawMatchedCount)
    }

    var activeSearchParams = typedSearchParams
    var firstSearchRes = network.search(activeSearchParams)
    if (firstSearchRes.isFailure && !isRateLimitError(firstSearchRes.exceptionOrNull())) {
      activeSearchParams = buildMap {
        put("q", primaryQuery)
        if (initialMedia?.mediaType == MediaType.SHOW) put("include", "seasons")
        season?.let { put("season", it.toString()) }
        episode?.let { put("episode", it.toString()) }
        put("page", "1")
        put("limit", "50")
      }
      firstSearchRes = network.search(activeSearchParams)
    }

    if (firstSearchRes.isFailure) {
      searchError = firstSearchRes.exceptionOrNull()
    } else {
      executedKeys.add(canonicalQueryKey(activeSearchParams))
      var parsed = TorrentClawJsonParser.parseSearchResponse(firstSearchRes.getOrThrow(), baseItem?.title)
      var (added, _) = collectFromSearchPage(parsed)

      // Retry by title query if exact ID lookup or strict params yielded 0 torrents
      if (added == 0 && collectedTorrents.isEmpty() && (hasExactId || activeSearchParams.size > 3)) {
        val bareParams = buildMap {
          put("q", primaryQuery)
          season?.let { put("season", it.toString()) }
          episode?.let { put("episode", it.toString()) }
          put("page", "1")
          put("limit", "50")
        }
        val bareRes = network.search(bareParams)
        if (bareRes.isFailure) {
          if (isRateLimitError(bareRes.exceptionOrNull())) searchError = bareRes.exceptionOrNull()
        } else {
          executedKeys.add(canonicalQueryKey(bareParams))
          val bareParsed = TorrentClawJsonParser.parseSearchResponse(bareRes.getOrThrow(), baseItem?.title)
          val (bareAdded, _) = collectFromSearchPage(bareParsed)
          if (bareAdded > 0 || bareParsed.mediaItems.isNotEmpty() || bareParsed.torrents.isNotEmpty()) {
            activeSearchParams = bareParams
            parsed = bareParsed
            added = bareAdded
          }
        }
      }

      // If title contains ':' and server did not report hasTorrents == false, try without punctuation
      if (searchError == null &&
        collectedTorrents.isEmpty() &&
        baseItem?.hasTorrents != false &&
        primaryQuery.contains(':')
      ) {
        val cleanQuery = primaryQuery.replace(":", " ").replace(Regex("\\s+"), " ").trim()
        if (cleanQuery.isNotBlank() && cleanQuery != primaryQuery) {
          val cleanParams = buildMap {
            put("q", cleanQuery)
            season?.let { put("season", it.toString()) }
            episode?.let { put("episode", it.toString()) }
            put("page", "1")
            put("limit", "50")
          }
          val cleanRes = network.search(cleanParams)
          if (cleanRes.isFailure) {
            if (isRateLimitError(cleanRes.exceptionOrNull())) searchError = cleanRes.exceptionOrNull()
          } else {
            executedKeys.add(canonicalQueryKey(cleanParams))
            val cleanParsed = TorrentClawJsonParser.parseSearchResponse(cleanRes.getOrThrow(), baseItem?.title)
            val (cleanAdded, _) = collectFromSearchPage(cleanParsed)
            if (cleanAdded > 0) {
              activeSearchParams = cleanParams
              parsed = cleanParsed
            }
          }
        }
      }

      // If still empty and server did not say hasTorrents == false, try imdbid if newly discovered
      val imdbLookup = baseItem?.imdbId?.takeIf { it.isNotBlank() && it != exactImdb }
      if (searchError == null &&
        collectedTorrents.isEmpty() &&
        baseItem?.hasTorrents != false &&
        imdbLookup != null
      ) {
        val imdbParams = buildMap {
          put("imdbid", imdbLookup)
          if (baseItem.mediaType == MediaType.SHOW) put("include", "seasons")
          season?.let { put("season", it.toString()) }
          episode?.let { put("episode", it.toString()) }
          put("page", "1")
          put("limit", "50")
        }
        val imdbRes = network.search(imdbParams)
        if (imdbRes.isFailure) {
          if (isRateLimitError(imdbRes.exceptionOrNull())) searchError = imdbRes.exceptionOrNull()
        } else {
          executedKeys.add(canonicalQueryKey(imdbParams))
          val imdbParsed = TorrentClawJsonParser.parseSearchResponse(imdbRes.getOrThrow(), baseItem.title)
          val (imdbAdded, _) = collectFromSearchPage(imdbParsed)
          if (imdbAdded > 0) {
            activeSearchParams = imdbParams
            parsed = imdbParsed
          }
        }
      }

      // Wave 1: TorrentClaw /api/v1/search caps embedded torrents per response (~15 for movies, ~25 for season queries).
      // Fetch all 4 resolution buckets in parallel so 2160p, 1080p, 720p, and 480p all populate immediately.
      if (searchError == null &&
        baseItem != null &&
        baseItem.hasTorrents != false &&
        season == null &&
        episode == null
      ) {
        val wave1Results =
          qualityBuckets
            .map { q ->
              val qParams =
                activeSearchParams.toMutableMap().apply {
                  put("quality", q)
                  put("page", "1")
                  put("limit", "50")
                }
              async { q to qParams to network.search(qParams) }
            }
            .awaitAll()

        wave1Results.forEach { (qPair, res) ->
          val (q, qParams) = qPair
          val body = res.getOrNull() ?: return@forEach
          executedKeys.add(canonicalQueryKey(qParams))
          val qParsed = TorrentClawJsonParser.parseSearchResponse(body, baseItem.title)
          val (_, rawMatchedCount) = collectFromSearchPage(qParsed)
          if (rawMatchedCount >= MOVIE_CAP_THRESHOLD) {
            cappedQualities.add(q)
          }
        }
      }

      // Paginate through remaining pages only when server reports more results
      var page = 2
      var shouldFetchNext = searchError == null && parsed.hasMore
      while (shouldFetchNext && page <= 10 && collectedTorrents.size < 1000) {
        val nextParams = activeSearchParams.toMutableMap().apply { put("page", page.toString()) }
        val nextRes = network.search(nextParams)
        if (nextRes.isFailure) {
          if (isRateLimitError(nextRes.exceptionOrNull())) searchError = nextRes.exceptionOrNull()
          break
        }
        executedKeys.add(canonicalQueryKey(nextParams))
        val nextParsed = TorrentClawJsonParser.parseSearchResponse(nextRes.getOrThrow(), baseItem?.title)
        val (newCount, _) = collectFromSearchPage(nextParsed)
        shouldFetchNext = nextParsed.hasMore && newCount > 0
        page++
      }
    }

    val resolvedMedia =
      baseItem
        ?: return@coroutineScope Result.failure(
          searchError ?: IllegalStateException("Unable to load title details")
        )

    // Discover known TV seasons from metadata + collected releases
    if (resolvedMedia.mediaType == MediaType.SHOW) {
      resolvedMedia.numberOfSeasons?.takeIf { it > 0 }?.let { total ->
        (1..total).forEach { knownSeasons.add(it) }
      }
      collectedTorrents.forEach { rel ->
        val s = rel.seasonNumber ?: extractSeasonEpisodeFromName(rel.releaseName).first
        if (s != null && s > 0) knownSeasons.add(s)
      }
    }

    // Fetch public TorrentClaw web page when any releases lack direct magnet/infoHash
    // so every Magnet button holds a genuine magnet:?xt=urn:btih:... URI.
    val needsMagnetEnrichment =
      resolvedMedia.hasTorrents != false &&
        (collectedTorrents.isEmpty() || collectedTorrents.any { !it.hasDirectMagnet })
    val publicContentPath =
      resolvedMedia.contentUrl?.takeIf { it.isNotBlank() }
        ?: buildFallbackContentPath(resolvedMedia)

    val publicPageDeferred =
      if (needsMagnetEnrichment && publicContentPath != null) {
        async { network.fetchPublicContentPage(publicContentPath) }
      } else {
        null
      }

    val expansionState =
      MediaExpansionState(
        baseMedia = resolvedMedia,
        activeSearchParams = activeSearchParams,
        executedQueryKeys = executedKeys,
        cappedQualities = cappedQualities,
        cappedSeasons = cappedSeasons,
        queriedSeasons = queriedSeasons,
        knownSeasons = knownSeasons,
      )
    rebuildPendingQueue(expansionState)
    expansionStates[cacheKey] = expansionState

    // Wave 2: Run 1 small, paced round-robin batch (3 requests) automatically when capped buckets or TV seasons exist.
    // Non-fatal if rate-limited: remaining queries stay queued for "Load More Releases".
    if (searchError == null &&
      resolvedMedia.hasTorrents != false &&
      season == null &&
      episode == null &&
      expansionState.pendingQueue.isNotEmpty()
    ) {
      executeQueuedBatch(
        state = expansionState,
        collected = collectedTorrents,
        maxQueries = 3,
        chunkSize = 2,
        interChunkDelayMs = 300L,
      )
    }

    publicPageDeferred?.await()?.getOrNull()?.let { html ->
      val pageTorrents =
        TorrentClawJsonParser.parsePublicContentPageTorrents(
          html = html,
          fallbackMediaTitle = resolvedMedia.title,
          parentTitleId = resolvedMedia.id,
          parentTmdbId = resolvedMedia.tmdbId,
          parentImdbId = resolvedMedia.imdbId,
          parentContentUrl = resolvedMedia.contentUrl ?: publicContentPath,
        )
      if (pageTorrents.isNotEmpty()) {
        collectedTorrents.addAll(pageTorrents)
        if (resolvedMedia.mediaType == MediaType.SHOW) {
          pageTorrents.forEach { rel ->
            val s = rel.seasonNumber ?: extractSeasonEpisodeFromName(rel.releaseName).first
            if (s != null && s > 0) expansionState.knownSeasons.add(s)
          }
        }
      }
    }

    val finalMedia = expansionState.baseMedia
    val scopedTorrents = deduplicateTorrents(collectedTorrents)
    val effectiveWarning =
      if (scopedTorrents.isEmpty() && searchError != null) {
        searchError.message ?: "Unable to fetch torrents right now."
      } else {
        null
      }
    val hasMoreTorrents =
      finalMedia.hasTorrents != false && expansionState.pendingQueue.isNotEmpty()

    val bundle =
      MediaDetailBundle(
        media = finalMedia.copy(torrentCount = maxOf(finalMedia.torrentCount, scopedTorrents.size)),
        torrents = scopedTorrents,
        cast = finalMedia.cast,
        watchProviders = finalMedia.watchProviders,
        hasMoreTorrents = hasMoreTorrents,
        warningError = effectiveWarning,
      )
    if (effectiveWarning == null) {
      detailCache[cacheKey] = CachedDetailEntry(bundle = bundle, timestampMs = System.currentTimeMillis())
    }
    Result.success(bundle)
  }

  suspend fun loadMoreMediaTorrents(
    media: MediaItem,
    currentTorrents: List<TorrentRelease>,
    focusQuality: String? = null,
    focusSeason: Int? = null,
    focusEpisode: Int? = null,
    batchLimit: Int = 6,
  ): Result<ExpandTorrentsResult> {
    val baseKey = "${media.id}_-1_-1"
    val scopedKey = "${media.id}_${focusSeason ?: -1}_${focusEpisode ?: -1}"
    val state =
      expansionStates[baseKey]
        ?: expansionStates[scopedKey]
        ?: createFallbackExpansionState(media).also { expansionStates[baseKey] = it }

    // Ensure season/episode specific focus queries are queued at the front when selected by user
    if (focusSeason != null && focusSeason > 0) {
      state.knownSeasons.add(focusSeason)
      enqueuePriorityFocusQueries(
        state = state,
        focusSeason = focusSeason,
        focusEpisode = focusEpisode,
        focusQuality = focusQuality,
      )
    } else if (focusQuality != null) {
      prioritizeQualityInQueue(state, focusQuality)
    }

    if (state.pendingQueue.isEmpty()) {
      return Result.success(
        ExpandTorrentsResult(
          torrents = deduplicateTorrents(currentTorrents),
          hasMoreTorrents = false,
        )
      )
    }

    val collected = currentTorrents.toMutableList()
    val batchWarning =
      executeQueuedBatch(
        state = state,
        collected = collected,
        maxQueries = batchLimit,
        chunkSize = 2,
        interChunkDelayMs = 350L,
      )

    val merged = deduplicateTorrents(collected)
    val hasMore = state.pendingQueue.isNotEmpty()

    val existingCached = detailCache[baseKey]
    if (existingCached != null) {
      val updatedMedia =
        existingCached.bundle.media.copy(
          torrentCount = maxOf(existingCached.bundle.media.torrentCount, merged.size),
        )
      detailCache[baseKey] =
        CachedDetailEntry(
          bundle =
            existingCached.bundle.copy(
              media = updatedMedia,
              torrents = merged,
              hasMoreTorrents = hasMore,
            ),
          timestampMs = System.currentTimeMillis(),
        )
    }

    return Result.success(
      ExpandTorrentsResult(
        torrents = merged,
        hasMoreTorrents = hasMore,
        warningMessage = batchWarning,
      )
    )
  }

  private suspend fun executeQueuedBatch(
    state: MediaExpansionState,
    collected: MutableList<TorrentRelease>,
    maxQueries: Int,
    chunkSize: Int,
    interChunkDelayMs: Long,
  ): String? = coroutineScope {
    val toExecute = mutableListOf<SubQuerySpec>()
    while (toExecute.size < maxQueries && state.pendingQueue.isNotEmpty()) {
      val next = state.pendingQueue.removeFirst()
      if (!state.executedQueryKeys.contains(next.key)) {
        toExecute.add(next)
      }
    }
    if (toExecute.isEmpty()) return@coroutineScope null

    var rateLimitWarning: String? = null
    val chunks = toExecute.chunked(chunkSize.coerceAtLeast(1))
    var rebuildNeeded = false

    for ((chunkIdx, chunk) in chunks.withIndex()) {
      if (chunkIdx > 0 && interChunkDelayMs > 0) {
        delay(interChunkDelayMs)
      }
      val results = chunk.map { spec -> async { spec to network.search(spec.params) } }.awaitAll()
      val hitRateLimitInChunk = results.any { (_, res) -> isRateLimitError(res.exceptionOrNull()) }

      val requeueFromThisAndLater = mutableListOf<SubQuerySpec>()
      results.forEach { (spec, res) ->
        if (res.isSuccess) {
          state.executedQueryKeys.add(spec.key)
          spec.seasonTag?.let { state.queriedSeasons.add(it) }
          val parsed = TorrentClawJsonParser.parseSearchResponse(res.getOrThrow(), state.baseMedia.title)
          val rawCount = mergeParsedIntoCollection(state, parsed, collected)
          if (spec.seasonTag != null && spec.qualityTag == null && spec.episodeTag == null && rawCount >= SEASON_CAP_THRESHOLD) {
            if (state.cappedSeasons.add(spec.seasonTag)) {
              rebuildNeeded = true
            }
          }
          if (spec.seasonTag == null && spec.qualityTag != null && spec.params.size == state.activeSearchParams.size + 1 && rawCount >= MOVIE_CAP_THRESHOLD) {
            if (state.cappedQualities.add(spec.qualityTag)) {
              rebuildNeeded = true
            }
          }
        } else if (isRateLimitError(res.exceptionOrNull())) {
          requeueFromThisAndLater.add(spec)
        } else {
          // Non-429 error (e.g., 400/404 for unsupported filter combo): mark executed so we don't loop forever
          state.executedQueryKeys.add(spec.key)
        }
      }

      if (hitRateLimitInChunk) {
        rateLimitWarning = "Rate limit reached briefly. Wait a moment and tap Load More again."
        for (laterIdx in (chunkIdx + 1) until chunks.size) {
          requeueFromThisAndLater.addAll(chunks[laterIdx])
        }
        requeueFromThisAndLater.asReversed().forEach { state.pendingQueue.addFirst(it) }
        break
      }
    }

    if (rebuildNeeded) {
      rebuildPendingQueue(state)
    }
    rateLimitWarning
  }

  private fun mergeParsedIntoCollection(
    state: MediaExpansionState,
    parsed: ParsedCatalogResponse,
    collected: MutableList<TorrentRelease>,
  ): Int {
    val currentTarget = state.baseMedia
    val matchedItems = parsed.mediaItems.filter { candidate -> matchesTargetMediaItem(candidate, currentTarget) }
    var rawMatchedCount = 0

    matchedItems.forEach { richMatch ->
      state.baseMedia = mergeMediaMetadata(state.baseMedia, richMatch)
      collected.addAll(richMatch.torrents)
      rawMatchedCount += richMatch.torrents.size
    }

    val updatedTarget = state.baseMedia
    val secondaryMatches =
      parsed.mediaItems
        .filterNot { it in matchedItems }
        .flatMap { it.torrents }
        .filter { matchesTargetMediaRelease(it, updatedTarget) }
    collected.addAll(secondaryMatches)
    rawMatchedCount += secondaryMatches.size

    val flatMatches = parsed.torrents.filter { matchesTargetMediaRelease(it, updatedTarget) }
    collected.addAll(flatMatches)
    rawMatchedCount += flatMatches.size

    if (updatedTarget.mediaType == MediaType.SHOW) {
      updatedTarget.numberOfSeasons?.takeIf { it > 0 }?.let { total ->
        (1..total).forEach { state.knownSeasons.add(it) }
      }
      collected.forEach { rel ->
        val s = rel.seasonNumber ?: extractSeasonEpisodeFromName(rel.releaseName).first
        if (s != null && s > 0) state.knownSeasons.add(s)
      }
    }

    return rawMatchedCount
  }

  private fun rebuildPendingQueue(state: MediaExpansionState) {
    val existingKeys = mutableSetOf<String>()
    val rebuilt = mutableListOf<SubQuerySpec>()

    fun tryAdd(spec: SubQuerySpec) {
      if (!state.executedQueryKeys.contains(spec.key) && existingKeys.add(spec.key)) {
        rebuilt.add(spec)
      }
    }

    // 1. Unqueried TV seasons (each season returns up to 25 dedicated releases)
    if (state.baseMedia.mediaType == MediaType.SHOW && state.knownSeasons.isNotEmpty()) {
      state.knownSeasons
        .filter { it !in state.queriedSeasons }
        .sortedDescending()
        .forEach { s ->
          val params =
            state.activeSearchParams.toMutableMap().apply {
              remove("quality")
              put("season", s.toString())
              put("page", "1")
              put("limit", "50")
            }
          tryAdd(
            SubQuerySpec(
              key = canonicalQueryKey(params),
              params = params,
              seasonTag = s,
            )
          )
        }
    }

    // 2. Capped TV seasons expanded across resolution buckets
    if (state.cappedSeasons.isNotEmpty()) {
      for (q in qualityBuckets) {
        for (s in state.cappedSeasons.sortedDescending()) {
          val params =
            state.activeSearchParams.toMutableMap().apply {
              put("season", s.toString())
              put("quality", q)
              put("page", "1")
              put("limit", "50")
            }
          tryAdd(
            SubQuerySpec(
              key = canonicalQueryKey(params),
              params = params,
              qualityTag = q,
              seasonTag = s,
            )
          )
        }
      }
    }

    // 3. Round-robin sub-buckets across all capped qualities so 2160p/1080p/720p/480p expand evenly
    val orderedCappedQualities = qualityBuckets.filter { it in state.cappedQualities }
    if (orderedCappedQualities.isNotEmpty()) {
      for (slice in subFilterSlices) {
        for (q in orderedCappedQualities) {
          val params =
            state.activeSearchParams.toMutableMap().apply {
              remove("season")
              remove("episode")
              put("quality", q)
              put("page", "1")
              put("limit", "50")
              putAll(slice)
            }
          tryAdd(
            SubQuerySpec(
              key = canonicalQueryKey(params),
              params = params,
              qualityTag = q,
            )
          )
        }
      }
    }

    // Preserve any ad-hoc focus queries that were prepended earlier
    val adHocFocus =
      state.pendingQueue.filter {
        !state.executedQueryKeys.contains(it.key) && existingKeys.add(it.key)
      }
    state.pendingQueue.clear()
    rebuilt.forEach { state.pendingQueue.addLast(it) }
    adHocFocus.asReversed().forEach { state.pendingQueue.addFirst(it) }
  }

  private fun enqueuePriorityFocusQueries(
    state: MediaExpansionState,
    focusSeason: Int,
    focusEpisode: Int?,
    focusQuality: String?,
  ) {
    val prioritized = mutableListOf<SubQuerySpec>()

    if (focusEpisode != null && focusEpisode > 0) {
      val epParams =
        state.activeSearchParams.toMutableMap().apply {
          remove("quality")
          put("season", focusSeason.toString())
          put("episode", focusEpisode.toString())
          put("page", "1")
          put("limit", "50")
        }
      val epKey = canonicalQueryKey(epParams)
      if (!state.executedQueryKeys.contains(epKey)) {
        prioritized.add(
          SubQuerySpec(
            key = epKey,
            params = epParams,
            seasonTag = focusSeason,
            episodeTag = focusEpisode,
          )
        )
      }
    }

    if (focusSeason !in state.queriedSeasons) {
      val sParams =
        state.activeSearchParams.toMutableMap().apply {
          remove("quality")
          put("season", focusSeason.toString())
          put("page", "1")
          put("limit", "50")
        }
      val sKey = canonicalQueryKey(sParams)
      if (!state.executedQueryKeys.contains(sKey)) {
        prioritized.add(
          SubQuerySpec(
            key = sKey,
            params = sParams,
            seasonTag = focusSeason,
          )
        )
      }
    }

    val qualitiesOrder =
      if (focusQuality != null) {
        listOf(focusQuality) + qualityBuckets.filter { it != focusQuality }
      } else {
        qualityBuckets
      }

    for (q in qualitiesOrder) {
      val sqParams =
        state.activeSearchParams.toMutableMap().apply {
          put("season", focusSeason.toString())
          put("quality", q)
          put("page", "1")
          put("limit", "50")
        }
      val sqKey = canonicalQueryKey(sqParams)
      if (!state.executedQueryKeys.contains(sqKey)) {
        prioritized.add(
          SubQuerySpec(
            key = sqKey,
            params = sqParams,
            qualityTag = q,
            seasonTag = focusSeason,
          )
        )
      }
    }

    val prioritizedKeys = prioritized.map { it.key }.toSet()
    val remaining = state.pendingQueue.filterNot { it.key in prioritizedKeys }
    state.pendingQueue.clear()
    prioritized.forEach { state.pendingQueue.addLast(it) }
    remaining.forEach { state.pendingQueue.addLast(it) }
  }

  private fun prioritizeQualityInQueue(state: MediaExpansionState, focusQuality: String) {
    val matching = mutableListOf<SubQuerySpec>()
    val others = mutableListOf<SubQuerySpec>()
    state.pendingQueue.forEach { spec ->
      if (spec.qualityTag == focusQuality) matching.add(spec) else others.add(spec)
    }
    state.pendingQueue.clear()
    matching.forEach { state.pendingQueue.addLast(it) }
    others.forEach { state.pendingQueue.addLast(it) }
  }

  private fun createFallbackExpansionState(media: MediaItem): MediaExpansionState {
    val baseParams =
      buildMap {
        val imdb = media.imdbId?.takeIf { it.isNotBlank() }
        val tmdb = media.tmdbId?.takeIf { it > 0 }
        if (imdb != null) {
          put("imdbid", imdb)
        } else if (tmdb != null) {
          put("tmdbid", tmdb.toString())
        } else {
          put("q", media.title.ifBlank { media.id })
          media.mediaType.apiValue?.let { put("type", it) }
        }
        if (media.mediaType == MediaType.SHOW) {
          put("include", "seasons")
        }
        put("page", "1")
        put("limit", "50")
      }
    val seasons =
      buildSet {
        media.numberOfSeasons?.takeIf { it > 0 }?.let { total -> (1..total).forEach { add(it) } }
      }.toMutableSet()
    return MediaExpansionState(
      baseMedia = media,
      activeSearchParams = baseParams,
      cappedQualities = qualityBuckets.toMutableSet(),
      knownSeasons = seasons,
    ).also { rebuildPendingQueue(it) }
  }

  private fun isRateLimitError(err: Throwable?): Boolean =
    (err as? TorrentClawHttpException)?.statusCode == 429

  private fun canonicalQueryKey(params: Map<String, String>): String =
    params.entries
      .filter { it.key != "include" }
      .sortedBy { it.key }
      .joinToString("&") { "${it.key}=${it.value}" }

  private fun mergeMediaMetadata(existing: MediaItem?, fresh: MediaItem): MediaItem {
    if (existing == null) return fresh
    return existing.copy(
      id = fresh.id.ifBlank { existing.id },
      tmdbId = fresh.tmdbId ?: existing.tmdbId,
      imdbId = fresh.imdbId ?: existing.imdbId,
      title = fresh.title.ifBlank { existing.title },
      originalTitle = fresh.originalTitle ?: existing.originalTitle,
      year = fresh.year ?: existing.year,
      rating = fresh.rating ?: existing.rating,
      imdbRating = fresh.imdbRating ?: existing.imdbRating,
      voteCount = fresh.voteCount ?: existing.voteCount,
      runtimeMinutes = fresh.runtimeMinutes ?: existing.runtimeMinutes,
      releaseDate = fresh.releaseDate ?: existing.releaseDate,
      tagline = fresh.tagline ?: existing.tagline,
      status = fresh.status ?: existing.status,
      originalLanguage = fresh.originalLanguage ?: existing.originalLanguage,
      certification = fresh.certification ?: existing.certification,
      numberOfSeasons = fresh.numberOfSeasons ?: existing.numberOfSeasons,
      numberOfEpisodes = fresh.numberOfEpisodes ?: existing.numberOfEpisodes,
      genres = fresh.genres.ifEmpty { existing.genres },
      posterUrl = fresh.posterUrl ?: existing.posterUrl,
      backdropUrl = fresh.backdropUrl ?: existing.backdropUrl,
      overview = fresh.overview.ifBlank { existing.overview },
      torrentCount = maxOf(fresh.torrentCount, existing.torrentCount),
      hasTorrents = fresh.hasTorrents ?: existing.hasTorrents,
      contentUrl = fresh.contentUrl ?: existing.contentUrl,
      collectionName = fresh.collectionName ?: existing.collectionName,
      cast = fresh.cast.ifEmpty { existing.cast },
      watchProviders = fresh.watchProviders.ifEmpty { existing.watchProviders },
    )
  }

  suspend fun resolveMagnetForRelease(
    release: TorrentRelease,
    fallbackContentUrl: String? = null,
  ): TorrentRelease {
    if (release.hasDirectMagnet && release.resolvedMagnetUrl.isNotBlank()) {
      return release
    }
    var targetPath =
      release.contentUrl?.takeIf { it.isNotBlank() }
        ?: fallbackContentUrl?.takeIf { it.isNotBlank() }

    if (targetPath == null) {
      val searchParams =
        buildMap {
          release.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdbid", it) }
            ?: release.tmdbId?.takeIf { it > 0 }?.let { put("tmdbid", it.toString()) }
            ?: put("q", release.mediaTitle?.takeIf { it.isNotBlank() } ?: release.releaseName)
          put("limit", "3")
        }
      val searchRaw = network.search(searchParams).getOrNull()
      if (searchRaw != null) {
        val parsed = TorrentClawJsonParser.parseSearchResponse(searchRaw, release.mediaTitle)
        targetPath =
          parsed.mediaItems.firstOrNull { item ->
            release.titleId == item.id ||
              (release.tmdbId != null && release.tmdbId == item.tmdbId) ||
              (!release.imdbId.isNullOrBlank() && release.imdbId.equals(item.imdbId, true)) ||
              !item.contentUrl.isNullOrBlank()
          }?.contentUrl
      }
    }

    if (targetPath.isNullOrBlank()) return release
    val html = network.fetchPublicContentPage(targetPath).getOrNull() ?: return release
    val pageTorrents =
      TorrentClawJsonParser.parsePublicContentPageTorrents(
        html = html,
        fallbackMediaTitle = release.mediaTitle,
        parentTitleId = release.titleId,
        parentTmdbId = release.tmdbId,
        parentImdbId = release.imdbId,
        parentContentUrl = targetPath,
      )
    if (pageTorrents.isEmpty()) return release

    val targetName = release.releaseName.trim().lowercase()
    val match =
      pageTorrents.firstOrNull { cand ->
        cand.hasDirectMagnet &&
          cand.releaseName.trim().lowercase() == targetName &&
          (release.sizeBytes <= 0L || cand.sizeBytes == release.sizeBytes) &&
          (release.trackerSource.isBlank() || cand.trackerSource.equals(release.trackerSource, true))
      }
        ?: pageTorrents.firstOrNull { cand ->
          cand.hasDirectMagnet &&
            cand.releaseName.trim().lowercase() == targetName &&
            (release.sizeBytes <= 0L || cand.sizeBytes == release.sizeBytes)
        }
        ?: pageTorrents.firstOrNull { cand ->
          cand.hasDirectMagnet && cand.releaseName.trim().lowercase() == targetName
        }

    return if (match != null) {
      deduplicateTorrents(listOf(release, match)).firstOrNull() ?: release
    } else {
      release
    }
  }

  private fun buildFallbackContentPath(media: MediaItem): String? {
    val numericId = media.id.trim().takeIf { it.all(Char::isDigit) && it.isNotEmpty() } ?: return null
    val slug =
      media.title
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .takeIf { it.isNotBlank() }
        ?: return null
    val prefix = if (media.mediaType == MediaType.SHOW) "shows" else "movies"
    val yearPart = media.year?.takeIf { it > 1800 }?.let { "-$it" }.orEmpty()
    return "/$prefix/$slug$yearPart-$numericId"
  }

  suspend fun downloadTorrentFile(release: TorrentRelease): Result<File> =
    network.downloadTorrentFile(
      infoHash = release.infoHash,
      directUrl = release.torrentDownloadUrl,
      cacheDir = appContext.cacheDir,
      suggestedName = release.releaseName,
    )

  companion object {
    private const val MOVIE_CAP_THRESHOLD = 15
    private const val SEASON_CAP_THRESHOLD = 20
  }
}
