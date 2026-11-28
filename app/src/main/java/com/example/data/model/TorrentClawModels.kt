package com.example.data.model

import com.example.core.preferences.UserPreferencesState
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import kotlin.math.abs
import kotlinx.serialization.Serializable

enum class MediaType(val apiValue: String?, val label: String) {
  ALL(null, "All"),
  MOVIE("movie", "Movies"),
  SHOW("show", "TV Shows"),
}

enum class TrueSpecStatus(val label: String) {
  VERIFIED("TrueSpec Verified"),
  UNVERIFIED("Unverified"),
  FAILED("Spec Mismatch"),
  SCANNING("Inspecting…"),
  UNKNOWN("Estimated"),
}

enum class SortOption(val apiValue: String, val label: String) {
  QUALITY_SCORE("quality", "Best Quality"),
  SEEDERS("seeders", "Most Seeded"),
  SIZE_ASC("size_asc", "Smallest Size"),
  SIZE_DESC("size_desc", "Largest Size"),
  RATING("rating", "Highest Rated"),
  NEWEST("newest", "Newest Releases"),
}

enum class TorrentSizeFilter(val label: String) {
  ALL("All sizes"),
  UNDER_2_GB("< 2 GB"),
  GB_2_TO_10("2–10 GB"),
  GB_10_TO_20("10–20 GB"),
  OVER_20_GB("20+ GB"),
}

enum class TorrentListSort(val label: String) {
  RELEASE_DATE_AND_EPISODE("Release Date & Episode"),
  BEST_QUALITY("Best Quality"),
  MOST_SEEDED("Most Seeded"),
  SMALLEST_SIZE("Smallest Size"),
  LARGEST_SIZE("Largest Size"),
  EPISODE_ASC("Episode Order"),
}

val STANDARD_RESOLUTION_OPTIONS = listOf("2160p", "1080p", "720p", "480p")

data class SearchFilter(
  val query: String = "",
  val mediaType: MediaType = MediaType.ALL,
  val genre: String? = null,
  val yearMin: Int? = null,
  val yearMax: Int? = null,
  val minRating: Float? = null,
  val resolution: String? = null,
  val codec: String? = null,
  val hdr: String? = null,
  val sortBy: SortOption = SortOption.QUALITY_SCORE,
) {
  val activeFilterCount: Int
    get() =
      listOfNotNull(
          mediaType.takeIf { it != MediaType.ALL },
          genre,
          yearMin,
          yearMax,
          minRating,
          resolution,
          codec,
          hdr,
        )
        .size
}

data class TorrentListFilterState(
  val resolutions: Set<String> = emptySet(),
  val sizeFilter: TorrentSizeFilter = TorrentSizeFilter.ALL,
  val customMinSizeGb: Float? = null,
  val customMaxSizeGb: Float? = null,
  val codecs: Set<String> = emptySet(),
  val hdrFormats: Set<String> = emptySet(),
  val sources: Set<String> = emptySet(),
  val verifiedOnly: Boolean = false,
  val minSeeders: Int = 0,
  val minQualityScore: Int = 0,
  val keyword: String = "",
  val sortBy: TorrentListSort = TorrentListSort.BEST_QUALITY,
) {
  val isDefaultSort: Boolean
    get() = sortBy == TorrentListSort.BEST_QUALITY

  val activeCount: Int
    get() =
      resolutions.size +
        (if (sizeFilter != TorrentSizeFilter.ALL || customMinSizeGb != null || customMaxSizeGb != null) 1 else 0) +
        codecs.size +
        hdrFormats.size +
        sources.size +
        (if (verifiedOnly) 1 else 0) +
        (if (minSeeders > 0) 1 else 0) +
        (if (minQualityScore > 0) 1 else 0) +
        (if (keyword.isNotBlank()) 1 else 0)
}

data class TorrentFilterFacets(
  val resolutions: List<Pair<String, Int>> = emptyList(),
  val codecs: List<Pair<String, Int>> = emptyList(),
  val hdrFormats: List<Pair<String, Int>> = emptyList(),
  val sources: List<Pair<String, Int>> = emptyList(),
  val verifiedCount: Int = 0,
)

