package com.example.data.model

import com.example.core.preferences.UserPreferencesState
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

enum class MediaType(val apiValue: String, val label: String) {
  ALL("", "All"),
  MOVIE("movie", "Movies"),
  SHOW("show", "TV Shows"),
}

enum class SortOption(val apiValue: String, val label: String) {
  QUALITY_SCORE("quality_score", "Best Quality"),
  SEEDERS("seeders", "Highest Seeders"),
  SIZE_ASC("size_asc", "Lowest File Size"),
  RATING("rating", "Highest Rating"),
  NEWEST("added", "Newest Releases"),
  RELEVANCE("relevance", "Most Relevant"),
}

enum class TorrentSizeFilter(val label: String, val minGb: Float? = null, val maxGb: Float? = null) {
  ALL("Any Size", null, null),
  UNDER_2_GB("< 2 GB", null, 2f),
  FROM_2_TO_8_GB("2–8 GB", 2f, 8f),
  FROM_8_TO_20_GB("8–20 GB", 8f, 20f),
  OVER_20_GB("> 20 GB", 20f, null),
}

enum class TorrentListSort(val label: String) {
  BEST_QUALITY("Best Quality"),
  MOST_SEEDED("Most Seeded"),
  SMALLEST_SIZE("Smallest Size"),
  LARGEST_SIZE("Largest Size"),
}

data class TorrentListFilterState(
  val resolutions: Set<String> = emptySet(),
  val sizePreset: TorrentSizeFilter = TorrentSizeFilter.ALL,
  val customMinSizeGb: Float? = null,
  val customMaxSizeGb: Float? = null,
  val sources: Set<String> = emptySet(),
  val codecs: Set<String> = emptySet(),
  val hdrFormats: Set<String> = emptySet(),
  val audioFormats: Set<String> = emptySet(),
  val minSeeders: Int? = null,
  val verifiedOnly: Boolean = false,
  val debridCachedOnly: Boolean = false,
  val keyword: String = "",
  val sortBy: TorrentListSort = TorrentListSort.BEST_QUALITY,
) {
  val hasCustomSizeRange: Boolean
    get() = customMinSizeGb != null || customMaxSizeGb != null

  val hasActiveFilters: Boolean
    get() =
      resolutions.isNotEmpty() ||
        sizePreset != TorrentSizeFilter.ALL ||
        hasCustomSizeRange ||
        sources.isNotEmpty() ||
        codecs.isNotEmpty() ||
        hdrFormats.isNotEmpty() ||
        audioFormats.isNotEmpty() ||
        (minSeeders != null && minSeeders > 0) ||
        verifiedOnly ||
        debridCachedOnly ||
        keyword.isNotBlank()

  val activeFilterCount: Int
    get() =
      resolutions.size +
        (if (sizePreset != TorrentSizeFilter.ALL || hasCustomSizeRange) 1 else 0) +
        sources.size +
        codecs.size +
        hdrFormats.size +
        audioFormats.size +
        (if (minSeeders != null && minSeeders > 0) 1 else 0) +
        (if (verifiedOnly) 1 else 0) +
        (if (debridCachedOnly) 1 else 0) +
        (if (keyword.isNotBlank()) 1 else 0)
}

data class TorrentFilterFacets(
  val resolutions: List<Pair<String, Int>> = emptyList(),
  val sources: List<Pair<String, Int>> = emptyList(),
  val codecs: List<Pair<String, Int>> = emptyList(),
  val hdrFormats: List<Pair<String, Int>> = emptyList(),
  val audioFormats: List<Pair<String, Int>> = emptyList(),
  val maxSizeGb: Float = 50f,
  val verifiedCount: Int = 0,
  val debridCachedCount: Int = 0,
)

enum class TrueSpecStatus(val label: String) {
  VERIFIED("TrueSpec Verified"),
  PENDING("Scan Queued"),
  WARNING("Verification Warning"),
  SUSPICIOUS("Suspicious Release"),
  UNVERIFIED("Filename Estimated"),
}

enum class AccountTier(
  val label: String,
  val requestsPerMinute: Int,
  val dailyLimit: Int,
  val maxAlerts: Int,
) {
  ANONYMOUS("Anonymous", 30, 300, 10),
  FREE("Free Account", 120, 1000, 25),
  PRO("TorrentClaw Pro", 1000, 10000, 500),
}

