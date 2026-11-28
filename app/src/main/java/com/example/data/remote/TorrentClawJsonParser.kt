package com.example.data.remote

import com.example.data.model.CastMember
import com.example.data.model.CatalogStats
import com.example.data.model.CollectionSummary
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecStatus
import com.example.data.model.WatchCategory
import com.example.data.model.WatchProvider
import com.example.data.model.deduplicateTorrents
import com.example.data.model.extractSeasonEpisodeFromName
import com.example.data.model.formatAudioChannels
import com.example.data.model.normalizeHdrLabel
import com.example.data.model.normalizeResolutionTag
import com.example.data.model.synthesizeCleanReleaseName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ParsedCatalogResponse(
  val mediaItems: List<MediaItem> = emptyList(),
  val torrents: List<TorrentRelease> = emptyList(),
  val total: Int = 0,
  val page: Int = 1,
  val hasMore: Boolean = false,
)

/**
 * Tolerant kotlinx.serialization JSON parser for TorrentClaw public endpoints.
 * Supports both camelCase (official TorrentClaw API / go-client schema) and snake_case keys,
 * TMDB & relative poster/backdrop paths, HTTPS upgrade, TrueSpec scanStatus,
 * credits, watch providers, collections, and clean release-name resolution.
 */
object TorrentClawJsonParser {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parseSearchResponse(
    rawJson: String,
    fallbackMediaTitle: String? = null,
  ): ParsedCatalogResponse {
    val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() ?: return ParsedCatalogResponse()
    if (root is JsonArray) {
      val mediaItems = mutableListOf<MediaItem>()
      val directTorrents = mutableListOf<TorrentRelease>()
      root.forEach { el ->
        val itemObj = el as? JsonObject ?: return@forEach
        if (isFlatTorrentOnlyObject(itemObj)) {
          parseTorrentObject(itemObj, fallbackMediaTitle)?.let { directTorrents.add(it) }
        } else {
          parseMediaObject(itemObj, fallbackMediaTitle)?.let { mediaItems.add(it) }
        }
      }
      return ParsedCatalogResponse(
        mediaItems = mediaItems,
        torrents = directTorrents,
        total = maxOf(mediaItems.size, directTorrents.size),
      )
    }
    val rawObj = root as? JsonObject ?: return ParsedCatalogResponse()
    val obj =
      if (rawObj.arrayOf("results", "items", "movies", "shows", "torrents", "releases", "matches") == null) {
        rawObj.obj("data") ?: rawObj
      } else {
        rawObj
      }
    val itemsArr =
      obj.arrayOf("results", "items", "movies", "shows", "data", "content", "media", "matches")
    val mediaList = mutableListOf<MediaItem>()
    val directTorrents = mutableListOf<TorrentRelease>()

    itemsArr?.forEach { el ->
      val itemObj = el as? JsonObject ?: return@forEach
      if (isFlatTorrentOnlyObject(itemObj)) {
        parseTorrentObject(itemObj, fallbackMediaTitle)?.let { directTorrents.add(it) }
      } else {
        parseMediaObject(itemObj, fallbackMediaTitle)?.let { mediaList.add(it) }
      }
    }

    obj.arrayOf("torrents", "releases")?.forEach { el ->
      (el as? JsonObject)?.let { parseTorrentObject(it, fallbackMediaTitle) }?.let { directTorrents.add(it) }
    }

    val total = obj.int("total", "totalResults", "total_results", "count")
      ?: mediaList.size.coerceAtLeast(directTorrents.size)
    val page = obj.int("page", "currentPage", "current_page") ?: 1
    val pageSize = obj.int("pageSize", "page_size", "limit", "perPage", "per_page")
    val totalPages = obj.int("totalPages", "total_pages", "pages")
    val hasMore =
      obj.bool("hasMore", "has_more", "hasNext", "has_next")
        ?: when {
          totalPages != null -> page < totalPages
          pageSize != null && pageSize > 0 ->
            (page * pageSize) < total && (mediaList.isNotEmpty() || directTorrents.isNotEmpty())
          else -> (mediaList.size >= 20 || directTorrents.size >= 20)
        }

    return ParsedCatalogResponse(
      mediaItems = mediaList,
      torrents = directTorrents,
      total = total,
      page = page,
      hasMore = hasMore,
    )
  }

  private fun isFlatTorrentOnlyObject(itemObj: JsonObject): Boolean {
    val hasHash = itemObj.str("infoHash", "info_hash", "infohash", "hash", "btih") != null
    val hasMagnet =
      itemObj.str("magnet", "magnetUrl", "magnet_url", "magnetUri", "magnet_uri", "magnetLink", "magnet_link") != null ||
        itemObj.str("url", "link")?.startsWith("magnet:", ignoreCase = true) == true
    val hasTorrentUrl = itemObj.str("torrentUrl", "torrent_url", "downloadUrl", "download_url", "torrentFile", "torrent_file") != null
    val hasNestedTorrents = itemObj.arrayOf("torrents", "releases") != null
    val hasMediaCardFields =
      itemObj.str("overview") != null ||
        itemObj.str("posterUrl", "poster_url", "posterPath", "poster_path") != null ||
        itemObj.obj("content", "media", "movie", "show") != null
    return (hasHash || hasMagnet || hasTorrentUrl) && !hasNestedTorrents && !hasMediaCardFields
  }