data class CastMember(
  val id: String,
  val name: String,
  val role: String? = null,
  val profileUrl: String? = null,
)

enum class WatchCategory(val label: String) {
  STREAM("Stream"),
  FREE("Free"),
  RENT("Rent"),
  BUY("Buy"),
}

data class WatchProvider(
  val providerId: String,
  val providerName: String,
  val logoUrl: String? = null,
  val category: WatchCategory = WatchCategory.STREAM,
)

data class TorrentRelease(
  val infoHash: String,
  val releaseName: String,
  val hasExplicitReleaseName: Boolean = true,
  val titleId: String? = null,
  val mediaTitle: String? = null,
  val tmdbId: Int? = null,
  val imdbId: String? = null,
  val resolution: String = "",
  val sourceType: String = "",
  val videoCodec: String = "",
  val audioCodec: String = "",
  val audioChannels: String = "",
  val hdrFormat: String? = null,
  val sizeBytes: Long = 0L,
  val seeders: Int = 0,
  val leechers: Int = 0,
  val qualityScore: Int = 0,
  val trueSpecStatus: TrueSpecStatus = TrueSpecStatus.UNKNOWN,
  val trackerSource: String = "",
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
  val releaseDate: String? = null,
  val language: String? = null,
  val releaseGroup: String? = null,
  val fileCount: Int? = null,
  val editionTags: List<String> = emptyList(),
  val contentUrl: String? = null,
  val magnetUrl: String? = null,
  val torrentDownloadUrl: String? = null,
) {
  val hasDirectMagnet: Boolean
    get() =
      infoHash.isNotBlank() ||
        magnetUrl?.startsWith("magnet:", ignoreCase = true) == true ||
        !torrentDownloadUrl.isNullOrBlank()

  val resolvedMagnetUrl: String
    get() =
      magnetUrl?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
        ?: if (infoHash.isNotBlank()) buildMagnetUri(infoHash, releaseName) else ""

  val resolvedContentWebUrl: String?
    get() =
      contentUrl?.trim()?.takeIf { it.isNotBlank() }?.let { path ->
        if (path.startsWith("http", ignoreCase = true)) path
        else "https://torrentclaw.com${if (path.startsWith("/")) path else "/$path"}"
      }
}

data class MediaItem(
  val id: String,
  val tmdbId: Int? = null,
  val imdbId: String? = null,
  val title: String,
  val originalTitle: String? = null,
  val year: Int? = null,
  val mediaType: MediaType = MediaType.MOVIE,
  val rating: Double? = null,
  val imdbRating: Double? = null,
  val voteCount: Int? = null,
  val runtimeMinutes: Int? = null,
  val releaseDate: String? = null,
  val tagline: String? = null,
  val status: String? = null,
  val originalLanguage: String? = null,
  val certification: String? = null,
  val numberOfSeasons: Int? = null,
  val numberOfEpisodes: Int? = null,
  val genres: List<String> = emptyList(),
  val posterUrl: String? = null,
  val backdropUrl: String? = null,
  val overview: String = "",
  val torrentCount: Int = 0,
  val hasTorrents: Boolean? = null,
  val torrents: List<TorrentRelease> = emptyList(),
  val contentUrl: String? = null,
  val collectionName: String? = null,
  val cast: List<CastMember> = emptyList(),
  val watchProviders: List<WatchProvider> = emptyList(),
) {
  val resolvedContentWebUrl: String?
    get() =
      contentUrl?.trim()?.takeIf { it.isNotBlank() }?.let { path ->
        if (path.startsWith("http", ignoreCase = true)) path
        else "https://torrentclaw.com${if (path.startsWith("/")) path else "/$path"}"
      }
}

data class CollectionSummary(
  val id: String,
  val name: String,
  val description: String = "",
  val itemCount: Int = 0,
  val backdropUrl: String? = null,
  val posterUrls: List<String> = emptyList(),
)

data class CatalogStats(
  val totalMovies: Int = 0,
  val totalShows: Int = 0,
  val totalTorrents: Int = 0,
  val activeSources: Int = 35,
)

