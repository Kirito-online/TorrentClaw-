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
