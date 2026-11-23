package com.example.data.remote

import com.example.data.model.AutocompleteSuggestion
import com.example.data.model.CollectionSummary
import com.example.data.model.CreditPerson
import com.example.data.model.EpisodeInfo
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.PlatformStats
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecReport
import com.example.data.model.TrueSpecStatus
import com.example.data.model.WatchProvider
import com.example.data.model.normalizeResolutionLabel
import java.net.URLDecoder
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

object TorrentClawJsonParser {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  private fun parseRoot(raw: String): JsonElement? = runCatching { json.parseToJsonElement(raw) }.getOrNull()

  private fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) {
      val v = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()
      if (!v.isNullOrEmpty() && v != "null") return v
    }
    return null
  }

  private fun JsonObject.intVal(vararg keys: String): Int? {
    for (k in keys) {
      val p = this[k] as? JsonPrimitive ?: continue
      p.intOrNull?.let { return it }
      p.contentOrNull?.toIntOrNull()?.let { return it }
    }
    return null
  }

  private fun JsonObject.longVal(vararg keys: String): Long? {
    for (k in keys) {
      val p = this[k] as? JsonPrimitive ?: continue
      p.longOrNull?.let { return it }
      p.contentOrNull?.toLongOrNull()?.let { return it }
    }
    return null
  }

  private fun JsonObject.doubleVal(vararg keys: String): Double? {
    for (k in keys) {
      val p = this[k] as? JsonPrimitive ?: continue
      p.doubleOrNull?.let { return it }
      p.contentOrNull?.toDoubleOrNull()?.let { return it }
    }
    return null
  }

  private fun JsonObject.boolVal(vararg keys: String): Boolean? {
    for (k in keys) {
      val p = this[k] as? JsonPrimitive ?: continue
      p.booleanOrNull?.let { return it }
      when (p.contentOrNull?.lowercase(Locale.US)) {
        "true",
        "1" -> return true
        "false",
        "0" -> return false
      }
    }
    return null
  }

  private fun extractArray(root: JsonElement?, vararg keys: String): JsonArray {
    if (root == null) return JsonArray(emptyList())
    if (root is JsonArray) return root
    if (root is JsonObject) {
      for (k in keys) {
        val child = root[k]
        if (child is JsonArray) return child
        if (child is JsonObject) {
          for (innerKey in keys) {
            val inner = child[innerKey]
            if (inner is JsonArray) return inner
          }
        }
      }
    }
    return JsonArray(emptyList())
  }

  private fun resolvePosterUrl(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return when {
      raw.startsWith("http://") || raw.startsWith("https://") -> raw
      raw.startsWith("/") -> "https://image.tmdb.org/t/p/w500$raw"
      else -> raw
    }
  }

  private fun resolveBackdropUrl(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return when {
      raw.startsWith("http://") || raw.startsWith("https://") -> raw
      raw.startsWith("/") -> "https://image.tmdb.org/t/p/w1280$raw"
      else -> raw
    }
  }

  fun parseSearchResponse(
    raw: String,
    fallbackTitleId: String? = null,
    fallbackMediaTitle: String? = null,
    fallbackPoster: String? = null,
  ): SearchParseResult {
    val root = parseRoot(raw)
    val itemsArray = extractArray(root, "results", "items", "content", "media", "data", "torrents")
    val mediaItems = mutableListOf<MediaItem>()
    val looseTorrents = mutableListOf<TorrentRelease>()

    for (element in itemsArray) {
      val obj = element as? JsonObject ?: continue
      // Determine whether each entry is a MediaItem (potentially with nested torrents) or a flat Torrent entry
      val hasNestedTorrents = obj["torrents"] is JsonArray || obj["releases"] is JsonArray
      val hasTitleId = obj.str("tmdb_id", "tmdbId", "imdb_id", "imdbId", "poster_url", "poster_path", "overview") != null
      val hasHashOnly = obj.str("info_hash", "infoHash", "hash") != null && !hasNestedTorrents

      if (hasHashOnly && !hasTitleId) {
        parseTorrentObject(obj, fallbackTitleId, fallbackMediaTitle, fallbackPoster)?.let { looseTorrents.add(it) }
      } else {
        val parsed = parseMediaObject(obj)
        mediaItems.add(parsed)
        if (hasHashOnly) {
          parseTorrentObject(
            obj,
            parsed.id.ifBlank { fallbackTitleId },
            parsed.title.takeIf { it != "Untitled" } ?: fallbackMediaTitle,
            parsed.posterUrl ?: fallbackPoster,
          )?.let {
            looseTorrents.add(it)
          }
        }
      }
    }

    // Also inspect top-level torrents array if returned alongside media
    if (root is JsonObject && root["torrents"] is JsonArray) {
      for (el in root["torrents"]!!.jsonArray) {
        val obj = el as? JsonObject ?: continue
        parseTorrentObject(obj, fallbackTitleId, fallbackMediaTitle, fallbackPoster)?.let { looseTorrents.add(it) }
      }
    }

    val total =
      (root as? JsonObject)?.intVal("total", "total_results", "totalCount", "count")
        ?: maxOf(mediaItems.size, looseTorrents.size)

    return SearchParseResult(
      mediaItems = mediaItems,
      torrents = looseTorrents,
      total = total,
    )
  }

  fun parseMediaList(raw: String): List<MediaItem> {
    val root = parseRoot(raw)
    val arr = extractArray(root, "results", "items", "popular", "recent", "trending", "data", "content")
    return arr.mapNotNull { (it as? JsonObject)?.let(::parseMediaObject) }
  }

  fun parseMediaDetail(raw: String, fallbackId: String = ""): MediaItem? {
    val root = parseRoot(raw) as? JsonObject ?: return null
    val target = (root["content"] as? JsonObject) ?: (root["data"] as? JsonObject) ?: (root["result"] as? JsonObject) ?: root
    var parsed = parseMediaObject(target)
    if (parsed.id.isBlank() && fallbackId.isNotBlank()) {
      parsed = parsed.copy(id = fallbackId)
    }
    if (target != root) {
      val rootTorrents =
        extractArray(root, "torrents", "releases", "streams", "sources").mapNotNull { el ->
          (el as? JsonObject)?.let {
            parseTorrentObject(
              obj = it,
              fallbackTitleId = parsed.id,
              fallbackMediaTitle = parsed.title,
              fallbackPoster = parsed.posterUrl,
              fallbackTmdbId = parsed.tmdbId,
              fallbackImdbId = parsed.imdbId,
              fallbackMediaYear = parsed.year,
            )
          }
        }
      if (rootTorrents.isNotEmpty()) {
        val merged = (parsed.torrents + rootTorrents).distinctBy { it.infoHash }
        parsed = parsed.copy(torrents = merged, torrentCount = maxOf(parsed.torrentCount, merged.size))
      }
    }
    return parsed
  }

  fun parseMediaObject(obj: JsonObject): MediaItem {
    val tmdbId = obj.intVal("tmdb_id", "tmdbId")
    val imdbId = obj.str("imdb_id", "imdbId")
    val rawId =
      obj.str("id", "_id", "content_id", "contentId", "slug")
        ?: tmdbId?.toString()
        ?: imdbId
        ?: obj.str("title", "name").orEmpty().lowercase(Locale.US).replace(" ", "_")

    val title = obj.str("title", "name", "media_title", "mediaTitle") ?: "Untitled"
    val originalTitle = obj.str("original_title", "originalTitle", "original_name")
    val yearStr = obj.str("year", "release_year", "release_date", "first_air_date")
    val year =
      obj.intVal("year", "release_year")
        ?: yearStr?.take(4)?.toIntOrNull()

    val typeRaw = obj.str("type", "media_type", "mediaType", "content_type")?.lowercase(Locale.US)
    val mediaType =
      when {
        typeRaw == "tv" || typeRaw == "show" || typeRaw == "series" -> MediaType.SHOW
        obj["seasons"] is JsonArray || obj.intVal("number_of_seasons") != null -> MediaType.SHOW
        else -> MediaType.MOVIE
      }

    val poster = resolvePosterUrl(obj.str("poster_url", "posterUrl", "poster_path", "posterPath", "poster"))
    val backdrop =
      resolveBackdropUrl(obj.str("backdrop_url", "backdropUrl", "backdrop_path", "backdropPath", "backdrop"))

    val genres =
      when (val g = obj["genres"]) {
        is JsonArray ->
          g.mapNotNull { item ->
            when (item) {
              is JsonPrimitive -> item.contentOrNull
              is JsonObject -> item.str("name", "label", "title")
              else -> null
            }
          }
        is JsonPrimitive ->
          g.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        else -> emptyList()
      }

    val nestedTorrents =
      extractArray(obj, "torrents", "releases", "streams", "sources").mapNotNull { el ->
        (el as? JsonObject)?.let {
          parseTorrentObject(
            obj = it,
            fallbackTitleId = rawId,
            fallbackMediaTitle = title,
            fallbackPoster = poster,
            fallbackTmdbId = tmdbId,
            fallbackImdbId = imdbId,
            fallbackMediaYear = year,
          )
        }
      }

    val seasonsList = mutableListOf<Int>()
    val episodesMap = mutableMapOf<Int, List<EpisodeInfo>>()
    val seasonsEl = obj["seasons"]
    if (seasonsEl is JsonArray) {
      for (s in seasonsEl) {
        when (s) {
          is JsonPrimitive -> s.intOrNull?.let { seasonsList.add(it) }
          is JsonObject -> {
            val sNum = s.intVal("season_number", "season", "number") ?: continue
            seasonsList.add(sNum)
            val epsArr = s["episodes"] as? JsonArray
            if (epsArr != null) {
              episodesMap[sNum] =
                epsArr.mapNotNull { epEl ->
                  val epObj = epEl as? JsonObject ?: return@mapNotNull null
                  val epNum = epObj.intVal("episode_number", "episode", "number") ?: return@mapNotNull null
                  EpisodeInfo(
                    season = sNum,
                    episode = epNum,
                    title = epObj.str("name", "title"),
                    airDate = epObj.str("air_date", "airDate"),
                    torrentCount = epObj.intVal("torrent_count", "torrentCount") ?: 0,
                  )
                }
            }
          }
          else -> Unit
        }
      }
    } else {
      val count = obj.intVal("number_of_seasons", "season_count") ?: 0
      for (i in 1..count.coerceAtMost(30)) seasonsList.add(i)
    }

    val torrentCount =
      obj.intVal("torrent_count", "torrentCount", "release_count")
        ?: nestedTorrents.size
    val bestScore =
      obj.intVal("best_quality_score", "quality_score", "bestScore")
        ?: nestedTorrents.maxOfOrNull { it.qualityScore }
    val bestResolution =
      obj.str("best_resolution", "best_quality", "quality")
        ?: nestedTorrents.maxByOrNull { it.qualityScore }?.resolution

    val collectionObj = obj["belongs_to_collection"] as? JsonObject ?: obj["collection"] as? JsonObject

    return MediaItem(
      id = rawId,
      tmdbId = tmdbId,
      imdbId = imdbId,
      title = title,
      originalTitle = originalTitle?.takeIf { !it.equals(title, ignoreCase = true) },
      year = year,
      mediaType = mediaType,
      rating = obj.doubleVal("rating", "vote_average", "imdb_rating", "tmdb_rating"),
      runtimeMinutes = obj.intVal("runtime", "runtime_minutes", "duration"),
      genres = genres,
      overview = obj.str("overview", "synopsis", "description") ?: "",
      posterUrl = poster,
      backdropUrl = backdrop ?: poster,
      torrentCount = torrentCount,
      bestQualityScore = bestScore,
      bestResolution = bestResolution,
      seasons = seasonsList.distinct().sorted(),
      episodesPerSeason = episodesMap,
      collectionName = collectionObj?.str("name", "title") ?: obj.str("collection_name"),
      collectionId = collectionObj?.str("id") ?: obj.str("collection_id"),
      torrents = nestedTorrents,
    )
  }

  fun parseTorrentObject(
    obj: JsonObject,
    fallbackTitleId: String? = null,
    fallbackMediaTitle: String? = null,
    fallbackPoster: String? = null,
    fallbackTmdbId: Int? = null,
    fallbackImdbId: String? = null,
    fallbackMediaYear: Int? = null,
  ): TorrentRelease? {
    val infoHash = obj.str("info_hash", "infoHash", "hash", "btih") ?: return null
    val magnetUrl = obj.str("magnet", "magnet_url", "magnetUrl", "magnet_uri")

    val trueSpecObj = obj["truespec"] as? JsonObject ?: obj["true_spec"] as? JsonObject
    val trueSpecStatus = parseTrueSpecStatus(obj, trueSpecObj)
    val trueSpecReport = parseTrueSpecReport(trueSpecObj, trueSpecStatus)

    val resolvedMediaTitle =
      obj.str("media_title", "mediaTitle", "content_title", "contentTitle")
        ?: fallbackMediaTitle?.takeIf { it.isNotBlank() && it != "Untitled" }
    val resolvedTmdbId = obj.intVal("tmdb_id", "tmdbId") ?: fallbackTmdbId
    val resolvedImdbId = obj.str("imdb_id", "imdbId") ?: fallbackImdbId
    val resolvedMediaYear = obj.intVal("media_year", "year", "release_year") ?: fallbackMediaYear

    val directCandidate = resolveRawReleaseName(obj, infoHash, magnetUrl, trueSpecReport?.primaryFileName)
    val inferenceBasis = directCandidate ?: ""

    // Extract filename-estimated metadata as fallback if API leaves individual fields blank
    val parsedResolution =
      obj.str("resolution", "quality") ?: inferResolution(inferenceBasis)
    val parsedSource =
      obj.str("source_type", "sourceType", "rip_type", "source_quality", "sourceQuality")
        ?: inferSourceType(inferenceBasis)
    val parsedCodec =
      obj.str("video_codec", "videoCodec", "codec") ?: inferVideoCodec(inferenceBasis)
    val parsedAudio =
      obj.str("audio_codec", "audioCodec", "audio") ?: inferAudioCodec(inferenceBasis)
    val rawChannels =
      obj.str("audio_channels", "audioChannels", "channels") ?: inferAudioChannels(inferenceBasis)
    val parsedChannels = normalizeAudioChannels(rawChannels)
    val parsedHdr =
      obj.str("hdr", "hdr_format", "hdrFormat") ?: inferHdr(inferenceBasis)
    val trackerSource = obj.str("tracker", "source", "indexer", "provider") ?: "TorrentClaw"
    val seasonNum = obj.intVal("season")
    val episodeNum = obj.intVal("episode")

    val releaseName =
      directCandidate
        ?: synthesizeReleaseTitle(
          mediaTitle = resolvedMediaTitle,
          resolution = parsedResolution,
          sourceType = parsedSource,
          videoCodec = parsedCodec,
          hdrFormat = parsedHdr,
          audioCodec = parsedAudio,
          audioChannels = parsedChannels,
          season = seasonNum,
          episode = episodeNum,
          trackerSource = trackerSource,
          infoHash = infoHash,
        )

    val languages =
      (obj["languages"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?: obj.str("language", "lang")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: emptyList()

    val subtitles =
      (obj["subtitles"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?: trueSpecReport?.verifiedSubtitles
        ?: emptyList()

    return TorrentRelease(
      infoHash = infoHash.lowercase(Locale.US),
      releaseName = releaseName,
      titleId = obj.str("content_id", "contentId", "tmdb_id") ?: fallbackTitleId,
      mediaTitle = resolvedMediaTitle,
      posterUrl = resolvePosterUrl(obj.str("poster_url", "posterUrl", "poster_path")) ?: fallbackPoster,
      resolution = parsedResolution,
      sourceType = parsedSource,
      videoCodec = parsedCodec,
      audioCodec = parsedAudio,
      audioChannels = parsedChannels,
      hdrFormat = parsedHdr,
      languages = languages,
      subtitles = subtitles,
      sizeBytes = obj.longVal("size_bytes", "sizeBytes", "size", "length") ?: 0L,
      seeders = obj.intVal("seeders", "seeds") ?: 0,
      leechers = obj.intVal("leechers", "peers") ?: 0,
      trackerSource = trackerSource,
      indexedAt = obj.str("indexed_at", "added", "created_at", "upload_date"),
      qualityScore = obj.intVal("quality_score", "qualityScore", "score") ?: 0,
      trueSpecStatus = trueSpecStatus,
      trueSpecReport = trueSpecReport,
      magnetUrl = magnetUrl,
      torrentFileUrl = obj.str("torrent_url", "torrentUrl")
        ?: "https://torrentclaw.com/api/v1/torrent/${infoHash.lowercase(Locale.US)}",
      season = seasonNum,
      episode = episodeNum,
      tmdbId = resolvedTmdbId,
      imdbId = resolvedImdbId,
      mediaYear = resolvedMediaYear,
      hasExplicitReleaseName = directCandidate != null,
    )
  }

  private fun isHashOnlyString(value: String, infoHash: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return true
    if (trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true)) return true
    if (trimmed.equals(infoHash, ignoreCase = true)) return true
    if (trimmed.startsWith("urn:btih:", ignoreCase = true)) return true
    return Regex("^[a-fA-F0-9]{32,64}$").matches(trimmed)
  }

  private fun cleanCandidateName(raw: String?, infoHash: String): String? {
    if (raw == null) return null
    val leaf = raw.trim().substringAfterLast('/').substringAfterLast('\\').trim()
    return leaf.takeIf { it.isNotBlank() && !isHashOnlyString(it, infoHash) }
  }

  private fun extractMagnetDisplayName(magnetUrl: String?): String? {
    if (magnetUrl.isNullOrBlank()) return null
    val query = magnetUrl.substringAfter('?', "")
    if (query.isBlank()) return null
    val dnParam =
      query.split('&').firstOrNull { it.startsWith("dn=", ignoreCase = true) }?.substringAfter('=')
        ?: return null
    return runCatching { URLDecoder.decode(dnParam, "UTF-8").trim() }
      .getOrNull()
      ?.takeIf { it.isNotBlank() }
  }

  private fun extractNestedFileName(obj: JsonObject, infoHash: String): String? {
    val nestedObjects =
      listOfNotNull(
        obj["file"] as? JsonObject,
        obj["parsed"] as? JsonObject,
        obj["metadata"] as? JsonObject,
        obj["attributes"] as? JsonObject,
        obj["torrent"] as? JsonObject,
      )
    for (nested in nestedObjects) {
      val hit =
        cleanCandidateName(
          nested.str(
            "release_name",
            "releaseName",
            "raw_title",
            "raw_name",
            "filename",
            "file_name",
            "name",
            "title",
            "path",
          ),
          infoHash,
        )
      if (hit != null) return hit
    }

    val filesArr = obj["files"] as? JsonArray
    if (filesArr != null) {
      for (el in filesArr) {
        val candidate =
          when (el) {
            is JsonPrimitive -> cleanCandidateName(el.contentOrNull, infoHash)
            is JsonObject -> cleanCandidateName(el.str("name", "filename", "file_name", "path", "title"), infoHash)
            else -> null
          }
        if (candidate != null) return candidate
      }
    }
    return null
  }

  private fun resolveRawReleaseName(
    obj: JsonObject,
    infoHash: String,
    magnetUrl: String?,
    trueSpecPrimaryFile: String?,
  ): String? {
    val candidateKeys =
      listOf(
        "release_name",
        "releaseName",
        "raw_title",
        "rawTitle",
        "raw_name",
        "rawName",
        "torrent_name",
        "torrentName",
        "file_name",
        "fileName",
        "filename",
        "display_name",
        "displayName",
        "release_title",
        "releaseTitle",
        "clean_title",
        "cleanTitle",
        "original_filename",
        "original_name",
        "name",
        "title",
        "release",
        "label",
        "dn",
      )
    for (key in candidateKeys) {
      val found = cleanCandidateName(obj.str(key), infoHash)
      if (found != null) return found
    }
    cleanCandidateName(trueSpecPrimaryFile, infoHash)?.let { return it }
    extractNestedFileName(obj, infoHash)?.let { return it }
    cleanCandidateName(extractMagnetDisplayName(magnetUrl), infoHash)?.let { return it }
    return null
  }

  private fun normalizeAudioChannels(raw: String?): String? {
    return when (raw?.trim()) {
      null, "" -> null
      "8" -> "7.1"
      "6" -> "5.1"
      "2" -> "2.0"
      "1" -> "1.0"
      else -> raw.trim()
    }
  }

  private fun formatTokenForTitle(value: String?): String? {
    val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when (trimmed.lowercase(Locale.US)) {
      "bluray", "blu-ray" -> "BluRay"
      "remux" -> "REMUX"
      "web-dl", "webdl" -> "WEB-DL"
      "webrip" -> "WEBRip"
      "hdtv" -> "HDTV"
      "hevc" -> "HEVC"
      "av1" -> "AV1"
      "h264", "h.264", "avc" -> "H.264"
      "x265" -> "x265"
      "x264" -> "x264"
      "truehd" -> "TrueHD"
      "atmos" -> "Atmos"
      "dts" -> "DTS"
      "dts-hd", "dts-hd ma" -> "DTS-HD MA"
      "eac3", "ddp", "dd+" -> "DD+"
      "ac3" -> "AC3"
      "aac" -> "AAC"
      "flac" -> "FLAC"
      "opus" -> "OPUS"
      else -> trimmed
    }
  }

  fun synthesizeReleaseTitle(
    mediaTitle: String?,
    resolution: String,
    sourceType: String?,
    videoCodec: String?,
    hdrFormat: String?,
    audioCodec: String?,
    audioChannels: String?,
    season: Int? = null,
    episode: Int? = null,
    trackerSource: String = "TorrentClaw",
    infoHash: String = "",
  ): String {
    val episodeTag =
      when {
        season != null && episode != null ->
          String.format(Locale.US, "S%02dE%02d", season, episode)
        season != null -> String.format(Locale.US, "S%02d", season)
        else -> null
      }
    val resLabel = normalizeResolutionLabel(resolution).takeIf { it != "SD" }
    val audioLabel =
      listOfNotNull(formatTokenForTitle(audioCodec), audioChannels)
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
    val specParts =
      listOfNotNull(
        episodeTag,
        resLabel,
        formatTokenForTitle(sourceType),
        formatTokenForTitle(videoCodec),
        hdrFormat,
        audioLabel,
      )
    val cleanTitle = mediaTitle?.takeIf { it.isNotBlank() && it != "Untitled" }
    return when {
      cleanTitle != null && specParts.isNotEmpty() -> "$cleanTitle • ${specParts.joinToString(" ")}"
      specParts.isNotEmpty() -> "${specParts.joinToString(" ")} [$trackerSource]"
      cleanTitle != null -> "$cleanTitle • ${infoHash.take(8).uppercase(Locale.US)}"
      else -> "Release ${infoHash.take(8).uppercase(Locale.US)}"
    }
  }

  private fun parseTrueSpecStatus(obj: JsonObject, tsObj: JsonObject?): TrueSpecStatus {
    val raw =
      (tsObj?.str("status", "state", "verdict")
        ?: obj.str("truespec_status", "trueSpecStatus", "verification_status"))
        ?.lowercase(Locale.US)
    val isVerifiedFlag = tsObj?.boolVal("verified") ?: obj.boolVal("truespec_verified", "verified")
    val isSuspiciousFlag = tsObj?.boolVal("suspicious", "is_fake", "malware") ?: obj.boolVal("suspicious")
    return when {
      isSuspiciousFlag == true || raw in listOf("suspicious", "fake", "malware", "malicious", "rejected") ->
        TrueSpecStatus.SUSPICIOUS
      raw in listOf("warning", "mismatch", "caution") -> TrueSpecStatus.WARNING
      isVerifiedFlag == true || raw in listOf("verified", "passed", "clean", "complete", "completed") ->
        TrueSpecStatus.VERIFIED
      raw in listOf("pending", "queued", "scanning", "in_progress", "processing") ->
        TrueSpecStatus.PENDING
      else -> TrueSpecStatus.UNVERIFIED
    }
  }

  fun parseTrueSpecScanResponse(raw: String): TrueSpecReport? {
    val root = parseRoot(raw) as? JsonObject ?: return null
    val target = (root["truespec"] as? JsonObject) ?: (root["scan"] as? JsonObject) ?: (root["result"] as? JsonObject) ?: root
    val status = parseTrueSpecStatus(target, target)
    return parseTrueSpecReport(target, status)
  }

  private fun parseTrueSpecReport(tsObj: JsonObject?, status: TrueSpecStatus): TrueSpecReport? {
    if (tsObj == null && status == TrueSpecStatus.UNVERIFIED) return null
    if (tsObj == null) return TrueSpecReport(status = status)
    val warnings =
      (tsObj["warnings"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    val mismatches =
      (tsObj["mismatches"] as? JsonArray ?: tsObj["mismatch_reasons"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
    val audioTracks =
      (tsObj["audio_tracks"] as? JsonArray ?: tsObj["audio"] as? JsonArray)
        ?.mapNotNull { item ->
          when (item) {
            is JsonPrimitive -> item.contentOrNull
            is JsonObject -> item.str("codec", "format", "language")
            else -> null
          }
        }
        .orEmpty()
    val subs =
      (tsObj["subtitles"] as? JsonArray ?: tsObj["subtitle_languages"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()

    return TrueSpecReport(
      status = status,
      verifiedResolution = tsObj.str("resolution", "verified_resolution", "height"),
      verifiedVideoCodec = tsObj.str("video_codec", "codec", "verified_codec"),
      verifiedBitDepth = tsObj.str("bit_depth", "bitDepth"),
      verifiedAudioTracks = audioTracks,
      verifiedSubtitles = subs,
      primaryFileName = tsObj.str("primary_file", "filename", "main_file"),
      fileCount = tsObj.intVal("file_count", "files"),
      malwareScanClean = tsObj.boolVal("malware_clean", "clean", "safe"),
      warnings = warnings,
      mismatchReasons = mismatches,
      scannedAt = tsObj.str("scanned_at", "updated_at", "completed_at"),
    )
  }

  fun parseAutocomplete(raw: String): List<AutocompleteSuggestion> {
    val root = parseRoot(raw)
    val arr = extractArray(root, "suggestions", "results", "items", "data")
    return arr.mapNotNull { el ->
      when (el) {
        is JsonPrimitive ->
          el.contentOrNull?.takeIf { it.isNotBlank() }?.let {
            AutocompleteSuggestion(id = it, title = it)
          }
        is JsonObject -> {
          val title = el.str("title", "name", "text", "query") ?: return@mapNotNull null
          val tmdbId = el.intVal("tmdb_id", "tmdbId")
          val imdbId = el.str("imdb_id", "imdbId")
          val id = el.str("id", "content_id") ?: tmdbId?.toString() ?: imdbId ?: title
          val type =
            when (el.str("type", "media_type")?.lowercase(Locale.US)) {
              "tv",
              "show",
              "series" -> MediaType.SHOW
              else -> MediaType.MOVIE
            }
          AutocompleteSuggestion(
            id = id,
            title = title,
            year = el.intVal("year", "release_year"),
            mediaType = type,
            posterUrl = resolvePosterUrl(el.str("poster_url", "poster_path")),
            tmdbId = tmdbId,
            imdbId = imdbId,
          )
        }
        else -> null
      }
    }
  }

  fun parseGenres(raw: String): List<String> {
    val root = parseRoot(raw)
    val arr = extractArray(root, "genres", "results", "data")
    return arr.mapNotNull { el ->
      when (el) {
        is JsonPrimitive -> el.contentOrNull
        is JsonObject -> el.str("name", "genre", "title", "label")
        else -> null
      }
    }
  }

  fun parseCollections(raw: String): List<CollectionSummary> {
    val root = parseRoot(raw)
    val arr = extractArray(root, "collections", "results", "items", "data")
    return arr.mapNotNull { el ->
      val obj = el as? JsonObject ?: return@mapNotNull null
      val name = obj.str("name", "title") ?: return@mapNotNull null
      val id = obj.str("id", "collection_id") ?: name
      CollectionSummary(
        id = id,
        name = name,
        overview = obj.str("overview", "description"),
        posterUrl = resolvePosterUrl(obj.str("poster_url", "poster_path")),
        backdropUrl = resolveBackdropUrl(obj.str("backdrop_url", "backdrop_path")),
        itemCount = obj.intVal("item_count", "parts_count", "count") ?: 0,
      )
    }
  }

  fun parseCredits(raw: String): List<CreditPerson> {
    val root = parseRoot(raw)
    val results = mutableListOf<CreditPerson>()
    if (root is JsonObject) {
      val directors = root["directors"] as? JsonArray ?: root["crew"] as? JsonArray
      directors?.forEach { el ->
        val obj = el as? JsonObject ?: return@forEach
        val name = obj.str("name") ?: return@forEach
        results.add(
          CreditPerson(
            id = obj.str("id") ?: name,
            name = name,
            role = obj.str("job", "role") ?: "Director",
            department = obj.str("department") ?: "Directing",
            profileUrl = resolvePosterUrl(obj.str("profile_url", "profile_path")),
          )
        )
      }
      val cast = root["cast"] as? JsonArray ?: root["actors"] as? JsonArray
      cast?.forEach { el ->
        val obj = el as? JsonObject ?: return@forEach
        val name = obj.str("name") ?: return@forEach
        results.add(
          CreditPerson(
            id = obj.str("id") ?: name,
            name = name,
            role = obj.str("character", "role") ?: "Cast",
            department = "Acting",
            profileUrl = resolvePosterUrl(obj.str("profile_url", "profile_path")),
          )
        )
      }
    }
    if (results.isEmpty()) {
      extractArray(root, "results", "credits", "data").forEach { el ->
        val obj = el as? JsonObject ?: return@forEach
        val name = obj.str("name") ?: return@forEach
        results.add(
          CreditPerson(
            id = obj.str("id") ?: name,
            name = name,
            role = obj.str("character", "job", "role") ?: "Cast",
            department = obj.str("department") ?: "Acting",
            profileUrl = resolvePosterUrl(obj.str("profile_url", "profile_path")),
          )
        )
      }
    }
    return results
  }

  fun parseWatchProviders(raw: String): List<WatchProvider> {
    val root = parseRoot(raw)
    val list = mutableListOf<WatchProvider>()
    val arr = extractArray(root, "providers", "watch_providers", "flatrate", "results", "data")
    for (el in arr) {
      val obj = el as? JsonObject ?: continue
      val name = obj.str("provider_name", "name", "provider") ?: continue
      list.add(
        WatchProvider(
          providerName = name,
          logoUrl = resolvePosterUrl(obj.str("logo_url", "logo_path", "icon")),
          type = obj.str("type", "monetization_type") ?: "stream",
          link = obj.str("link", "url"),
        )
      )
    }
    return list.distinctBy { it.providerName.lowercase(Locale.US) }
  }

  fun parseStats(raw: String): PlatformStats? {
    val root = parseRoot(raw) as? JsonObject ?: return null
    val obj = (root["stats"] as? JsonObject) ?: (root["data"] as? JsonObject) ?: root
    return PlatformStats(
      totalTitles = obj.longVal("total_titles", "titles", "media_count", "total_content") ?: 0L,
      totalTorrents = obj.longVal("total_torrents", "torrents", "indexed_torrents") ?: 0L,
      indexedSources = obj.intVal("sources_count", "trackers", "sources") ?: 30,
      trueSpecVerifiedCount = obj.longVal("truespec_verified", "verified_count") ?: 0L,
    )
  }

  private fun inferResolution(name: String): String {
    val lower = name.lowercase(Locale.US)
    return when {
      "2160p" in lower || "4k" in lower || "uhd" in lower -> "2160p"
      "1080p" in lower -> "1080p"
      "720p" in lower -> "720p"
      "480p" in lower -> "480p"
      else -> "SD"
    }
  }

  private fun inferSourceType(name: String): String? {
    val lower = name.lowercase(Locale.US)
    return when {
      "remux" in lower -> "REMUX"
      "bluray" in lower || "blu-ray" in lower || "bdrip" in lower -> "BluRay"
      "web-dl" in lower || "webdl" in lower -> "WEB-DL"
      "webrip" in lower -> "WEBRip"
      "hdtv" in lower -> "HDTV"
      "cam" in lower || "ts" in lower -> "CAM/TS"
      else -> null
    }
  }

  private fun inferVideoCodec(name: String): String? {
    val lower = name.lowercase(Locale.US)
    return when {
      "av1" in lower -> "AV1"
      "x265" in lower || "h265" in lower || "h.265" in lower || "hevc" in lower -> "HEVC"
      "x264" in lower || "h264" in lower || "h.264" in lower || "avc" in lower -> "H.264"
      else -> null
    }
  }

  private fun inferAudioCodec(name: String): String? {
    val lower = name.lowercase(Locale.US)
    return when {
      "atmos" in lower -> "Atmos"
      "truehd" in lower -> "TrueHD"
      "dts-hd" in lower -> "DTS-HD MA"
      "dts" in lower -> "DTS"
      "eac3" in lower || "ddp" in lower || "dd+" in lower -> "DD+"
      "ac3" in lower || "dd5" in lower -> "AC3"
      "flac" in lower -> "FLAC"
      "opus" in lower -> "OPUS"
      "aac" in lower -> "AAC"
      else -> null
    }
  }

  private fun inferAudioChannels(name: String): String? {
    return when {
      "7.1" in name -> "7.1"
      "5.1" in name -> "5.1"
      "2.0" in name -> "2.0"
      else -> null
    }
  }

  private fun inferHdr(name: String): String? {
    val lower = name.lowercase(Locale.US)
    return when {
      "dolby.vision" in lower || "dolby vision" in lower || ".dv." in lower || " dovi " in lower ->
        "Dolby Vision"
      "hdr10+" in lower -> "HDR10+"
      "hdr10" in lower || ".hdr." in lower -> "HDR10"
      "hlg" in lower -> "HLG"
      else -> null
    }
  }
}

data class SearchParseResult(
  val mediaItems: List<MediaItem>,
  val torrents: List<TorrentRelease>,
  val total: Int,
)