  fun parseDetailResponse(rawJson: String, fallbackTitle: String? = null): MediaItem? {
    val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject ?: return null
    val dataObj = root.obj("data") ?: root
    val mediaObj = dataObj.obj("content", "media", "movie", "show", "item", "result") ?: dataObj
    val parsed = parseMediaObject(mediaObj, fallbackTitle) ?: return null
    val outerTorrents =
      (dataObj.arrayOf("torrents", "releases").orEmpty() + root.arrayOf("torrents", "releases").orEmpty())
        .mapNotNull {
          (it as? JsonObject)?.let { tObj ->
            parseTorrentObject(
              obj = tObj,
              fallbackMediaTitle = parsed.title,
              parentTitleId = parsed.id,
              parentTmdbId = parsed.tmdbId,
              parentImdbId = parsed.imdbId,
            )
          }
        }
    val mergedTorrents = deduplicateTorrents(parsed.torrents + outerTorrents)
    return parsed.copy(
      torrents = mergedTorrents,
      torrentCount = maxOf(parsed.torrentCount, mergedTorrents.size),
    )
  }

  /**
   * Parses public HTML pages from TorrentClaw (e.g. https://torrentclaw.to/movies/...)
   * extracting both <a href="magnet:..."> links and Next.js flight "torrents":[...] payloads
   * using linear O(N) string scanners (no backtracking regexes on multi-MB HTML).
   */
  fun parsePublicContentPageTorrents(
    html: String,
    fallbackMediaTitle: String? = null,
    parentTitleId: String? = null,
    parentTmdbId: Int? = null,
    parentImdbId: String? = null,
    parentContentUrl: String? = null,
  ): List<TorrentRelease> {
    if (html.isBlank()) return emptyList()

    val magnetsByHash = extractHtmlMagnetLinksByHash(html)
    val parsed = mutableListOf<TorrentRelease>()

    val flightChunks = extractNextFlightChunks(html)
    for (chunk in flightChunks) {
      var searchFrom = 0
      while (true) {
        val markerIdx = chunk.indexOf("\"torrents\":[", searchFrom)
        if (markerIdx == -1) break
        val arrStart = chunk.indexOf('[', markerIdx)
        if (arrStart == -1) break
        val arrEnd = findBalancedBracketEnd(chunk, arrStart)
        if (arrEnd <= arrStart) {
          searchFrom = arrStart + 1
          continue
        }
        val arrayText = chunk.substring(arrStart, arrEnd)
        val arr = runCatching { json.parseToJsonElement(arrayText) as? JsonArray }.getOrNull()
        arr?.forEach { el ->
          val tObj = el as? JsonObject ?: return@forEach
          val hasHashOrMagnet =
            tObj.str("infoHash", "info_hash", "infohash", "hash", "btih") != null ||
              tObj.str("magnet", "magnetUrl", "magnet_url", "magnetUri", "magnet_uri", "magnetLink", "magnet_link") != null
          val hasTitle =
            tObj.str("rawTitle", "raw_title", "releaseName", "release_name", "title", "name") != null
          if (!hasHashOrMagnet && !hasTitle) return@forEach

          val release =
            parseTorrentObject(
              obj = tObj,
              fallbackMediaTitle = fallbackMediaTitle,
              parentTitleId = parentTitleId,
              parentTmdbId = parentTmdbId,
              parentImdbId = parentImdbId,
              parentContentUrl = parentContentUrl,
            ) ?: return@forEach

          val matchedMagnet =
            if (release.magnetUrl.isNullOrBlank() && release.infoHash.isNotBlank()) {
              magnetsByHash[release.infoHash.lowercase()]
            } else {
              release.magnetUrl
            }
          parsed.add(if (matchedMagnet != null) release.copy(magnetUrl = matchedMagnet) else release)
        }
        searchFrom = arrEnd
      }
    }

    val seenHashes = parsed.mapNotNull { it.infoHash.trim().lowercase().takeIf { h -> h.isNotBlank() } }.toMutableSet()
    for ((hashLower, magnetUri) in magnetsByHash) {
      if (hashLower !in seenHashes) {
        seenHashes.add(hashLower)
        val displayName =
          com.example.data.model.extractMagnetDisplayName(magnetUri)
            ?: fallbackMediaTitle
            ?: hashLower
        val (seasonNum, epNum) = extractSeasonEpisodeFromName(displayName)
        parsed.add(
          TorrentRelease(
            infoHash = hashLower,
            releaseName = displayName,
            hasExplicitReleaseName = displayName != hashLower,
            titleId = parentTitleId,
            mediaTitle = fallbackMediaTitle,
            tmdbId = parentTmdbId,
            imdbId = parentImdbId,
            seasonNumber = seasonNum,
            episodeNumber = epNum,
            contentUrl = parentContentUrl,
            magnetUrl = magnetUri,
          )
        )
      }
    }

    return deduplicateTorrents(parsed)
  }

  private fun extractHtmlMagnetLinksByHash(html: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var idx = 0
    while (true) {
      val pos = html.indexOf("magnet:?xt=urn:btih:", idx, ignoreCase = true)
      if (pos == -1) break
      var end = pos
      while (end < html.length) {
        val ch = html[end]
        if (ch == '"' || ch == '\'' || ch == '<' || ch == '>' || ch.isWhitespace() || ch == '\\') break
        end++
      }
      val raw = html.substring(pos, end)
      val unescaped =
        raw
          .replace("&amp;", "&")
          .replace("&#x26;", "&", ignoreCase = true)
          .replace("&#38;", "&")
      val hash =
        Regex("btih:([a-zA-Z0-9]{32,40})", RegexOption.IGNORE_CASE)
          .find(unescaped)
          ?.groupValues
          ?.get(1)
          ?.lowercase()
      if (!hash.isNullOrBlank() && !result.containsKey(hash)) {
        result[hash] = unescaped
      }
      idx = maxOf(end, pos + 20)
    }
    return result
  }