data class DiscoverySnapshot(
  val trending: List<MediaItem> = emptyList(),
  val popularMovies: List<MediaItem> = emptyList(),
  val popularShows: List<MediaItem> = emptyList(),
  val recentMedia: List<MediaItem> = emptyList(),
  val recentTorrents: List<TorrentRelease> = emptyList(),
  val genres: List<String> = emptyList(),
  val collections: List<CollectionSummary> = emptyList(),
  val stats: CatalogStats = CatalogStats(),
)

data class ParsedExternalMediaUrl(
  val imdbId: String? = null,
  val tmdbId: Int? = null,
  val mediaType: MediaType? = null,
)

data class SmartMatchRecommendation(
  val release: TorrentRelease,
  val reasons: List<String>,
)

fun parseImdbOrTmdbUrl(raw: String): ParsedExternalMediaUrl? {
  val trimmed = raw.trim()
  if (trimmed.isBlank()) return null

  val imdbMatch = Regex("(tt\\d{6,10})", RegexOption.IGNORE_CASE).find(trimmed)
  if (imdbMatch != null) {
    return ParsedExternalMediaUrl(imdbId = imdbMatch.groupValues[1].lowercase())
  }

  val tmdbMatch = Regex("themoviedb\\.org/(movie|tv)/(\\d+)", RegexOption.IGNORE_CASE).find(trimmed)
  if (tmdbMatch != null) {
    val type = if (tmdbMatch.groupValues[1].equals("tv", true)) MediaType.SHOW else MediaType.MOVIE
    val id = tmdbMatch.groupValues[2].toIntOrNull()
    if (id != null) {
      return ParsedExternalMediaUrl(tmdbId = id, mediaType = type)
    }
  }
  return null
}

fun buildMagnetUri(infoHash: String, displayName: String? = null): String {
  val cleanHash = infoHash.trim()
  val dnParam =
    displayName?.trim()?.takeIf { it.isNotBlank() }?.let {
      "&dn=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}"
    }.orEmpty()
  val trackers =
    listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
      )
      .joinToString("") { "&tr=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}" }
  return "magnet:?xt=urn:btih:$cleanHash$dnParam$trackers"
}

fun extractMagnetDisplayName(magnetUri: String?): String? {
  if (magnetUri.isNullOrBlank()) return null
  val query = magnetUri.substringAfter('?', "")
  for (part in query.split('&')) {
    val idx = part.indexOf('=')
    if (idx > 0 && part.substring(0, idx).equals("dn", true)) {
      val decoded =
        runCatching { URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8.name()) }
          .getOrNull()
          ?.trim()
      if (!decoded.isNullOrBlank()) return decoded
    }
  }
  return null
}

private val hexHashRegex = Regex("^[a-fA-F0-9]{40}$|^[A-Z2-7]{32}$")

fun isRawHashText(text: String?, infoHash: String?): Boolean {
  val clean = text?.trim() ?: return true
  if (clean.isBlank()) return true
  if (!infoHash.isNullOrBlank() && clean.equals(infoHash.trim(), ignoreCase = true)) return true
  return hexHashRegex.matches(clean)
}

fun formatAudioChannels(channels: Int?): String =
  when (channels) {
    8 -> "7.1"
    6 -> "5.1"
    2 -> "2.0"
    1 -> "1.0"
    else -> channels?.let { "$it.0" }.orEmpty()
  }

fun synthesizeCleanReleaseName(
  rawCandidates: List<String?>,
  infoHash: String,
  magnetUri: String?,
  fallbackTitle: String?,
  resolution: String,
  sourceType: String,
  videoCodec: String,
  audioCodec: String,
  audioChannels: String,
  hdrFormat: String?,
): Pair<String, Boolean> {
  val explicit =
    (rawCandidates + extractMagnetDisplayName(magnetUri))
      .mapNotNull { it?.trim()?.takeIf { c -> c.isNotBlank() } }
      .firstOrNull { !isRawHashText(it, infoHash) }

  if (explicit != null) return explicit to true

  val tokens =
    listOfNotNull(
      fallbackTitle?.takeIf { it.isNotBlank() },
      resolution.takeIf { it.isNotBlank() },
      sourceType.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
      videoCodec.takeIf { it.isNotBlank() },
      hdrFormat?.takeIf { it.isNotBlank() },
      listOf(audioCodec, audioChannels).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() },
    )
  val synthesized =
    if (tokens.isNotEmpty()) tokens.joinToString(" ") else infoHash.ifBlank { "Torrent Release" }
  return synthesized to false
}

