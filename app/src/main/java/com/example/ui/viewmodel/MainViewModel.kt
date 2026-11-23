package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.preferences.MagnetAction
import com.example.core.preferences.TorrentFileAction
import com.example.core.preferences.UserPreferencesState
import com.example.data.model.DebridAccount
import com.example.data.model.DebridProvider
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecReport
import com.example.data.model.TrueSpecStatus
import com.example.data.model.UserAccount
import com.example.data.model.parseImdbOrTmdbUrl
import com.example.util.InstalledTorrentClient
import com.example.util.NotificationHelper
import com.example.util.TorrentIntentHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AppEvent {
  data class Message(val text: String) : AppEvent()

  data class NavigateToSearch(val query: String) : AppEvent()

  data class OpenTorrentInspector(val release: TorrentRelease) : AppEvent()

  data class ChooseTorrentClient(
    val release: TorrentRelease,
    val clients: List<InstalledTorrentClient>,
  ) : AppEvent()

  data class MissingTorrentClient(val release: TorrentRelease) : AppEvent()
}

class MainViewModel(private val container: AppContainer) : ViewModel() {

  val preferences: StateFlow<UserPreferencesState> =
    container.preferences.preferencesFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      UserPreferencesState(),
    )

  val account: StateFlow<UserAccount> =
    container.authRepository.accountFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      container.authRepository.currentAccount,
    )

  val debridAccounts: StateFlow<List<DebridAccount>> = container.authRepository.debridAccounts

  private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<AppEvent> = _events.asSharedFlow()

  private val _authBusy = MutableStateFlow(false)
  val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

  private val _authError = MutableStateFlow<String?>(null)
  val authError: StateFlow<String?> = _authError.asStateFlow()

  private val _selectedTorrentForSheet = MutableStateFlow<TorrentRelease?>(null)
  val selectedTorrentForSheet: StateFlow<TorrentRelease?> = _selectedTorrentForSheet.asStateFlow()

  private val _trueSpecScanning = MutableStateFlow(false)
  val trueSpecScanning: StateFlow<Boolean> = _trueSpecScanning.asStateFlow()

  fun inspectTorrent(release: TorrentRelease) {
    _selectedTorrentForSheet.value = release
  }

  fun dismissTorrentInspector() {
    _selectedTorrentForSheet.value = null
  }

  fun clearAuthError() {
    _authError.value = null
  }

  fun signInWithApiKey(apiKey: String, username: String? = null, email: String? = null) {
    viewModelScope.launch {
      _authBusy.value = true
      _authError.value = null
      val result = container.authRepository.signInWithApiKey(apiKey, username, email)
      _authBusy.value = false
      result.onSuccess { acc ->
        _events.tryEmit(AppEvent.Message("Signed in as ${acc.username} (${acc.tier.label})"))
      }.onFailure { err ->
        _authError.value = err.localizedMessage ?: "Authentication failed"
      }
    }
  }

  fun continueWithoutAccount() {
    viewModelScope.launch {
      container.authRepository.continueWithoutAccount()
    }
  }

  fun logout() {
    viewModelScope.launch {
      container.authRepository.logout()
      _events.tryEmit(AppEvent.Message("Signed out"))
    }
  }

  fun switchAccount() {
    viewModelScope.launch {
      container.authRepository.logout()
      container.preferences.setOnboardingCompleted(false)
    }
  }

  fun handleIncomingUri(uri: Uri) {
    val scheme = uri.scheme?.lowercase()
    viewModelScope.launch {
      when {
        scheme == "torrentclaw" && uri.host == "auth" -> {
          _authBusy.value = true
          val res = container.authRepository.handleAuthDeepLink(uri)
          _authBusy.value = false
          res?.onSuccess {
            _events.tryEmit(AppEvent.Message("Signed in via torrentclaw.com (${it.tier.label})"))
          }?.onFailure {
            _authError.value = it.localizedMessage ?: "Web login failed"
          }
        }
        scheme == "magnet" -> {
          val raw = uri.toString()
          val hashMatch = Regex("btih:([a-fA-F0-9]{32,40})").find(raw)?.groupValues?.getOrNull(1)
          val dn = uri.getQueryParameter("dn") ?: "Shared Magnet Release"
          if (!hashMatch.isNullOrBlank()) {
            val rel =
              TorrentRelease(
                infoHash = hashMatch.lowercase(),
                releaseName = dn,
                magnetUrl = raw,
              )
            inspectTorrent(rel)
          } else {
            _events.tryEmit(AppEvent.Message("Could not parse magnet info hash"))
          }
        }
        else -> {
          val text = uri.toString()
          val parsed = parseImdbOrTmdbUrl(text)
          if (parsed != null) {
            _events.tryEmit(AppEvent.NavigateToSearch(text))
          }
        }
      }
    }
  }

  fun handleSharedText(text: String) {
    val trimmed = text.trim()
    if (trimmed.startsWith("magnet:", ignoreCase = true)) {
      handleIncomingUri(Uri.parse(trimmed))
    } else if (trimmed.isNotEmpty()) {
      _events.tryEmit(AppEvent.NavigateToSearch(trimmed))
    }
  }

  fun triggerMagnetAction(release: TorrentRelease) {
    viewModelScope.launch {
      val prefs = preferences.first()
      when (prefs.defaultMagnetAction) {
        MagnetAction.COPY -> copyMagnet(release)
        MagnetAction.SEND_DEBRID -> {
          val connected = debridAccounts.value.firstOrNull { it.isConnected }
          if (connected != null) sendToDebrid(connected.provider, release)
          else openMagnetWithClientSelection(release, prefs.preferredTorrentClientPackage)
        }
        MagnetAction.OPEN_CLIENT ->
          openMagnetWithClientSelection(release, prefs.preferredTorrentClientPackage)
        MagnetAction.ASK -> openMagnetWithClientSelection(release, null)
      }
    }
  }

  fun openMagnetWithClientSelection(release: TorrentRelease, preferredPackage: String?) {
    val ctx = container.appContext
    val clients = TorrentIntentHelper.findInstalledTorrentClients(ctx)
    if (clients.isEmpty()) {
      _events.tryEmit(AppEvent.MissingTorrentClient(release))
      return
    }
    if (!preferredPackage.isNullOrBlank() && clients.any { it.packageName == preferredPackage }) {
      launchMagnetInPackage(release, preferredPackage)
    } else if (clients.size == 1) {
      launchMagnetInPackage(release, clients.first().packageName)
    } else {
      _events.tryEmit(AppEvent.ChooseTorrentClient(release, clients))
    }
  }

  fun launchMagnetInPackage(release: TorrentRelease, packageName: String, saveAsDefault: Boolean = false) {
    viewModelScope.launch {
      if (saveAsDefault) {
        val label =
          TorrentIntentHelper.findInstalledTorrentClients(container.appContext)
            .firstOrNull { it.packageName == packageName }
            ?.label
        container.preferences.setPreferredTorrentClient(packageName, label)
      }
      val ok =
        TorrentIntentHelper.openMagnetLink(
          container.appContext,
          release.resolvedMagnetUrl,
          packageName,
        )
      if (ok) {
        container.historyRepository.recordTorrentAction(release, "MAGNET_OPENED")
        container.mediaRepository.trackInteraction(release.infoHash, "magnet")
      } else {
        _events.tryEmit(AppEvent.Message("Selected torrent client could not handle this magnet link"))
      }
    }
  }

  fun copyMagnet(release: TorrentRelease) {
    TorrentIntentHelper.copyToClipboard(
      container.appContext,
      release.releaseName,
      release.resolvedMagnetUrl,
    )
    viewModelScope.launch {
      container.historyRepository.recordTorrentAction(release, "MAGNET_COPIED")
      container.mediaRepository.trackInteraction(release.infoHash, "copy")
      _events.tryEmit(AppEvent.Message("Magnet link copied to clipboard"))
    }
  }

  fun downloadTorrentFile(release: TorrentRelease) {
    viewModelScope.launch {
      _events.tryEmit(AppEvent.Message("Downloading .torrent file…"))
      val res = container.mediaRepository.downloadTorrentFile(release.infoHash, release.releaseName)
      res.onSuccess { file ->
        container.historyRepository.recordTorrentAction(release, "TORRENT_DOWNLOADED")
        container.mediaRepository.trackInteraction(release.infoHash, "torrent_download")
        val prefs = preferences.first()
        when (prefs.defaultTorrentFileAction) {
          TorrentFileAction.DOWNLOAD_AND_OPEN -> {
            val opened =
              TorrentIntentHelper.openTorrentFile(
                container.appContext,
                file,
                prefs.preferredTorrentClientPackage,
              )
            if (!opened) _events.tryEmit(AppEvent.Message("Saved .torrent (${file.name}), no handler found"))
          }
          TorrentFileAction.SHARE ->
            TorrentIntentHelper.shareTorrentFile(container.appContext, file, release.releaseName)
          TorrentFileAction.SAVE_ONLY ->
            _events.tryEmit(AppEvent.Message("Saved ${file.name}"))
        }
      }.onFailure { err ->
        _events.tryEmit(AppEvent.Message(err.localizedMessage ?: "Failed to download .torrent"))
      }
    }
  }

  fun sendToDebrid(provider: DebridProvider, release: TorrentRelease) {
    viewModelScope.launch {
      val result = container.debridRepository.sendToDebrid(provider, release.resolvedMagnetUrl)
      result.onSuccess { msg ->
        container.historyRepository.recordTorrentAction(release, "DEBRID_SENT")
        _events.tryEmit(AppEvent.Message(msg))
      }.onFailure { err ->
        _events.tryEmit(AppEvent.Message(err.localizedMessage ?: "Debrid request failed"))
      }
    }
  }

  fun requestTrueSpecScan(release: TorrentRelease) {
    viewModelScope.launch {
      _trueSpecScanning.value = true
      val submit = container.mediaRepository.submitTrueSpecScan(release.infoHash)
      submit.onSuccess { report ->
        val updated = applyTrueSpecReport(release, report ?: TrueSpecReport(TrueSpecStatus.PENDING))
        _selectedTorrentForSheet.value = updated
        if (updated.trueSpecStatus == TrueSpecStatus.PENDING) {
          repeat(4) {
            delay(2500L)
            val poll = container.mediaRepository.getTrueSpecScanStatus(release.infoHash).getOrNull()
            if (poll != null && poll.status != TrueSpecStatus.PENDING) {
              val finished = applyTrueSpecReport(updated, poll)
              _selectedTorrentForSheet.value = finished
              if (preferences.value.trueSpecNotifications) {
                NotificationHelper.postTrueSpecUpdate(
                  container.appContext,
                  release.infoHash,
                  release.releaseName,
                  finished.trueSpecStatus.label,
                )
              }
              _trueSpecScanning.value = false
              return@launch
            }
          }
        }
      }.onFailure { err ->
        _events.tryEmit(AppEvent.Message(err.localizedMessage ?: "TrueSpec scan unavailable"))
      }
      _trueSpecScanning.value = false
    }
  }

  private fun applyTrueSpecReport(release: TorrentRelease, report: TrueSpecReport): TorrentRelease {
    return release.copy(
      trueSpecStatus = report.status,
      trueSpecReport = report,
      resolution = report.verifiedResolution ?: release.resolution,
      videoCodec = report.verifiedVideoCodec ?: release.videoCodec,
      subtitles = report.verifiedSubtitles.ifEmpty { release.subtitles },
    )
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
  }
}