  private fun extractNextFlightChunks(html: String): List<String> {
    val chunks = mutableListOf<String>()
    val prefix = "self.__next_f.push([1,\""
    var cursor = 0
    while (true) {
      val start = html.indexOf(prefix, cursor)
      if (start == -1) break
      val strStart = start + prefix.length - 1 // include opening quote
      var i = strStart + 1
      var escaped = false
      while (i < html.length) {
        val ch = html[i]
        if (escaped) {
          escaped = false
        } else if (ch == '\\') {
          escaped = true
        } else if (ch == '"') {
          break
        }
        i++
      }
      if (i < html.length && html[i] == '"') {
        val decoded = unescapeJsString(html, strStart + 1, i)
        if (decoded.isNotBlank() && decoded.contains("torrents")) {
          chunks.add(decoded)
        }
        cursor = i + 1
      } else {
        break
      }
    }
    return chunks
  }

  private fun unescapeJsString(src: String, startInclusive: Int, endExclusive: Int): String {
    val sb = StringBuilder(endExclusive - startInclusive)
    var i = startInclusive
    while (i < endExclusive) {
      val ch = src[i]
      if (ch == '\\' && i + 1 < endExclusive) {
        val next = src[i + 1]
        when (next) {
          '"' -> { sb.append('"'); i += 2 }
          '\'' -> { sb.append('\''); i += 2 }
          '\\' -> { sb.append('\\'); i += 2 }
          '/' -> { sb.append('/'); i += 2 }
          'b' -> { sb.append('\b'); i += 2 }
          'f' -> { sb.append('\u000C'); i += 2 }
          'n' -> { sb.append('\n'); i += 2 }
          'r' -> { sb.append('\r'); i += 2 }
          't' -> { sb.append('\t'); i += 2 }
          'u' -> {
            if (i + 5 < endExclusive) {
              val code = src.substring(i + 2, i + 6).toIntOrNull(16)
              if (code != null) {
                sb.append(code.toChar())
                i += 6
                continue
              }
            }
            sb.append('u')
            i += 2
          }
          'x' -> {
            if (i + 3 < endExclusive) {
              val code = src.substring(i + 2, i + 4).toIntOrNull(16)
              if (code != null) {
                sb.append(code.toChar())
                i += 4
                continue
              }
            }
            sb.append('x')
            i += 2
          }
          else -> { sb.append(next); i += 2 }
        }
      } else {
        sb.append(ch)
        i++
      }
    }
    return sb.toString()
  }

  private fun findBalancedBracketEnd(text: String, openBracketIndex: Int): Int {
    if (openBracketIndex !in text.indices || text[openBracketIndex] != '[') return -1
    var depth = 0
    var inStr = false
    var escaped = false
    for (i in openBracketIndex until text.length) {
      val ch = text[i]
      if (escaped) {
        escaped = false
        continue
      }
      if (ch == '\\' && inStr) {
        escaped = true
        continue
      }
      if (ch == '"') {
        inStr = !inStr
        continue
      }
      if (!inStr) {
        if (ch == '[') depth++
        else if (ch == ']') {
          depth--
          if (depth == 0) return i + 1
        }
      }
    }
    return -1
  }

  fun parseAutocompleteResponse(rawJson: String): List<MediaItem> {
    val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() ?: return emptyList()
    val arr =
      when (root) {
        is JsonArray -> root
        is JsonObject -> root.arrayOf("suggestions", "results", "items", "data")
        else -> null
      } ?: return emptyList()
    return arr.mapNotNull { (it as? JsonObject)?.let { o -> parseMediaObject(o) } }
  }

  fun parseGenresResponse(rawJson: String): List<String> {
    val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() ?: return emptyList()
    val arr =
      when (root) {
        is JsonArray -> root
        is JsonObject -> root.arrayOf("genres", "results", "data")
        else -> null
      } ?: return emptyList()
    return arr.mapNotNull { el ->
      when (el) {
        is JsonObject -> el.str("name", "label", "genre")
        else -> runCatching { el.jsonPrimitive.contentOrNull }.getOrNull()
      }?.trim()?.takeIf { it.isNotBlank() }
    }.distinct()
  }