enum class DebridProvider(val displayName: String, val baseUrl: String) {
  REAL_DEBRID("Real-Debrid", "https://api.real-debrid.com/rest/1.0/"),
  TORBOX("TorBox", "https://api.torbox.app/v1/api/"),
  ALLDEBRID("AllDebrid", "https://api.alldebrid.com/v4/"),
  PREMIUMIZE("Premiumize", "https://www.premiumize.me/api/"),
}

data class UserAccount(
  val isSignedIn: Boolean = false,
  val username: String = "Guest",
  val email: String? = null,
  val tier: AccountTier = AccountTier.ANONYMOUS,
  val apiKeyMasked: String? = null,
  val subscriptionStatus: String = "Anonymous (30 req/min)",
  val rateLimitRemaining: Int? = null,
  val rateLimitTotal: Int? = null,
  val stremioAddonConfigured: Boolean = false,
  val torznabEnabled: Boolean = false,
)

data class DebridAccount(
  val provider: DebridProvider,
  val isConnected: Boolean = false,
  val username: String? = null,
  val planLabel: String? = null,
  val expiresAt: String? = null,
)

data class SearchFilter(
  val query: String = "",
  val mediaType: MediaType = MediaType.ALL,
  val yearMin: Int? = null,
  val yearMax: Int? = null,
  val genre: String? = null,
  val minRating: Float? = null,
  val resolutions: Set<String> = emptySet(),
  val sourceQualities: Set<String> = emptySet(),
  val codecs: Set<String> = emptySet(),
  val audioFormats: Set<String> = emptySet(),
  val hdrFormats: Set<String> = emptySet(),
  val languages: Set<String> = emptySet(),
  val season: Int? = null,
  val episode: Int? = null,
  val minSeeders: Int? = null,
  val maxSizeGb: Float? = null,
  val minQualityScore: Int? = null,
  val sortBy: SortOption = SortOption.QUALITY_SCORE,
) {
  val activeFilterCount: Int
    get() =
      listOfNotNull(
          if (mediaType != MediaType.ALL) 1 else null,
          yearMin,
          yearMax,
          genre?.takeIf { it.isNotBlank() },
          minRating,
          season,
          episode,
          minSeeders,
          maxSizeGb,
          minQualityScore,
        )
        .size +
        resolutions.size +
        sourceQualities.size +
        codecs.size +
        audioFormats.size +
        hdrFormats.size +
        languages.size
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
  val runtimeMinutes: Int? = null,
  val genres: List<String> = emptyList(),
  val overview: String = "",
  val posterUrl: String? = null,
  val backdropUrl: String? = null,
  val torrentCount: Int = 0,
  val bestQualityScore: Int? = null,
  val bestResolution: String? = null,
  val seasons: List<Int> = emptyList(),
  val episodesPerSeason: Map<Int, List<EpisodeInfo>> = emptyMap(),
  val collectionName: String? = null,
  val collectionId: String? = null,
  val torrents: List<TorrentRelease> = emptyList(),
) {
  val hasTorrents: Boolean
    get() = torrentCount > 0 || torrents.isNotEmpty()
}

data class EpisodeInfo(
  val season: Int,
  val episode: Int,
  val title: String? = null,
  val airDate: String? = null,
  val torrentCount: Int = 0,
)

data class TorrentRelease(
  val infoHash: String,
  val releaseName: String,
  val titleId: String? = null,
  val mediaTitle: String? = null,
  val posterUrl: String? = null,
  val resolution: String = "Unknown",
  val sourceType: String? = null,
  val videoCodec: String? = null,
  val audioCodec: String? = null,
  val audioChannels: String? = null,
  val hdrFormat: String? = null,
  val languages: List<String> = emptyList(),
  val subtitles: List<String> = emptyList(),
  val sizeBytes: Long = 0L,
  val seeders: Int = 0,
  val leechers: Int = 0,
  val trackerSource: String = "TorrentClaw",
  val indexedAt: String? = null,
  val qualityScore: Int = 0,
  val trueSpecStatus: TrueSpecStatus = TrueSpecStatus.UNVERIFIED,
  val trueSpecReport: TrueSpecReport? = null,
  val magnetUrl: String? = null,
  val torrentFileUrl: String? = null,
  val season: Int? = null,
  val episode: Int? = null,
  val debridCached: Boolean = false,
  val debridProvider: DebridProvider? = null,
  val tmdbId: Int? = null,
  val imdbId: String? = null,
  val mediaYear: Int? = null,
  val hasExplicitReleaseName: Boolean = true,
) {
  val resolvedMagnetUrl: String
    get() = magnetUrl?.takeIf { it.startsWith("magnet:") } ?: buildMagnetUri(infoHash, releaseName)
}

