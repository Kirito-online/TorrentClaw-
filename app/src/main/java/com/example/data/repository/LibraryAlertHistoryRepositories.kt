package com.example.data.repository

import android.content.Context
import com.example.data.local.ContentAlertDao
import com.example.data.local.ContentAlertEntity
import com.example.data.local.DownloadHistoryDao
import com.example.data.local.DownloadHistoryEntity
import com.example.data.local.SavedMediaDao
import com.example.data.local.SavedMediaEntity
import com.example.data.local.SavedTorrentDao
import com.example.data.local.SavedTorrentEntity
import com.example.data.local.SearchHistoryDao
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.DebridProvider
import com.example.data.model.MediaItem
import com.example.data.model.SearchFilter
import com.example.data.model.TorrentRelease
import com.example.data.remote.DebridService
import com.example.util.NotificationHelper
import com.example.widget.TorrentClawWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LibraryRepository(
  private val savedMediaDao: SavedMediaDao,
  private val savedTorrentDao: SavedTorrentDao,
) {
  val savedMediaFlow: Flow<List<SavedMediaEntity>> = savedMediaDao.observeAll()
  val savedTorrentsFlow: Flow<List<SavedTorrentEntity>> = savedTorrentDao.observeAll()

  fun observeIsSaved(id: String, category: String): Flow<Boolean> =
    savedMediaDao.observeIsSaved(id, category)

  fun observeIsTorrentSaved(infoHash: String): Flow<Boolean> =
    savedTorrentDao.observeIsSaved(infoHash)

  suspend fun toggleMediaCategory(item: MediaItem, category: String, currentlySaved: Boolean) {
    if (currentlySaved) {
      savedMediaDao.delete(item.id, category)
    } else {
      savedMediaDao.upsert(
        SavedMediaEntity(
          id = item.id,
          tmdbId = item.tmdbId,
          imdbId = item.imdbId,
          title = item.title,
          year = item.year,
          mediaType = item.mediaType.name,
          rating = item.rating,
          posterUrl = item.posterUrl,
          backdropUrl = item.backdropUrl,
          overview = item.overview,
          genresCsv = item.genres.joinToString(","),
          category = category,
          collectionName = item.collectionName,
        )
      )
    }
  }

  suspend fun toggleSavedTorrent(release: TorrentRelease, currentlySaved: Boolean) {
    if (currentlySaved) {
      savedTorrentDao.delete(release.infoHash)
    } else {
      savedTorrentDao.upsert(
        SavedTorrentEntity(
          infoHash = release.infoHash,
          releaseName = release.releaseName,
          mediaTitle = release.mediaTitle,
          posterUrl = release.posterUrl,
          resolution = release.resolution,
          sourceType = release.sourceType,
          videoCodec = release.videoCodec,
          audioCodec = release.audioCodec,
          hdrFormat = release.hdrFormat,
          sizeBytes = release.sizeBytes,
          seeders = release.seeders,
          leechers = release.leechers,
          trackerSource = release.trackerSource,
          qualityScore = release.qualityScore,
          trueSpecStatus = release.trueSpecStatus.name,
          magnetUrl = release.resolvedMagnetUrl,
        )
      )
    }
  }

  suspend fun clearAllLocalLibrary() {
    savedMediaDao.clearAll()
    savedTorrentDao.clearAll()
  }
}

class HistoryRepository(
  private val searchHistoryDao: SearchHistoryDao,
  private val downloadHistoryDao: DownloadHistoryDao,
) {
  val searchHistoryFlow: Flow<List<SearchHistoryEntity>> = searchHistoryDao.observeRecent()
  val downloadHistoryFlow: Flow<List<DownloadHistoryEntity>> = downloadHistoryDao.observeHistory()

  suspend fun recordSearch(query: String, mediaTypeFilter: String = "ALL") {
    val clean = query.trim()
    if (clean.isEmpty()) return
    searchHistoryDao.upsert(SearchHistoryEntity(query = clean, mediaTypeFilter = mediaTypeFilter))
  }

  suspend fun deleteSearchQuery(query: String) = searchHistoryDao.deleteByQuery(query)

  suspend fun clearSearchHistory() = searchHistoryDao.clearAll()

  suspend fun recordTorrentAction(release: TorrentRelease, actionType: String) {
    downloadHistoryDao.insert(
      DownloadHistoryEntity(
        infoHash = release.infoHash,
        releaseName = release.releaseName,
        mediaTitle = release.mediaTitle,
        posterUrl = release.posterUrl,
        resolution = release.resolution,
        qualityScore = release.qualityScore,
        trackerSource = release.trackerSource,
        actionType = actionType,
        magnetUrl = release.resolvedMagnetUrl,
      )
    )
  }

  suspend fun deleteDownloadHistoryItem(id: Long) = downloadHistoryDao.deleteById(id)

  suspend fun clearDownloadHistory() = downloadHistoryDao.clearAll()
}