  fun parseCollectionsResponse(rawJson: String): List<CollectionSummary> {
    val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() ?: return emptyList()
    val arr =
      when (root) {
        is JsonArray -> root
        is JsonObject -> root.arrayOf("collections", "results", "items", "data")
        else -> null
      } ?: return emptyList()
    return arr.mapNotNull { el ->
      val o = el as? JsonObject ?: return@mapNotNull null
      val id = o.str("id", "slug", "collectionId", "collection_id", "tmdbId", "tmdb_id") ?: return@mapNotNull null
      val name = o.str("name", "title") ?: return@mapNotNull null
      val singlePoster =
        o.str("posterUrl", "poster_url", "posterPath", "poster_path")?.let { resolveImageUrl(it, "w500") }
      val posters =
        o.arrayOf("posters", "posterPaths", "poster_paths")?.mapNotNull { p ->
          runCatching { p.jsonPrimitive.contentOrNull }.getOrNull()?.let { resolveImageUrl(it, "w500") }
        }.orEmpty()
      val effectivePosters = posters.ifEmpty { listOfNotNull(singlePoster) }
      CollectionSummary(
        id = id,
        name = name,
        description = o.str("description", "overview").orEmpty(),
        itemCount =
          o.int("movieCount", "movie_count", "partCount", "part_count", "itemCount", "item_count", "count", "total")
            ?: o.arrayOf("items", "movies", "parts")?.size
            ?: 0,
        backdropUrl =
          o.str("backdropUrl", "backdrop_url", "backdropPath", "backdrop_path")
            ?.let { resolveImageUrl(it, "w780") },
        posterUrls = effectivePosters,
      )
    }
  }

  fun parseStatsResponse(rawJson: String): CatalogStats {
    val obj = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject ?: return CatalogStats()
    val contentObj = obj.obj("content")
    val torrentsObj = obj.obj("torrents")
    if (contentObj != null || torrentsObj != null) {
      val bySourceCount = torrentsObj?.obj("bySource", "by_source")?.size
      return CatalogStats(
        totalMovies = contentObj?.int("movies", "totalMovies", "total_movies") ?: 0,
        totalShows = contentObj?.int("shows", "totalShows", "total_shows") ?: 0,
        totalTorrents = torrentsObj?.int("total", "totalTorrents", "total_torrents") ?: 0,
        activeSources = bySourceCount ?: obj.int("activeSources", "active_sources") ?: 35,
      )
    }
    val inner = obj.obj("stats", "data") ?: obj
    return CatalogStats(
      totalMovies = inner.int("totalMovies", "total_movies", "movies") ?: 0,
      totalShows = inner.int("totalShows", "total_shows", "shows", "tv_shows") ?: 0,
      totalTorrents = inner.int("totalTorrents", "total_torrents", "torrents") ?: 0,
      activeSources = inner.int("activeSources", "active_sources", "sources") ?: inner.arrayOf("providers")?.size ?: 35,
    )
  }

  fun parseCreditsResponse(rawJson: String): List<CastMember> {
    val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() ?: return emptyList()
    val out = mutableListOf<CastMember>()
    if (root is JsonObject) {
      root.str("director")?.let { dir ->
        out.add(CastMember(id = "director_$dir", name = dir, role = "Director", profileUrl = null))
      }
    }
    val castArr =
      when (root) {
        is JsonArray -> root
        is JsonObject -> root.arrayOf("cast", "credits", "results", "data")
        else -> null
      }
    castArr?.forEach { el ->
      val o = el as? JsonObject ?: return@forEach
      val name = o.str("name", "originalName", "original_name") ?: return@forEach
      val role = o.str("character", "role", "job")
      val id = o.str("id", "tmdbId", "tmdb_id") ?: name
      val profile = o.str("profileUrl", "profile_url", "profilePath", "profile_path")?.let {
        resolveImageUrl(it, "w185")
      }
      out.add(CastMember(id = id, name = name, role = role, profileUrl = profile))
    }
    return out.take(22)
  }

  fun parseWatchProvidersResponse(rawJson: String): List<WatchProvider> {
    val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject ?: return emptyList()
    val providersObj =
      root.obj("streaming", "providers", "watchProviders", "watch_providers", "data")
        ?: root.obj("results")?.let { res -> res.obj("US") ?: res.values.firstOrNull() as? JsonObject }
        ?: root
    return parseWatchProvidersFromObject(providersObj)
  }

  private fun parseWatchProvidersFromObject(providersObj: JsonObject?): List<WatchProvider> {
    if (providersObj == null) return emptyList()
    val out = mutableListOf<WatchProvider>()

    fun collect(arrKey: String, cat: WatchCategory) {
      providersObj.arrayOf(arrKey)?.forEach { el ->
        val o = el as? JsonObject ?: return@forEach
        val name = o.str("name", "providerName", "provider_name") ?: return@forEach
        val id = o.str("providerId", "provider_id", "id") ?: name
        val logo = o.str("logo", "logoUrl", "logo_url", "logoPath", "logo_path")?.let {
          resolveImageUrl(it, "w92")
        }
        out.add(WatchProvider(id, name, logo, cat))
      }
    }

    collect("flatrate", WatchCategory.STREAM)
    collect("stream", WatchCategory.STREAM)
    collect("free", WatchCategory.FREE)
    collect("ads", WatchCategory.FREE)
    collect("rent", WatchCategory.RENT)
    collect("buy", WatchCategory.BUY)
    return out.distinctBy { "${it.providerName}:${it.category}" }
  }