data class TrueSpecReport(
  val status: TrueSpecStatus,
  val verifiedResolution: String? = null,
  val verifiedVideoCodec: String? = null,
  val verifiedBitDepth: String? = null,
  val verifiedAudioTracks: List<String> = emptyList(),
  val verifiedSubtitles: List<String> = emptyList(),
  val primaryFileName: String? = null,
  val fileCount: Int? = null,
  val malwareScanClean: Boolean? = null,
  val warnings: List<String> = emptyList(),
  val mismatchReasons: List<String> = emptyList(),
  val scannedAt: String? = null,
)

data class CreditPerson(
  val id: String,
  val name: String,
  val role: String,
  val department: String,
  val profileUrl: String? = null,
)

data class WatchProvider(
  val providerName: String,
  val logoUrl: String? = null,
  val type: String = "stream", // stream, rent, buy
  val link: String? = null,
)

data class CollectionSummary(
  val id: String,
  val name: String,
  val overview: String? = null,
  val posterUrl: String? = null,
  val backdropUrl: String? = null,
  val itemCount: Int = 0,
)

data class PlatformStats(
  val totalTitles: Long = 0,
  val totalTorrents: Long = 0,
  val indexedSources: Int = 30,
  val trueSpecVerifiedCount: Long = 0,
)

data class AutocompleteSuggestion(
  val id: String,
  val title: String,
  val year: Int? = null,
  val mediaType: MediaType = MediaType.MOVIE,
  val posterUrl: String? = null,
  val tmdbId: Int? = null,
  val imdbId: String? = null,
)

sealed class ApiHealthState {
  data object Connected : ApiHealthState()

  data class RateLimited(val retryAfterSeconds: Int) : ApiHealthState()

  data class OfflineCached(val message: String) : ApiHealthState()

  data class Error(val message: String, val isRetryable: Boolean = true) : ApiHealthState()
}

data class SmartRecommendation(
  val release: TorrentRelease,
  val matchScore: Int,
  val reasons: List<String>,
)

fun formatFileSize(bytes: Long): String {
  if (bytes <= 0L) return "Unknown size"
  val kb = 1024.0
  val mb = kb * 1024.0
  val gb = mb * 1024.0
  return when {
    bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
    bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
    else -> String.format(Locale.US, "%.0f KB", bytes / kb)
  }
}

fun buildMagnetUri(infoHash: String, displayName: String): String {
  val cleanHash = infoHash.trim()
  if (cleanHash.isBlank()) return ""
  val encodedName = URLEncoder.encode(displayName, "UTF-8")
  val trackers =
    listOf(
      "udp://tracker.opentrackr.org:1337/announce",
      "udp://open.stealth.si:80/announce",
      "udp://tracker.torrent.eu.org:451/announce",
      "udp://exodus.desync.com:6969/announce",
    )
  val trackerQuery = trackers.joinToString("&") { "tr=${URLEncoder.encode(it, "UTF-8")}" }
  return "magnet:?xt=urn:btih:$cleanHash&dn=$encodedName&$trackerQuery"
}

