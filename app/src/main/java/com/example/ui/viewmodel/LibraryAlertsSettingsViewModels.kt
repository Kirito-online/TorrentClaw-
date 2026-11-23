package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.example.AppContainer
import com.example.core.preferences.InterfaceDensity
import com.example.core.preferences.ListingLayout
import com.example.core.preferences.MagnetAction
import com.example.core.preferences.PosterSize
import com.example.core.preferences.TorrentFileAction
import com.example.data.local.ContentAlertEntity
import com.example.data.local.DownloadHistoryEntity
import com.example.data.local.SavedMediaEntity
import com.example.data.local.SavedTorrentEntity
import com.example.data.model.DebridProvider
import com.example.data.model.SortOption
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecStatus
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import com.example.util.InstalledTorrentClient
import com.example.util.TorrentIntentHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

  val savedMedia: StateFlow<List<SavedMediaEntity>> =
    container.libraryRepository.savedMediaFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val savedTorrents: StateFlow<List<SavedTorrentEntity>> =
    container.libraryRepository.savedTorrentsFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val downloadHistory: StateFlow<List<DownloadHistoryEntity>> =
    container.historyRepository.downloadHistoryFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  fun removeSavedMedia(item: SavedMediaEntity) {
    viewModelScope.launch {
      container.libraryRepository.toggleMediaCategory(
        item =
          com.example.data.model.MediaItem(
            id = item.id,
            title = item.title,
          ),
        category = item.category,
        currentlySaved = true,
      )
    }
  }

  fun removeSavedTorrent(entity: SavedTorrentEntity) {
    viewModelScope.launch {
      container.libraryRepository.toggleSavedTorrent(entity.toDomain(), currentlySaved = true)
    }
  }

  fun deleteHistoryEntry(id: Long) {
    viewModelScope.launch { container.historyRepository.deleteDownloadHistoryItem(id) }
  }

  fun clearHistory() {
    viewModelScope.launch { container.historyRepository.clearDownloadHistory() }
  }

  fun SavedTorrentEntity.toDomain(): TorrentRelease =
    TorrentRelease(
      infoHash = infoHash,
      releaseName = releaseName,
      mediaTitle = mediaTitle,
      posterUrl = posterUrl,
      resolution = resolution,
      sourceType = sourceType,
      videoCodec = videoCodec,
      audioCodec = audioCodec,
      hdrFormat = hdrFormat,
      sizeBytes = sizeBytes,
      seeders = seeders,
      leechers = leechers,
      trackerSource = trackerSource,
      qualityScore = qualityScore,
      trueSpecStatus = TrueSpecStatus.entries.find { it.name == trueSpecStatus } ?: TrueSpecStatus.UNVERIFIED,
      magnetUrl = magnetUrl,
    )

  fun DownloadHistoryEntity.toDomain(): TorrentRelease =
    TorrentRelease(
      infoHash = infoHash,
      releaseName = releaseName,
      mediaTitle = mediaTitle,
      posterUrl = posterUrl,
      resolution = resolution,
      trackerSource = trackerSource,
      qualityScore = qualityScore,
      magnetUrl = magnetUrl,
    )

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(container) as T
  }
}

class AlertsViewModel(private val container: AppContainer) : ViewModel() {

  val alerts: StateFlow<List<ContentAlertEntity>> =
    container.alertRepository.alertsFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  private val _isChecking = MutableStateFlow(false)
  val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

  private val _lastCheckSummary = MutableStateFlow<String?>(null)
  val lastCheckSummary: StateFlow<String?> = _lastCheckSummary.asStateFlow()

  fun setEnabled(mediaId: String, enabled: Boolean) {
    viewModelScope.launch { container.alertRepository.setAlertEnabled(mediaId, enabled) }
  }

  fun deleteAlert(mediaId: String) {
    viewModelScope.launch { container.alertRepository.unfollowTitle(mediaId) }
  }

  fun updatePreferences(alert: ContentAlertEntity, resolution: String, minScore: Int) {
    viewModelScope.launch {
      container.alertRepository.updateAlertPreferences(alert, resolution, minScore)
    }
  }

  fun checkNow() {
    viewModelScope.launch {
      _isChecking.value = true
      val notify = container.preferences.preferencesFlow.first().newReleaseNotifications
      val matches = container.alertRepository.checkAlertsNow(notifyOnMatches = notify)
      _lastCheckSummary.value =
        if (matches > 0) "$matches new matching release(s) found"
        else "All followed titles checked — no new matches yet"
      _isChecking.value = false
    }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AlertsViewModel(container) as T
  }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

  private val _installedClients = MutableStateFlow<List<InstalledTorrentClient>>(emptyList())
  val installedClients: StateFlow<List<InstalledTorrentClient>> = _installedClients.asStateFlow()