  private fun parseMediaObject(obj: JsonObject, fallbackTitle: String? = null): MediaItem? {
    val contentObj = obj.obj("content", "media", "movie", "show", "item") ?: obj
    val title =
      contentObj.str("title", "name", "originalTitle", "original_title", "titleOriginal", "originalName", "original_name")
        ?: obj.str("title", "name", "mediaTitle", "media_title")
        ?: fallbackTitle
        ?: return null
    val id =
      contentObj.str("id", "contentId", "content_id", "tmdbId", "tmdb_id", "imdbId", "imdb_id", "slug")
        ?: obj.str("id", "contentId", "content_id", "tmdbId", "tmdb_id", "imdbId", "imdb_id", "slug")
        ?: title.lowercase().replace(Regex("[^a-z0-9]+"), "-")
    val tmdbId = (contentObj.int("tmdbId", "tmdb_id") ?: obj.int("tmdbId", "tmdb_id"))?.takeIf { it > 0 }
    val imdbId = contentObj.str("imdbId", "imdb_id") ?: obj.str("imdbId", "imdb_id")
    val typeStr =
      (contentObj.str("contentType", "type", "mediaType", "media_type", "kind")
        ?: obj.str("contentType", "type", "mediaType", "media_type", "kind")).orEmpty().lowercase()
    val mediaType =
      if (typeStr.contains("tv") || typeStr.contains("show") || typeStr.contains("series")) {
        MediaType.SHOW
      } else {
        MediaType.MOVIE
      }
    val rawReleaseDate =
      contentObj.str("releaseDate", "release_date", "firstAirDate", "first_air_date")
        ?: obj.str("releaseDate", "release_date", "firstAirDate", "first_air_date")
    val year =
      (contentObj.int("year", "releaseYear", "release_year")
        ?: obj.int("year", "releaseYear", "release_year")
        ?: rawReleaseDate?.take(4)?.toIntOrNull())
        ?.takeIf { it > 1800 }
    val rating =
      (contentObj.double("rating", "voteAverage", "vote_average", "tmdbRating", "tmdb_rating", "ratingTmdb", "ratingImdb")
        ?: obj.double("rating", "voteAverage", "vote_average", "tmdbRating", "tmdb_rating", "ratingTmdb", "ratingImdb"))
        ?.takeIf { it > 0.0 }
    val imdbRating =
      (contentObj.double("ratingImdb", "rating_imdb", "imdbRating", "imdb_rating")
        ?: obj.double("ratingImdb", "rating_imdb", "imdbRating", "imdb_rating"))
        ?.takeIf { it > 0.0 }
    val genres =
      (contentObj.arrayOf("genres") ?: obj.arrayOf("genres"))?.mapNotNull { g ->
        when (g) {
          is JsonObject -> g.str("name", "label")
          else -> runCatching { g.jsonPrimitive.contentOrNull }.getOrNull()
        }
      }.orEmpty()

    val tmdbNested = contentObj.obj("tmdb", "metadata", "images") ?: obj.obj("tmdb", "metadata", "images")
    val posterRaw =
      contentObj.str("posterUrl", "poster_url", "posterPath", "poster_path", "poster", "cover", "image")
        ?: obj.str("posterUrl", "poster_url", "posterPath", "poster_path", "poster", "cover", "image")
        ?: tmdbNested?.str("posterUrl", "poster_url", "posterPath", "poster_path", "poster")
    val backdropRaw =
      contentObj.str("backdropUrl", "backdrop_url", "backdropPath", "backdrop_path", "backdrop", "banner")
        ?: obj.str("backdropUrl", "backdrop_url", "backdropPath", "backdrop_path", "backdrop", "banner")
        ?: tmdbNested?.str("backdropUrl", "backdrop_url", "backdropPath", "backdrop_path", "backdrop")

    val poster = posterRaw?.let { resolveImageUrl(it, "w500") }
    val backdrop = backdropRaw?.let { resolveImageUrl(it, "w780") }

    val contentUrl =
      contentObj.str("contentUrl", "content_url") ?: obj.str("contentUrl", "content_url")
    val watchProviders =
      parseWatchProvidersFromObject(
        contentObj.obj("streaming", "providers", "watchProviders", "watch_providers")
          ?: obj.obj("streaming", "providers", "watchProviders", "watch_providers")
      )
    val seasonsArr = contentObj.arrayOf("seasons") ?: obj.arrayOf("seasons")
    val derivedSeasonsCount =
      seasonsArr
        ?.mapNotNull { (it as? JsonObject)?.int("season", "seasonNumber", "season_number") }
        ?.count { it > 0 }
        ?.takeIf { it > 0 }
    val derivedEpisodesCount =
      seasonsArr
        ?.sumOf { el ->
          val so = el as? JsonObject ?: return@sumOf 0
          val sNum = so.int("season", "seasonNumber", "season_number") ?: 0
          if (sNum > 0) (so.int("episodeCount", "episode_count") ?: 0) else 0
        }
        ?.takeIf { it > 0 }

    val rawTorrentArray = obj.arrayOf("torrents", "releases") ?: contentObj.arrayOf("torrents", "releases")
    val nestedTorrents =
      rawTorrentArray?.mapNotNull {
        (it as? JsonObject)?.let { tObj ->
          parseTorrentObject(
            obj = tObj,
            fallbackMediaTitle = title,
            parentTitleId = id,
            parentTmdbId = tmdbId,
            parentImdbId = imdbId,
            parentContentUrl = contentUrl,
          )
        }
      }.orEmpty()
    val torrents =
      if (nestedTorrents.isNotEmpty()) {
        nestedTorrents
      } else if (rawTorrentArray == null && contentObj === obj && hasTorrentSpecificFields(obj)) {
        listOfNotNull(
          parseTorrentObject(
            obj = obj,
            fallbackMediaTitle = title,
            parentTitleId = id,
            parentTmdbId = tmdbId,
            parentImdbId = imdbId,
            parentContentUrl = contentUrl,
          )
        )
      } else {
        emptyList()
      }
    val count =
      contentObj.int("torrentCount", "torrent_count", "releaseCount", "release_count", "movieCount")
        ?: obj.int("torrentCount", "torrent_count", "releaseCount", "release_count", "movieCount")
        ?: torrents.size

    return MediaItem(
      id = id,
      tmdbId = tmdbId,
      imdbId = imdbId,
      title = title,
      originalTitle = contentObj.str("titleOriginal", "originalTitle", "original_title", "originalName", "original_name"),
      year = year,
      mediaType = mediaType,
      rating = rating,
      imdbRating = imdbRating,
      voteCount = (contentObj.int("voteCount", "vote_count") ?: obj.int("voteCount", "vote_count"))?.takeIf { it > 0 },
      runtimeMinutes = (contentObj.int("runtime", "runtimeMinutes", "runtime_minutes") ?: obj.int("runtime", "runtimeMinutes", "runtime_minutes"))?.takeIf { it > 0 },
      releaseDate = rawReleaseDate,
      tagline = contentObj.str("tagline") ?: obj.str("tagline"),
      status = contentObj.str("status") ?: obj.str("status"),
      originalLanguage = contentObj.str("originalLanguage", "original_language", "language") ?: obj.str("originalLanguage", "original_language"),
      certification = contentObj.str("certification", "mpaa", "contentRating", "content_rating"),
      numberOfSeasons = (contentObj.int("numberOfSeasons", "number_of_seasons", "seasonsCount", "seasons_count") ?: obj.int("numberOfSeasons", "number_of_seasons"))?.takeIf { it > 0 } ?: derivedSeasonsCount,
      numberOfEpisodes = (contentObj.int("numberOfEpisodes", "number_of_episodes", "episodesCount", "episodes_count") ?: obj.int("numberOfEpisodes", "number_of_episodes"))?.takeIf { it > 0 } ?: derivedEpisodesCount,
      genres = genres,
      posterUrl = poster,
      backdropUrl = backdrop,
      overview = (contentObj.str("overview", "description", "synopsis") ?: obj.str("overview", "description", "synopsis")).orEmpty(),
      torrentCount = count,
      hasTorrents = contentObj.bool("hasTorrents", "has_torrents") ?: obj.bool("hasTorrents", "has_torrents"),
      torrents = torrents,
      contentUrl = contentUrl,
      collectionName = contentObj.str("collectionName", "collection_name") ?: obj.str("collectionName", "collection_name"),
      watchProviders = watchProviders,
    )
  }