fun computeSmartRecommendation(
  releases: List<TorrentRelease>,
  prefs: UserPreferencesState,
): SmartRecommendation? {
  if (releases.isEmpty()) return null
  val candidates =
    releases.filter { it.trueSpecStatus != TrueSpecStatus.SUSPICIOUS }.ifEmpty { releases }
  val scored =
    candidates.map { rel ->
      var score = rel.qualityScore
      val reasons = mutableListOf<String>()
      if (prefs.preferredResolutions.any { it.equals(rel.resolution, ignoreCase = true) }) {
        score += 18
        reasons += "Matches ${rel.resolution}"
      }
      if (
        rel.videoCodec != null &&
          prefs.preferredCodecs.any { rel.videoCodec.contains(it, ignoreCase = true) }
      ) {
        score += 12
        reasons += rel.videoCodec
      }
      if (prefs.preferTrueSpecVerified && rel.trueSpecStatus == TrueSpecStatus.VERIFIED) {
        score += 15
        reasons += "TrueSpec Verified"
      }
      if (rel.debridCached) {
        score += 10
        reasons += "Debrid Cached"
      }
      if (rel.seeders >= 10) {
        score += 5
      } else if (rel.seeders == 0) {
        score -= 20
      }
      if (reasons.isEmpty()) {
        reasons += "Score ${rel.qualityScore}/100"
      }
      SmartRecommendation(
        release = rel,
        matchScore = score.coerceIn(0, 100),
        reasons = reasons.take(3),
      )
    }
  return scored.maxByOrNull { it.matchScore }
}

fun deduplicateTorrents(releases: List<TorrentRelease>): List<TorrentRelease> {
  val seen = LinkedHashMap<String, TorrentRelease>()
  for (item in releases) {
    val key = item.infoHash.lowercase(Locale.US).ifBlank { item.releaseName.lowercase(Locale.US) }
    val existing = seen[key]
    if (existing == null || item.qualityScore > existing.qualityScore) {
      seen[key] = item
    }
  }
  return seen.values.toList()
}

fun parseImdbOrTmdbUrl(input: String): ParsedMediaLookup? {
  val trimmed = input.trim()
  val imdbRegex = Regex("""(?:imdb\.com/title/)?(tt\d{7,10})""", RegexOption.IGNORE_CASE)
  imdbRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { imdbId ->
    return ParsedMediaLookup(imdbId = imdbId.lowercase(Locale.US))
  }
  val tmdbRegex =
    Regex("""themoviedb\.org/(movie|tv)/(\d+)""", RegexOption.IGNORE_CASE)
  tmdbRegex.find(trimmed)?.let { match ->
    val type = if (match.groupValues[1].equals("tv", true)) MediaType.SHOW else MediaType.MOVIE
    val id = match.groupValues[2].toIntOrNull()
    if (id != null) return ParsedMediaLookup(tmdbId = id, mediaType = type)
  }
  return null
}

data class ParsedMediaLookup(
  val imdbId: String? = null,
  val tmdbId: Int? = null,
  val mediaType: MediaType? = null,
)

private fun resolutionOrder(resolution: String): Int {
  val lower = resolution.lowercase(Locale.US)
  return when {
    "2160" in lower || "4k" in lower || "uhd" in lower -> 0
    "1080" in lower -> 1
    "720" in lower -> 2
    "480" in lower -> 3
    else -> 4
  }
}

fun normalizeResolutionLabel(resolution: String): String {
  val lower = resolution.lowercase(Locale.US).trim()
  return when {
    "2160" in lower || "4k" in lower || "uhd" in lower -> "2160p"
    "1080" in lower -> "1080p"
    "720" in lower -> "720p"
    "480" in lower -> "480p"
    lower.isBlank() || lower == "unknown" -> "SD"
    else -> resolution.trim()
  }
}

fun normalizeCodecGroup(raw: String?): String? {
  val lower = raw?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() } ?: return null
  return when {
    "x265" in lower || "h265" in lower || "h.265" in lower || "hevc" in lower -> "HEVC"
    "x264" in lower || "h264" in lower || "h.264" in lower || "avc" in lower -> "H.264"
    "av1" in lower -> "AV1"
    "xvid" in lower || "divx" in lower -> "XviD"
    else -> raw.trim()
  }
}

fun normalizeSourceGroup(raw: String?): String? {
  val lower = raw?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() } ?: return null
  return when {
    "remux" in lower -> "REMUX"
    "bluray" in lower || "blu-ray" in lower || "bdrip" in lower || "brrip" in lower -> "BluRay"
    "web-dl" in lower || "webdl" in lower -> "WEB-DL"
    "webrip" in lower -> "WEBRip"
    "hdtv" in lower -> "HDTV"
    "cam" in lower || "telesync" in lower || lower == "ts" -> "CAM/TS"
    else -> raw.trim()
  }
}