fun formatFileSize(bytes: Long): String {
  if (bytes <= 0L) return "—"
  val kb = 1024.0
  val mb = kb * 1024.0
  val gb = mb * 1024.0
  return when {
    bytes >= gb -> String.format("%.2f GB", bytes / gb)
    bytes >= mb -> String.format("%.1f MB", bytes / mb)
    else -> String.format("%.0f KB", bytes / kb)
  }
}

fun normalizeResolutionTag(raw: String): String {
  val clean = raw.trim().lowercase()
  return when {
    clean == "4k" || clean == "uhd" || clean.contains("2160") -> "2160p"
    clean.contains("1440") -> "1440p"
    clean.contains("1080") -> "1080p"
    clean.contains("720") -> "720p"
    clean.contains("480") -> "480p"
    clean.contains("360") -> "360p"
    else -> clean
  }
}

fun normalizeCodecTag(raw: String): String {
  val c = raw.trim().lowercase()
  return when {
    c.contains("265") || c.contains("hevc") -> "HEVC"
    c.contains("264") || c.contains("avc") -> "H.264"
    c.contains("av1") -> "AV1"
    c.contains("vp9") -> "VP9"
    else -> raw.trim().uppercase()
  }
}

fun formatDisplayDate(rawDate: String?): String? {
  val clean = rawDate?.trim()?.takeIf { it.isNotBlank() } ?: return null
  val isoMatch = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(clean) ?: return clean
  val year = isoMatch.groupValues[1]
  val monthNum = isoMatch.groupValues[2].toIntOrNull() ?: return clean
  val dayNum = isoMatch.groupValues[3].toIntOrNull() ?: return clean
  val months =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
  val monthName = months.getOrNull(monthNum - 1) ?: return clean
  return "$monthName $dayNum, $year"
}

fun formatSeasonEpisodeLabel(season: Int?, episode: Int?): String? =
  when {
    season != null && season >= 0 && episode != null && episode > 0 ->
      "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    season != null && season > 0 -> "Season $season"
    episode != null && episode > 0 -> "E${episode.toString().padStart(2, '0')}"
    else -> null
  }

fun formatVoteCount(count: Int?): String? {
  val c = count?.takeIf { it > 0 } ?: return null
  return if (c >= 1000) String.format("%.1fk", c / 1000.0) else c.toString()
}

fun normalizeHdrLabel(raw: String?): String? {
  val clean = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
  return when (clean.lowercase().replace("_", "").replace("-", "").replace(" ", "")) {
    "dolbyvision", "dv", "dovi" -> "Dolby Vision"
    "hdr10plus", "hdr10+" -> "HDR10+"
    "hdr10", "hdr" -> "HDR10"
    "hlg" -> "HLG"
    "sdr", "none" -> null
    else -> clean
  }
}

fun normalizeTitleForMatching(title: String): String {
  val decomposed = Normalizer.normalize(title, Normalizer.Form.NFD)
  return decomposed
    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    .replace(Regex("\\(\\s*(?:19|20)\\d{2}\\s*\\)"), " ")
    .replace("&", " and ")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
}

fun matchesTargetMediaItem(candidate: MediaItem, target: MediaItem): Boolean {
  if (target.id.isNotBlank() && target.id == candidate.id) return true
  val tTmdb = target.tmdbId?.takeIf { it > 0 }
  val cTmdb = candidate.tmdbId?.takeIf { it > 0 }
  if (tTmdb != null && cTmdb != null && tTmdb != cTmdb) return false
  val tImdb = target.imdbId?.takeIf { it.isNotBlank() }
  val cImdb = candidate.imdbId?.takeIf { it.isNotBlank() }
  if (tImdb != null && cImdb != null && !tImdb.equals(cImdb, true)) return false
  if (tTmdb != null && cTmdb == tTmdb) return true
  if (tImdb != null && cImdb.equals(tImdb, true)) return true

  val normTarget = normalizeTitleForMatching(target.title)
  val normCand = normalizeTitleForMatching(candidate.title)
  if (normTarget.isBlank() || normTarget != normCand) return false
  if (target.year != null && candidate.year != null && abs(target.year - candidate.year) > 1) return false
  return true
}