  private fun parseTorrentObject(
    obj: JsonObject,
    fallbackMediaTitle: String?,
    parentTitleId: String? = null,
    parentTmdbId: Int? = null,
    parentImdbId: String? = null,
    parentContentUrl: String? = null,
  ): TorrentRelease? {
    val hash = obj.str("infoHash", "info_hash", "infohash", "hash", "btih").orEmpty().trim()
    val magnet =
      obj.str("magnet", "magnetUrl", "magnet_url", "magnetUri", "magnet_uri", "magnetLink", "magnet_link")
        ?: obj.str("url", "link")?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
    val torrentDownloadUrl = obj.str("torrentUrl", "torrent_url", "downloadUrl", "download_url", "torrentFile", "torrent_file")
    val resolvedHash =
      hash
        .ifBlank {
          Regex("btih:([a-zA-Z0-9]{32,40})", RegexOption.IGNORE_CASE)
            .find(magnet.orEmpty())
            ?.groupValues?.get(1).orEmpty()
        }
        .ifBlank {
          Regex("([a-fA-F0-9]{40})")
            .find(torrentDownloadUrl.orEmpty())
            ?.groupValues?.get(1).orEmpty()
        }

    val rawCandidates =
      listOf(
        obj.str("rawTitle", "raw_title"),
        obj.str("releaseName", "release_name"),
        obj.str("rawName", "raw_name"),
        obj.str("filename", "fileName", "file_name"),
        obj.str("title"),
        obj.str("name"),
      )
    val hasExplicitReleaseMetadata = parentTitleId != null && hasTorrentSpecificFields(obj)

    if (resolvedHash.isBlank() && magnet.isNullOrBlank() && torrentDownloadUrl.isNullOrBlank() && !hasExplicitReleaseMetadata) {
      return null
    }

    val trueSpecObj = obj.obj("truespec", "trueSpec", "true_spec")
    val videoInfoObj = obj.obj("videoInfo", "video_info")
    val firstAudioTrack = obj.arrayOf("audioTracks", "audio_tracks")?.firstOrNull() as? JsonObject

    val res =
      normalizeResolutionTag(
        trueSpecObj?.str("resolution")
          ?: videoInfoObj?.str("resolution")
          ?: obj.str("quality", "resolution").orEmpty()
      )
    val sourceType = obj.str("sourceType", "source_type", "sourceFormat", "source_format", "ripType", "rip_type").orEmpty()
    val videoCodec =
      trueSpecObj?.str("videoCodec", "video_codec")
        ?: videoInfoObj?.str("codec", "videoCodec")
        ?: obj.str("codec", "videoCodec", "video_codec").orEmpty()
    val rawAudio =
      trueSpecObj?.str("audioCodec", "audio_codec")
        ?: obj.str("audioCodec", "audio_codec", "audio")
        ?: firstAudioTrack?.str("codec", "audioCodec")
        ?: ""
    val audioChannelsFromExplicit =
      obj.str("audioChannels", "audio_channels")?.takeIf { it.toIntOrNull() == null }
        ?: formatAudioChannels(
          obj.int("audioChannels", "audio_channels")
            ?: firstAudioTrack?.int("channels")
        )
    val channelMatch = Regex("\\b(7\\.1|5\\.1|2\\.0|1\\.0)\\b").find(rawAudio)
    val audioChannels =
      audioChannelsFromExplicit.ifBlank { channelMatch?.groupValues?.get(1).orEmpty() }
    val strippedAudio =
      if (channelMatch != null) {
        rawAudio.replace(channelMatch.value, "").replace(Regex("\\s+"), " ").trim()
      } else {
        rawAudio
      }
    val audioCodec =
      strippedAudio.ifBlank {
        firstAudioTrack?.str("codec", "audioCodec").orEmpty()
      }

    val hdr =
      normalizeHdrLabel(
        obj.str("hdrType", "hdrFormat", "hdr_format", "hdr")
          ?: trueSpecObj?.str("hdr", "hdrType", "hdrFormat")
          ?: videoInfoObj?.str("hdr", "hdrType")
      )
    val trackerSource = obj.str("source", "trackerSource", "tracker_source", "tracker", "provider", "indexer").orEmpty()

    val (cleanName, isExplicit) =
      synthesizeCleanReleaseName(
        rawCandidates = rawCandidates,
        infoHash = resolvedHash,
        magnetUri = magnet,
        fallbackTitle = obj.str("mediaTitle", "media_title") ?: fallbackMediaTitle,
        resolution = res,
        sourceType = sourceType,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        audioChannels = audioChannels,
        hdrFormat = hdr,
      )

    val tsStatusStr =
      (trueSpecObj?.str("status") ?: obj.str("scanStatus", "scan_status", "trueSpecStatus", "truespec_status")).orEmpty().lowercase()
    val hasTrueSpecBlock = trueSpecObj != null
    val tsStatus =
      when {
        obj.bool("verified", "isVerified", "is_verified", "trueSpecVerified") == true ||
          tsStatusStr == "verified" || tsStatusStr == "passed" || tsStatusStr == "clean" ->
          TrueSpecStatus.VERIFIED
        tsStatusStr == "failed" || tsStatusStr == "mismatch" -> TrueSpecStatus.FAILED
        tsStatusStr == "unverified" || tsStatusStr == "pending" -> TrueSpecStatus.UNVERIFIED
        hasTrueSpecBlock -> TrueSpecStatus.VERIFIED
        else -> TrueSpecStatus.UNKNOWN
      }

    val (parsedSeason, parsedEp) = extractSeasonEpisodeFromName(cleanName)
    val parsedLanguage =
      obj.str("language", "lang")
        ?: obj.arrayOf("languages")?.mapNotNull {
          runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()?.trim()?.takeIf { s -> s.isNotBlank() }
        }?.take(2)?.joinToString("/")?.takeIf { it.isNotBlank() }
    val parsedGroup =
      obj.str("releaseGroup", "release_group", "group")
        ?: Regex("-([A-Za-z0-9]{2,14})(?:\\.[a-z0-9]{2,4})?$").find(cleanName)?.groupValues?.get(1)
    val parsedFileCount =
      obj.int("fileCount", "filesCount", "file_count", "files_count")?.takeIf { it > 0 }
        ?: obj.arrayOf("files")?.size?.takeIf { it > 0 }
    val editionTags =
      buildList {
        if (obj.bool("isProper", "is_proper") == true) add("PROPER")
        if (obj.bool("isRepack", "is_repack") == true) add("REPACK")
        if (obj.bool("isRemastered", "is_remastered") == true) add("REMASTERED")
      }

    return TorrentRelease(
      infoHash = resolvedHash,
      releaseName = cleanName,
      hasExplicitReleaseName = isExplicit,
      titleId = obj.str("titleId", "title_id", "contentId", "content_id") ?: parentTitleId,
      mediaTitle = obj.str("mediaTitle", "media_title") ?: fallbackMediaTitle,
      tmdbId = obj.int("tmdbId", "tmdb_id")?.takeIf { it > 0 } ?: parentTmdbId?.takeIf { it > 0 },
      imdbId = obj.str("imdbId", "imdb_id") ?: parentImdbId,
      resolution = res,
      sourceType = sourceType,
      videoCodec = videoCodec,
      audioCodec = audioCodec,
      audioChannels = audioChannels,
      hdrFormat = hdr,
      sizeBytes =
        obj.long("sizeBytes", "size_bytes", "size")
          ?: parseFormattedSizeToBytes(obj.str("sizeFormatted", "size_formatted"))
          ?: 0L,
      seeders = obj.int("seeders", "seeds") ?: 0,
      leechers = obj.int("leechers", "peers") ?: 0,
      qualityScore = obj.int("qualityScore", "quality_score", "score") ?: 0,
      trueSpecStatus = tsStatus,
      trackerSource = trackerSource,
      seasonNumber = obj.int("season", "seasonNumber", "season_number")?.takeIf { it >= 0 } ?: parsedSeason,
      episodeNumber = obj.int("episode", "episodeNumber", "episode_number")?.takeIf { it > 0 } ?: parsedEp,
      releaseDate = parseDateField(obj),
      language = parsedLanguage,
      releaseGroup = parsedGroup,
      fileCount = parsedFileCount,
      editionTags = editionTags,
      contentUrl = obj.str("contentUrl", "content_url") ?: parentContentUrl,
      magnetUrl = magnet,
      torrentDownloadUrl = torrentDownloadUrl,
    )
  }