fun normalizeAudioGroup(raw: String?): String? {
  val lower = raw?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() } ?: return null
  return when {
    "atmos" in lower -> "Atmos"
    "truehd" in lower -> "TrueHD"
    "dts-hd" in lower || "dts:x" in lower -> "DTS-HD"
    "dts" in lower -> "DTS"
    "eac3" in lower || "ddp" in lower || "dd+" in lower || "e-ac-3" in lower -> "DD+"
    "ac3" in lower || "dd5" in lower -> "AC3"
    "flac" in lower -> "FLAC"
    "opus" in lower -> "OPUS"
    "aac" in lower -> "AAC"
    else -> raw.trim()
  }
}

fun extractAvailableResolutions(torrents: List<TorrentRelease>): List<Pair<String, Int>> {
  return torrents
    .groupingBy { normalizeResolutionLabel(it.resolution) }
    .eachCount()
    .entries
    .sortedWith(compareBy<Map.Entry<String, Int>> { resolutionOrder(it.key) }.thenByDescending { it.value })
    .map { it.key to it.value }
}

fun extractTorrentFilterFacets(torrents: List<TorrentRelease>): TorrentFilterFacets {
  val gb = 1024.0 * 1024.0 * 1024.0
  val maxBytes = torrents.maxOfOrNull { it.sizeBytes } ?: 0L
  val rawMaxGb = (maxBytes / gb).toFloat()
  val ceilingGb = maxOf(10f, kotlin.math.ceil(rawMaxGb.toDouble()).toFloat())

  return TorrentFilterFacets(
    resolutions = extractAvailableResolutions(torrents),
    sources =
      torrents
        .mapNotNull { normalizeSourceGroup(it.sourceType) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value },
    codecs =
      torrents
        .mapNotNull { normalizeCodecGroup(it.videoCodec) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value },
    hdrFormats =
      torrents
        .mapNotNull { it.hdrFormat?.trim()?.takeIf { h -> h.isNotBlank() } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value },
    audioFormats =
      torrents
        .mapNotNull { normalizeAudioGroup(it.audioCodec) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value },
    maxSizeGb = ceilingGb,
    verifiedCount = torrents.count { it.trueSpecStatus == TrueSpecStatus.VERIFIED },
    debridCachedCount = torrents.count { it.debridCached },
  )
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
        sizePreset = sizeFilter,
        sortBy = sortBy,
      ),
  )
}