  private val _debridStatusMessage = MutableStateFlow<String?>(null)
  val debridStatusMessage: StateFlow<String?> = _debridStatusMessage.asStateFlow()

  private val _cacheSizeLabel = MutableStateFlow("Calculating…")
  val cacheSizeLabel: StateFlow<String> = _cacheSizeLabel.asStateFlow()

  init {
    refreshInstalledClients()
    refreshCacheSize()
  }

  fun refreshInstalledClients() {
    _installedClients.value = TorrentIntentHelper.findInstalledTorrentClients(container.appContext)
  }

  fun refreshCacheSize() {
    viewModelScope.launch(Dispatchers.IO) {
      val dirSize = folderSize(container.appContext.cacheDir)
      _cacheSizeLabel.value = com.example.data.model.formatFileSize(dirSize)
    }
  }

  private fun folderSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
  }

  fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { container.preferences.setThemeMode(mode) }

  fun setAccentPalette(palette: AccentPalette) =
    viewModelScope.launch { container.preferences.setAccentPalette(palette) }

  fun setDensity(density: InterfaceDensity) =
    viewModelScope.launch { container.preferences.setDensity(density) }

  fun setPosterSize(size: PosterSize) =
    viewModelScope.launch { container.preferences.setPosterSize(size) }

  fun setListingLayout(layout: ListingLayout) =
    viewModelScope.launch { container.preferences.setDefaultListingLayout(layout) }

  fun setDefaultSort(sort: SortOption) =
    viewModelScope.launch { container.preferences.setDefaultSort(sort) }

  fun togglePreferredResolution(res: String, current: Set<String>) {
    viewModelScope.launch {
      val next = if (res in current) current - res else current + res
      container.preferences.setPreferredResolutions(next)
    }
  }

  fun setAutocompleteEnabled(enabled: Boolean) =
    viewModelScope.launch { container.preferences.setAutocompleteEnabled(enabled) }

  fun setSaveSearchHistory(enabled: Boolean) =
    viewModelScope.launch { container.preferences.setSaveSearchHistory(enabled) }

  fun setPreferredClient(client: InstalledTorrentClient?) =
    viewModelScope.launch {
      container.preferences.setPreferredTorrentClient(client?.packageName, client?.label)
    }

  fun setDefaultMagnetAction(action: MagnetAction) =
    viewModelScope.launch { container.preferences.setDefaultMagnetAction(action) }

  fun setDefaultTorrentFileAction(action: TorrentFileAction) =
    viewModelScope.launch { container.preferences.setDefaultTorrentFileAction(action) }

  fun setNotificationsEnabled(enabled: Boolean) =
    viewModelScope.launch { container.preferences.setNotificationsEnabled(enabled) }

  fun setNewReleaseNotifications(enabled: Boolean) =
    viewModelScope.launch { container.preferences.setNewReleaseNotifications(enabled) }

  fun setTrueSpecNotifications(enabled: Boolean) =
    viewModelScope.launch { container.preferences.setTrueSpecNotifications(enabled) }

  fun setAlertDefaultQuality(quality: String) =
    viewModelScope.launch { container.preferences.setAlertDefaultQuality(quality) }

  fun connectDebrid(provider: DebridProvider, token: String) {
    viewModelScope.launch {
      _debridStatusMessage.value = "Verifying ${provider.displayName}…"
      val res = container.authRepository.connectDebridProvider(provider, token)
      _debridStatusMessage.value =
        res.fold(
          onSuccess = { "${provider.displayName} connected (${it.username})" },
          onFailure = { it.localizedMessage ?: "Failed to verify token" },
        )
    }
  }

  fun disconnectDebrid(provider: DebridProvider) {
    container.authRepository.disconnectDebridProvider(provider)
    _debridStatusMessage.value = "${provider.displayName} disconnected"
  }

  @OptIn(ExperimentalCoilApi::class)
  fun clearImageCache() {
    viewModelScope.launch(Dispatchers.IO) {
      val loader = Coil.imageLoader(container.appContext)
      loader.memoryCache?.clear()
      loader.diskCache?.clear()
      refreshCacheSize()
    }
  }

  fun clearTempFiles() {
    viewModelScope.launch(Dispatchers.IO) {
      File(container.appContext.cacheDir, "torrents").deleteRecursively()
      File(container.appContext.cacheDir, "http_cache").deleteRecursively()
      refreshCacheSize()
    }
  }

  fun clearLocalData() {
    viewModelScope.launch {
      container.libraryRepository.clearAllLocalLibrary()
      container.historyRepository.clearSearchHistory()
      container.historyRepository.clearDownloadHistory()
      container.database.cachedDiscoveryDao().clearAll()
      clearTempFiles()
    }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
  }
}