  private fun hasTorrentSpecificFields(obj: JsonObject): Boolean =
    obj.str(
      "infoHash",
      "info_hash",
      "infohash",
      "hash",
      "btih",
      "magnet",
      "magnetUrl",
      "magnet_url",
      "magnetUri",
      "magnet_uri",
      "magnetLink",
      "magnet_link",
      "torrentUrl",
      "torrent_url",
      "downloadUrl",
      "download_url",
      "torrentFile",
      "torrent_file",
      "rawTitle",
      "raw_title",
      "releaseName",
      "release_name",
      "rawName",
      "raw_name",
      "filename",
      "fileName",
      "file_name",
      "quality",
      "resolution",
      "sizeFormatted",
      "size_formatted",
    ) != null ||
      obj.long("sizeBytes", "size_bytes", "size") != null ||
      obj.int("seeders", "seeds", "qualityScore", "quality_score", "score") != null

  private fun parseDateField(obj: JsonObject): String? {
    val keys =
      arrayOf(
        "releaseDate",
        "release_date",
        "publishedAt",
        "published_at",
        "uploadedAt",
        "uploaded_at",
        "addedAt",
        "added_at",
        "indexedAt",
        "indexed_at",
        "scrapedAt",
        "scraped_at",
        "createdAt",
        "created_at",
        "airDate",
        "air_date",
        "updatedAt",
        "updated_at",
        "date",
        "timestamp",
      )
    val rawStr = obj.str(*keys)
    if (rawStr != null) {
      val epochNum = rawStr.toLongOrNull()
      if (epochNum != null && rawStr.length >= 9) {
        val millis = if (rawStr.length <= 10) epochNum * 1000L else epochNum
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return fmt.format(Date(millis))
      }
      return rawStr
    }
    val epochLong = obj.long(*keys)
    if (epochLong != null && epochLong > 100_000_000L) {
      val millis = if (epochLong < 10_000_000_000L) epochLong * 1000L else epochLong
      val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
      return fmt.format(Date(millis))
    }
    return null
  }