fun filterAndSortTorrents(
  torrents: List<TorrentRelease>,
  filter: TorrentListFilterState,
): List<TorrentRelease> {
  val gb = 1024.0 * 1024.0 * 1024.0
  val effectiveMinGb = filter.customMinSizeGb ?: filter.sizePreset.minGb
  val effectiveMaxGb = filter.customMaxSizeGb ?: filter.sizePreset.maxGb
  val kw = filter.keyword.trim().lowercase(Locale.US)

  val filtered =
    torrents.filter { t ->
      val resOk =
        filter.resolutions.isEmpty() ||
          filter.resolutions.any {
            normalizeResolutionLabel(t.resolution).equals(normalizeResolutionLabel(it), ignoreCase = true)
          }

      val sizeOk =
        if (effectiveMinGb == null && effectiveMaxGb == null) {
          true
        } else if (t.sizeBytes <= 0L) {
          false
        } else {
          val minBytes = effectiveMinGb?.let { (it * gb).toLong() } ?: 1L
          val maxBytes = effectiveMaxGb?.let { (it * gb).toLong() } ?: Long.MAX_VALUE
          t.sizeBytes in minBytes..maxBytes
        }

      val sourceOk =
        filter.sources.isEmpty() ||
          normalizeSourceGroup(t.sourceType)?.let { grp ->
            filter.sources.any { it.equals(grp, ignoreCase = true) }
          } == true

      val codecOk =
        filter.codecs.isEmpty() ||
          normalizeCodecGroup(t.videoCodec)?.let { grp ->
            filter.codecs.any { it.equals(grp, ignoreCase = true) }
          } == true

      val hdrOk =
        filter.hdrFormats.isEmpty() ||
          t.hdrFormat?.let { h ->
            filter.hdrFormats.any { h.contains(it, ignoreCase = true) }
          } == true

      val audioOk =
        filter.audioFormats.isEmpty() ||
          normalizeAudioGroup(t.audioCodec)?.let { grp ->
            filter.audioFormats.any { it.equals(grp, ignoreCase = true) }
          } == true

      val seedersOk = filter.minSeeders == null || t.seeders >= filter.minSeeders
      val verifiedOk = !filter.verifiedOnly || t.trueSpecStatus == TrueSpecStatus.VERIFIED
      val debridOk = !filter.debridCachedOnly || t.debridCached
      val keywordOk =
        kw.isEmpty() ||
          t.releaseName.lowercase(Locale.US).contains(kw) ||
          t.trackerSource.lowercase(Locale.US).contains(kw) ||
          (t.videoCodec?.lowercase(Locale.US)?.contains(kw) == true) ||
          (t.sourceType?.lowercase(Locale.US)?.contains(kw) == true) ||
          (t.audioCodec?.lowercase(Locale.US)?.contains(kw) == true) ||
          (t.hdrFormat?.lowercase(Locale.US)?.contains(kw) == true)

      resOk && sizeOk && sourceOk && codecOk && hdrOk && audioOk && seedersOk && verifiedOk && debridOk && keywordOk
    }

  return when (filter.sortBy) {
    TorrentListSort.BEST_QUALITY ->
      filtered.sortedWith(compareByDescending<TorrentRelease> { it.qualityScore }.thenByDescending { it.seeders })
    TorrentListSort.MOST_SEEDED ->
      filtered.sortedWith(compareByDescending<TorrentRelease> { it.seeders }.thenByDescending { it.qualityScore })
    TorrentListSort.SMALLEST_SIZE ->
      filtered.sortedWith(
        compareBy<TorrentRelease> { if (it.sizeBytes > 0L) 0 else 1 }
          .thenBy { it.sizeBytes }
          .thenByDescending { it.qualityScore }
      )
    TorrentListSort.LARGEST_SIZE ->
      filtered.sortedWith(compareByDescending<TorrentRelease> { it.sizeBytes }.thenByDescending { it.qualityScore })
  }
}

private fun normalizeTitleForMatch(raw: String): String {
  return raw
    .lowercase(Locale.US)
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
}

private fun titleTokensForMatch(title: String): List<String> {
  return normalizeTitleForMatch(title).split(' ').filter { it.isNotBlank() }
}

private fun titlesMatchStrictly(candidateTitle: String, target: MediaItem): Boolean {
  val candNorm = normalizeTitleForMatch(candidateTitle)
  if (candNorm.isBlank()) return false
  val targets =
    listOfNotNull(target.title, target.originalTitle)
      .map(::normalizeTitleForMatch)
      .filter { it.isNotBlank() }
  return targets.any { it == candNorm }
}

private fun extractYearsFromText(text: String): List<Int> {
  return Regex("""\b(19\d{2}|20\d{2})\b""")
    .findAll(text)
    .mapNotNull { it.groupValues[1].toIntOrNull() }
    .filter { it != 2160 && it != 1080 }
    .toList()
}

fun matchesTargetMediaItem(candidate: MediaItem, target: MediaItem): Boolean {
  if (candidate.tmdbId != null && target.tmdbId != null) {
    return candidate.tmdbId == target.tmdbId
  }
  if (!candidate.imdbId.isNullOrBlank() && !target.imdbId.isNullOrBlank()) {
    return candidate.imdbId.equals(target.imdbId, ignoreCase = true)
  }
  if (candidate.id.isNotBlank() && target.id.isNotBlank() && candidate.id == target.id) {
    return true
  }
  if (candidate.mediaType != target.mediaType) return false
  if (!titlesMatchStrictly(candidate.title, target)) return false
  if (candidate.year != null && target.year != null && kotlin.math.abs(candidate.year - target.year) > 1) {
    return false
  }
  return true
}