fun matchesTargetMediaRelease(release: TorrentRelease, target: MediaItem): Boolean {
  val tTmdb = target.tmdbId?.takeIf { it > 0 }
  val rTmdb = release.tmdbId?.takeIf { it > 0 }
  if (tTmdb != null && rTmdb != null) {
    return tTmdb == rTmdb
  }
  val tImdb = target.imdbId?.takeIf { it.isNotBlank() }
  val rImdb = release.imdbId?.takeIf { it.isNotBlank() }
  if (tImdb != null && rImdb != null) {
    return tImdb.equals(rImdb, true)
  }
  if (!release.titleId.isNullOrBlank() && release.titleId == target.id) {
    return true
  }

  val targetNorm = normalizeTitleForMatching(target.title)
  if (targetNorm.isBlank()) return false

  val mediaTitleNorm = release.mediaTitle?.let { normalizeTitleForMatching(it) }.orEmpty()
  if (mediaTitleNorm.isNotBlank()) {
    if (mediaTitleNorm != targetNorm) return false
    if (target.year != null) {
      val titleYear = Regex("(19\\d{2}|20[0-3]\\d)").find(release.mediaTitle.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
      if (titleYear != null && abs(titleYear - target.year) > 1) return false
    }
    return true
  }

  if (!release.hasExplicitReleaseName) return false

  val releaseNorm = normalizeTitleForMatching(release.releaseName)
  val paddedTarget = " $targetNorm "
  val paddedRelease = " $releaseNorm "
  if (!paddedRelease.contains(paddedTarget)) return false

  if (target.year != null) {
    val relYear = Regex("\\b(19\\d{2}|20[0-3]\\d)\\b").find(release.releaseName)?.groupValues?.get(1)?.toIntOrNull()
    if (relYear != null && abs(relYear - target.year) > 1) return false
  }
  return true
}

fun extractSeasonEpisodeFromName(name: String): Pair<Int?, Int?> {
  val se = Regex("\\bS(\\d{1,2})E(\\d{1,3})\\b", RegexOption.IGNORE_CASE).find(name)
  if (se != null) {
    return se.groupValues[1].toIntOrNull() to se.groupValues[2].toIntOrNull()
  }
  val alt = Regex("\\b(\\d{1,2})x(\\d{1,3})\\b", RegexOption.IGNORE_CASE).find(name)
  if (alt != null) {
    return alt.groupValues[1].toIntOrNull() to alt.groupValues[2].toIntOrNull()
  }
  val seasonOnly = Regex("\\bS(?:eason)?[ ._-]?(\\d{1,2})\\b", RegexOption.IGNORE_CASE).find(name)
  if (seasonOnly != null) {
    return seasonOnly.groupValues[1].toIntOrNull() to null
  }
  return null to null
}

fun matchesSeasonAndEpisode(
  release: TorrentRelease,
  seasonFilter: Int?,
  episodeFilter: Int?,
): Boolean {
  if (seasonFilter == null && episodeFilter == null) return true
  val (parsedSeason, parsedEpisode) = extractSeasonEpisodeFromName(release.releaseName)
  val s = release.seasonNumber ?: parsedSeason
  val e = release.episodeNumber ?: parsedEpisode
  if (seasonFilter != null && s != null && s != seasonFilter) return false
  if (episodeFilter != null) {
    if (e != null && e != episodeFilter) return false
  }
  return true
}

fun deduplicateTorrents(list: List<TorrentRelease>): List<TorrentRelease> {
  val map = linkedMapOf<String, TorrentRelease>()
  for (item in list) {
    val key =
      item.infoHash.lowercase().ifBlank {
        "${item.releaseName.lowercase()}|${item.sizeBytes}|${item.trackerSource.lowercase()}|${normalizeResolutionTag(item.resolution)}"
      }
    val existing = map[key]
    if (existing == null || item.qualityScore > existing.qualityScore ||
      (item.qualityScore == existing.qualityScore && item.seeders > existing.seeders)
    ) {
      map[key] = item
    }
  }
  return map.values.toList()
}

fun extractAvailableResolutions(torrents: List<TorrentRelease>): List<Pair<String, Int>> {
  val order = mapOf("2160p" to 0, "1440p" to 1, "1080p" to 2, "720p" to 3, "480p" to 4, "360p" to 5)
  return torrents
    .map { normalizeResolutionTag(it.resolution) }
    .filter { it.isNotBlank() }
    .groupingBy { it }
    .eachCount()
    .entries
    .sortedWith(compareBy({ order[it.key] ?: 99 }, { -it.value }))
    .map { it.key to it.value }
}

fun mergeFacetOptionsWithDefaults(
  defaults: List<String>,
  present: List<Pair<String, Int>>,
): List<Pair<String, Int>> {
  val map = present.toMap()
  val result = defaults.map { it to (map[it] ?: 0) }.toMutableList()
  present.forEach { (k, count) ->
    if (k !in defaults) result.add(k to count)
  }
  return result
}

fun extractTorrentFilterFacets(torrents: List<TorrentRelease>): TorrentFilterFacets {
  val resolutions = extractAvailableResolutions(torrents)
  val codecs =
    torrents
      .mapNotNull { it.videoCodec.takeIf { c -> c.isNotBlank() }?.let(::normalizeCodecTag) }
      .groupingBy { it }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .map { it.key to it.value }
  val hdr =
    torrents
      .mapNotNull { it.hdrFormat?.takeIf { h -> h.isNotBlank() } }
      .groupingBy { it }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .map { it.key to it.value }
  val sources =
    torrents
      .mapNotNull { it.trackerSource.takeIf { s -> s.isNotBlank() } }
      .groupingBy { it }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .map { it.key to it.value }
  val verifiedCount = torrents.count { it.trueSpecStatus == TrueSpecStatus.VERIFIED }
  return TorrentFilterFacets(resolutions, codecs, hdr, sources, verifiedCount)
}

fun filterAndSortTorrents(
  torrents: List<TorrentRelease>,
  filter: TorrentListFilterState,
): List<TorrentRelease> {
  val gb = 1024L * 1024L * 1024L
  val filtered =
    torrents.filter { rel ->
      val resOk =
        filter.resolutions.isEmpty() ||
          normalizeResolutionTag(rel.resolution) in filter.resolutions.map(::normalizeResolutionTag)
      val sizeOk =
        when {
          filter.customMinSizeGb != null || filter.customMaxSizeGb != null -> {
            val minB = ((filter.customMinSizeGb ?: 0f) * gb).toLong()
            val maxB = filter.customMaxSizeGb?.let { (it * gb).toLong() } ?: Long.MAX_VALUE
            rel.sizeBytes in minB..maxB
          }
          else ->
            when (filter.sizeFilter) {
              TorrentSizeFilter.ALL -> true
              TorrentSizeFilter.UNDER_2_GB -> rel.sizeBytes in 1L until (2L * gb)
              TorrentSizeFilter.GB_2_TO_10 -> rel.sizeBytes in (2L * gb)..(10L * gb)
              TorrentSizeFilter.GB_10_TO_20 -> rel.sizeBytes in (10L * gb + 1)..(20L * gb)
              TorrentSizeFilter.OVER_20_GB -> rel.sizeBytes > (20L * gb)
            }
        }
      val codecOk =
        filter.codecs.isEmpty() ||
          normalizeCodecTag(rel.videoCodec) in filter.codecs.map(::normalizeCodecTag)
      val hdrOk =
        filter.hdrFormats.isEmpty() ||
          (rel.hdrFormat != null && filter.hdrFormats.any { it.equals(rel.hdrFormat, true) })
      val sourceOk =
        filter.sources.isEmpty() ||
          filter.sources.any { it.equals(rel.trackerSource, true) }
      val verifiedOk = !filter.verifiedOnly || rel.trueSpecStatus == TrueSpecStatus.VERIFIED
      val seedersOk = rel.seeders >= filter.minSeeders
      val scoreOk = rel.qualityScore >= filter.minQualityScore
      val tokens =
        filter.keyword.trim()
          .split(Regex("\\s+"))
          .filter { it.isNotBlank() }
      val kwOk =
        tokens.isEmpty() ||
          run {
            val searchable =
              listOfNotNull(
                rel.releaseName,
                rel.trackerSource,
                rel.resolution,
                rel.videoCodec,
                rel.audioCodec,
                rel.audioChannels,
                rel.hdrFormat,
                rel.sourceType,
                rel.releaseGroup,
                rel.language,
                formatSeasonEpisodeLabel(rel.seasonNumber, rel.episodeNumber),
                rel.editionTags.joinToString(" ").takeIf { it.isNotBlank() },
              ).joinToString(" ")
            tokens.all { token -> searchable.contains(token, ignoreCase = true) }
          }

      resOk && sizeOk && codecOk && hdrOk && sourceOk && verifiedOk && seedersOk && scoreOk && kwOk
    }

  return when (filter.sortBy) {
    TorrentListSort.BEST_QUALITY ->
      filtered.sortedWith(compareByDescending<TorrentRelease> { it.qualityScore }.thenByDescending { it.seeders })
    TorrentListSort.MOST_SEEDED ->
      filtered.sortedWith(compareByDescending<TorrentRelease> { it.seeders }.thenByDescending { it.qualityScore })
    TorrentListSort.SMALLEST_SIZE ->
      filtered.sortedWith(compareBy<TorrentRelease> { if (it.sizeBytes <= 0L) Long.MAX_VALUE else it.sizeBytes }.thenByDescending { it.qualityScore })
    TorrentListSort.LARGEST_SIZE ->
      filtered.sortedWith(compareByDescending<TorrentRelease> { it.sizeBytes }.thenByDescending { it.qualityScore })
    TorrentListSort.EPISODE_ASC ->
      filtered.sortedWith(
        compareBy<TorrentRelease>(
          { (it.seasonNumber ?: extractSeasonEpisodeFromName(it.releaseName).first) ?: Int.MAX_VALUE },
          { (it.episodeNumber ?: extractSeasonEpisodeFromName(it.releaseName).second) ?: Int.MAX_VALUE },
          { -it.qualityScore },
        )
      )
    TorrentListSort.RELEASE_DATE_AND_EPISODE ->
      filtered.sortedWith(
        compareBy<TorrentRelease>(
          { if (it.releaseDate.isNullOrBlank()) 1 else 0 },
          { -(it.releaseDate?.let(::parseDateSortKey) ?: 0L) },
          { (it.seasonNumber ?: extractSeasonEpisodeFromName(it.releaseName).first) ?: Int.MAX_VALUE },
          { (it.episodeNumber ?: extractSeasonEpisodeFromName(it.releaseName).second) ?: Int.MAX_VALUE },
          { -it.qualityScore },
        )
      )
  }
}

fun filterAndSortTorrents(
  torrents: List<TorrentRelease>,
  selectedResolution: String?,
  sizeFilter: TorrentSizeFilter,
  sortBy: TorrentListSort,
): List<TorrentRelease> {
  return filterAndSortTorrents(
    torrents = torrents,
    filter =
      TorrentListFilterState(
        resolutions = selectedResolution?.let { setOf(it) }.orEmpty(),
        sizeFilter = sizeFilter,
        sortBy = sortBy,
      ),
  )
}

private fun parseDateSortKey(isoDate: String): Long {
  val digits = isoDate.filter { it.isDigit() }
  return digits.take(14).padEnd(14, '0').toLongOrNull() ?: 0L
}

fun computeSmartRecommendation(
  torrents: List<TorrentRelease>,
  prefs: UserPreferencesState,
): SmartMatchRecommendation? {
  if (torrents.isEmpty()) return null
  var best: TorrentRelease? = null
  var bestPoints = Int.MIN_VALUE
  var bestReasons = emptyList<String>()

  for (rel in torrents) {
    var points = rel.qualityScore
    val reasons = mutableListOf<String>()
    val res = normalizeResolutionTag(rel.resolution)
    if (res in prefs.preferredResolutions.map(::normalizeResolutionTag)) {
      points += 25
      reasons.add("Matches $res")
    }
    val codec = normalizeCodecTag(rel.videoCodec)
    if (codec in prefs.preferredCodecs.map(::normalizeCodecTag)) {
      points += 15
      reasons.add(codec)
    }
    if (prefs.preferTrueSpecVerified && rel.trueSpecStatus == TrueSpecStatus.VERIFIED) {
      points += 20
      reasons.add("TrueSpec Verified")
    }
    if (rel.seeders >= 20) points += 5
    if (points > bestPoints) {
      bestPoints = points
      best = rel
      bestReasons = reasons
    }
  }
  return best?.let { SmartMatchRecommendation(it, bestReasons) }
}

@Serializable
data class FavoriteMediaEntry(
  val id: String,
  val tmdbId: Int? = null,
  val imdbId: String? = null,
  val title: String,
  val originalTitle: String? = null,
  val year: Int? = null,
  val mediaTypeName: String = MediaType.MOVIE.name,
  val rating: Double? = null,
  val imdbRating: Double? = null,
  val voteCount: Int? = null,
  val runtimeMinutes: Int? = null,
  val releaseDate: String? = null,
  val tagline: String? = null,
  val status: String? = null,
  val originalLanguage: String? = null,
  val certification: String? = null,
  val numberOfSeasons: Int? = null,
  val numberOfEpisodes: Int? = null,
  val genres: List<String> = emptyList(),
  val posterUrl: String? = null,
  val backdropUrl: String? = null,
  val overview: String = "",
  val torrentCount: Int = 0,
  val contentUrl: String? = null,
  val collectionName: String? = null,
  val savedAtMs: Long = 0L,
)

fun MediaItem.toFavoriteEntry(savedAtMs: Long = System.currentTimeMillis()): FavoriteMediaEntry =
  FavoriteMediaEntry(
    id = id,
    tmdbId = tmdbId,
    imdbId = imdbId,
    title = title,
    originalTitle = originalTitle,
    year = year,
    mediaTypeName = mediaType.name,
    rating = rating,
    imdbRating = imdbRating,
    voteCount = voteCount,
    runtimeMinutes = runtimeMinutes,
    releaseDate = releaseDate,
    tagline = tagline,
    status = status,
    originalLanguage = originalLanguage,
    certification = certification,
    numberOfSeasons = numberOfSeasons,
    numberOfEpisodes = numberOfEpisodes,
    genres = genres,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    overview = overview,
    torrentCount = torrentCount,
    contentUrl = contentUrl,
    collectionName = collectionName,
    savedAtMs = savedAtMs,
  )

fun FavoriteMediaEntry.toMediaItem(): MediaItem =
  MediaItem(
    id = id,
    tmdbId = tmdbId,
    imdbId = imdbId,
    title = title,
    originalTitle = originalTitle,
    year = year,
    mediaType = MediaType.entries.find { it.name == mediaTypeName } ?: MediaType.MOVIE,
    rating = rating,
    imdbRating = imdbRating,
    voteCount = voteCount,
    runtimeMinutes = runtimeMinutes,
    releaseDate = releaseDate,
    tagline = tagline,
    status = status,
    originalLanguage = originalLanguage,
    certification = certification,
    numberOfSeasons = numberOfSeasons,
    numberOfEpisodes = numberOfEpisodes,
    genres = genres,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    overview = overview,
    torrentCount = torrentCount,
    contentUrl = contentUrl,
    collectionName = collectionName,
  )

fun isSameFavoriteMedia(a: MediaItem, b: MediaItem): Boolean {
  if (a.mediaType != MediaType.ALL && b.mediaType != MediaType.ALL && a.mediaType != b.mediaType) {
    return false
  }

  val imdbA = a.imdbId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
  val imdbB = b.imdbId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
  if (imdbA != null && imdbB != null) return imdbA == imdbB

  val tmdbA = a.tmdbId?.takeIf { it > 0 }
  val tmdbB = b.tmdbId?.takeIf { it > 0 }
  if (tmdbA != null && tmdbB != null) return tmdbA == tmdbB

  if (a.id.isNotBlank() && b.id.isNotBlank() && a.id == b.id) return true
  return matchesTargetMediaItem(a, b)
}