  private fun parseFormattedSizeToBytes(formatted: String?): Long? {
    val clean = formatted?.trim() ?: return null
    val match = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|MB|KB|B)$", RegexOption.IGNORE_CASE).find(clean)
      ?: return null
    val num = match.groupValues[1].toDoubleOrNull() ?: return null
    val mult =
      when (match.groupValues[2].uppercase()) {
        "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        "GB" -> 1024.0 * 1024.0 * 1024.0
        "MB" -> 1024.0 * 1024.0
        "KB" -> 1024.0
        else -> 1.0
      }
    return (num * mult).toLong()
  }

  private fun resolveImageUrl(pathOrUrl: String, size: String = "w500"): String {
    val trimmed = pathOrUrl.trim()
    return when {
      trimmed.startsWith("https://", true) -> trimmed
      trimmed.startsWith("http://", true) -> "https://" + trimmed.removePrefix("http://")
      trimmed.startsWith("//") -> "https:$trimmed"
      trimmed.startsWith("/assets/", true) ||
        trimmed.startsWith("/images/", true) ||
        trimmed.startsWith("/posters/", true) ||
        trimmed.startsWith("/api/", true) -> "https://torrentclaw.com$trimmed"
      else -> {
        val cleanPath = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        "https://image.tmdb.org/t/p/$size$cleanPath"
      }
    }
  }

  private fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) {
      val el = this[k] ?: continue
      val v = runCatching { el.jsonPrimitive.contentOrNull }.getOrNull()?.trim()
      if (!v.isNullOrBlank() && !v.equals("null", true)) return v
    }
    return null
  }

  private fun JsonObject.int(vararg keys: String): Int? {
    for (k in keys) {
      val el = this[k] ?: continue
      val p = runCatching { el.jsonPrimitive }.getOrNull() ?: continue
      p.intOrNull?.let { return it }
      p.contentOrNull?.toIntOrNull()?.let { return it }
    }
    return null
  }

  private fun JsonObject.long(vararg keys: String): Long? {
    for (k in keys) {
      val el = this[k] ?: continue
      val p = runCatching { el.jsonPrimitive }.getOrNull() ?: continue
      p.longOrNull?.let { return it }
      p.contentOrNull?.toLongOrNull()?.let { return it }
    }
    return null
  }

  private fun JsonObject.double(vararg keys: String): Double? {
    for (k in keys) {
      val el = this[k] ?: continue
      val p = runCatching { el.jsonPrimitive }.getOrNull() ?: continue
      p.doubleOrNull?.let { return it }
      p.contentOrNull?.toDoubleOrNull()?.let { return it }
    }
    return null
  }

  private fun JsonObject.bool(vararg keys: String): Boolean? {
    for (k in keys) {
      val el = this[k] ?: continue
      val b = runCatching { el.jsonPrimitive.booleanOrNull }.getOrNull()
      if (b != null) return b
    }
    return null
  }

  private fun JsonObject.obj(vararg keys: String): JsonObject? {
    for (k in keys) {
      val o = this[k] as? JsonObject
      if (o != null) return o
    }
    return null
  }

  private fun JsonObject.arrayOf(vararg keys: String): JsonArray? {
    for (k in keys) {
      val a = this[k] as? JsonArray
      if (a != null) return a
    }
    return null
  }
}