fun matchesExplicitMediaIdentifiers(release: TorrentRelease, target: MediaItem): Boolean {
  if (release.tmdbId != null && target.tmdbId != null) {
    return release.tmdbId == target.tmdbId
  }
  if (!release.imdbId.isNullOrBlank() && !target.imdbId.isNullOrBlank()) {
    return release.imdbId.equals(target.imdbId, ignoreCase = true)
  }
  if (!release.titleId.isNullOrBlank() && target.id.isNotBlank()) {
    val matchesId =
      release.titleId == target.id ||
        (target.tmdbId != null && release.titleId == target.tmdbId.toString()) ||
        (target.imdbId != null && release.titleId.equals(target.imdbId, ignoreCase = true))
    if (!matchesId) return false
  }
  if (!release.mediaTitle.isNullOrBlank() && !titlesMatchStrictly(release.mediaTitle, target)) {
    return false
  }
  if (release.mediaYear != null && target.year != null && kotlin.math.abs(release.mediaYear - target.year) > 1) {
    return false
  }
  return true
}

fun matchesTargetMediaRelease(release: TorrentRelease, target: MediaItem): Boolean {
  if (!matchesExplicitMediaIdentifiers(release, target)) return false

  val idConfirmed =
    (release.tmdbId != null && release.tmdbId == target.tmdbId) ||
      (!release.imdbId.isNullOrBlank() && release.imdbId.equals(target.imdbId, ignoreCase = true)) ||
      (!release.titleId.isNullOrBlank() && release.titleId == target.id)
  if (idConfirmed) return true

  if (!release.mediaTitle.isNullOrBlank() && titlesMatchStrictly(release.mediaTitle, target)) {
    return true
  }

  if (!release.hasExplicitReleaseName) {
    return false
  }

  val candidateTitles = listOfNotNull(target.title, target.originalTitle).filter { it.isNotBlank() }
  val releaseNorm = normalizeTitleForMatch(release.releaseName)
  val releaseWords = releaseNorm.split(' ').filter { it.isNotBlank() }
  if (releaseWords.isEmpty()) return false

  val titleMatched =
    candidateTitles.any { titleCandidate ->
      val titleNorm = normalizeTitleForMatch(titleCandidate)
      val titleTokens = titleTokensForMatch(titleCandidate)
      if (titleTokens.isEmpty()) {
        false
      } else {
        // Require the full normalized title phrase at the start or as a contiguous token sequence,
        // or all tokens when non-trivial so unrelated movies with one overlapping word don't leak in.
        val phraseHit =
          releaseNorm == titleNorm ||
            releaseNorm.startsWith("$titleNorm ") ||
            " $titleNorm " in " $releaseNorm "
        val orderedTokensHit =
          if (titleTokens.size >= 2) {
            var idx = 0
            for (word in releaseWords) {
              if (word == titleTokens[idx]) {
                idx++
                if (idx == titleTokens.size) break
              }
            }
            idx == titleTokens.size && releaseWords.indexOf(titleTokens.first()) <= 2
          } else {
            val single = titleTokens.first()
            releaseWords.firstOrNull() == single || (single.length >= 5 && single in releaseWords)
          }
        phraseHit || orderedTokensHit
      }
    }

  if (!titleMatched) return false

  if (target.year != null) {
    val yearsInName = extractYearsFromText(release.releaseName)
    if (yearsInName.isNotEmpty() && yearsInName.none { kotlin.math.abs(it - target.year) <= 1 }) {
      return false
    }
  }
  return true
}

fun matchesSeasonAndEpisode(
  release: TorrentRelease,
  seasonFilter: Int?,
  episodeFilter: Int?,
): Boolean {
  if (seasonFilter == null && episodeFilter == null) return true

  val sxeRegex = Regex("""(?:^|[^a-z0-9])s(\d{1,2})(?:e(\d{1,3}))?(?=[^a-z0-9]|$)""", RegexOption.IGNORE_CASE)
  val altRegex = Regex("""\b(\d{1,2})x(\d{1,3})\b""", RegexOption.IGNORE_CASE)
  val sxeMatch = sxeRegex.find(release.releaseName)
  val altMatch = altRegex.find(release.releaseName)

  val parsedSeason = release.season ?: sxeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: altMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
  val parsedEpisode = release.episode ?: sxeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: altMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

  if (seasonFilter != null && parsedSeason != null && parsedSeason != seasonFilter) {
    return false
  }
  if (episodeFilter != null) {
    if (parsedEpisode != null) {
      return parsedEpisode == episodeFilter
    }
    // Keep season packs if season matches, exclude only if another episode was explicitly tagged
    if (seasonFilter != null && parsedSeason != null && parsedSeason != seasonFilter) {
      return false
    }
  }
  return true
}