class AlertRepository(
  private val context: Context,
  private val contentAlertDao: ContentAlertDao,
  private val mediaRepository: MediaRepository,
  private val authRepository: AuthRepository,
) {
  val alertsFlow: Flow<List<ContentAlertEntity>> = contentAlertDao.observeAlerts()

  fun observeIsFollowed(mediaId: String): Flow<Boolean> = contentAlertDao.observeIsFollowed(mediaId)

  suspend fun followTitle(
    item: MediaItem,
    preferredResolution: String,
    minQualityScore: Int,
  ): Result<Unit> =
    runCatching {
      val current = contentAlertDao.getAlertsSnapshot()
      val maxAllowed = authRepository.currentAccount.tier.maxAlerts
      if (current.none { it.mediaId == item.id } && current.size >= maxAllowed) {
        throw IllegalStateException(
          "Alert limit reached ($maxAllowed for ${authRepository.currentAccount.tier.label}). Upgrade or remove an alert."
        )
      }
      contentAlertDao.upsert(
        ContentAlertEntity(
          mediaId = item.id,
          tmdbId = item.tmdbId,
          imdbId = item.imdbId,
          title = item.title,
          mediaType = item.mediaType.name,
          posterUrl = item.posterUrl,
          enabled = true,
          preferredResolution = preferredResolution,
          minQualityScore = minQualityScore,
          lastKnownReleaseCount = item.torrentCount,
        )
      )
      TorrentClawWidgetProvider.requestUpdate(context)
    }

  suspend fun unfollowTitle(mediaId: String) {
    contentAlertDao.delete(mediaId)
    TorrentClawWidgetProvider.requestUpdate(context)
  }

  suspend fun setAlertEnabled(mediaId: String, enabled: Boolean) {
    contentAlertDao.setEnabled(mediaId, enabled)
    TorrentClawWidgetProvider.requestUpdate(context)
  }

  suspend fun updateAlertPreferences(
    alert: ContentAlertEntity,
    resolution: String,
    minScore: Int,
  ) {
    contentAlertDao.upsert(
      alert.copy(preferredResolution = resolution, minQualityScore = minScore)
    )
    TorrentClawWidgetProvider.requestUpdate(context)
  }

  suspend fun checkAlertsNow(notifyOnMatches: Boolean): Int =
    withContext(Dispatchers.IO) {
      val alerts = contentAlertDao.getAlertsSnapshot().filter { it.enabled }
      var totalNew = 0
      for (alert in alerts) {
        val result =
          runCatching {
            mediaRepository.searchPage(
              SearchFilter(
                query = alert.title,
                resolutions = setOf(alert.preferredResolution),
                minQualityScore = alert.minQualityScore,
              ),
              page = 1,
              limit = 20,
            )
          }.getOrNull() ?: continue

        val matchingReleases =
          result.torrents.size + result.mediaItems.sumOf { maxOf(it.torrentCount, it.torrents.size) }
        val delta = (matchingReleases - alert.lastKnownReleaseCount).coerceAtLeast(0)
        if (delta > 0) {
          totalNew += delta
          if (notifyOnMatches) {
            NotificationHelper.postReleaseAlert(
              context = context,
              mediaId = alert.mediaId,
              title = alert.title,
              matchSummary = "$delta new ${alert.preferredResolution} release(s) matched (score ≥ ${alert.minQualityScore})",
            )
          }
        }
        contentAlertDao.upsert(
          alert.copy(
            lastKnownReleaseCount = matchingReleases,
            newMatchesCount = if (delta > 0) delta else alert.newMatchesCount,
            lastCheckedAt = System.currentTimeMillis(),
          )
        )
      }
      TorrentClawWidgetProvider.requestUpdate(context)
      totalNew
    }
}

class DebridRepository(
  private val authRepository: AuthRepository,
  private val debridService: DebridService,
) {
  suspend fun sendToDebrid(provider: DebridProvider, magnetUri: String): Result<String> {
    val token =
      authRepository.getDebridToken(provider)
        ?: return Result.failure(IllegalStateException("${provider.displayName} is not connected."))
    return debridService.sendMagnetToDebrid(provider, token, magnetUri)
  }
}
