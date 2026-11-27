package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.preferences.AppPreferences
import com.example.data.web.MagnetTarget
import com.example.data.web.NavigationGuard
import com.example.data.web.PageLoadOutcome
import com.example.data.web.ParsedWebsitePage
import com.example.data.web.UrlDecision
import com.example.data.web.WebFormModel
import com.example.data.web.WebTorrentItem
import com.example.data.web.WebsitePageRepository
import com.example.util.InstalledTorrentClient
import com.example.util.TorrentIntentHelper
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebsiteBrowserUiState(
  val currentUrl: String = NavigationGuard.HOME_URL,
  val page: ParsedWebsitePage? = null,
  val isInitialLoading: Boolean = true,
  val isNavigating: Boolean = false,
  val isRefreshing: Boolean = false,
  val errorMessage: String? = null,
  val blockedReason: String? = null,
  val canGoBack: Boolean = false,
  val hasSessionCookies: Boolean = false,
)

sealed interface BrowserUiEvent {
  data class Message(val text: String) : BrowserUiEvent

  data class OpenTorrentFile(val file: File, val preferredPackage: String?) : BrowserUiEvent
}

class WebsiteBrowserViewModel(
  private val appContext: Context,
  private val repository: WebsitePageRepository,
  private val preferences: AppPreferences,
) : ViewModel() {

  private val historyStack = mutableListOf<String>()

  private val _uiState =
    MutableStateFlow(
      WebsiteBrowserUiState(
        hasSessionCookies = repository.hasSessionCookies(),
      )
    )
  val uiState: StateFlow<WebsiteBrowserUiState> = _uiState.asStateFlow()

  private val _magnetTarget = MutableStateFlow<MagnetTarget?>(null)
  val magnetTarget: StateFlow<MagnetTarget?> = _magnetTarget.asStateFlow()

  private val _installedClients = MutableStateFlow<List<InstalledTorrentClient>>(emptyList())
  val installedClients: StateFlow<List<InstalledTorrentClient>> = _installedClients.asStateFlow()

  private val _isDownloadingTorrent = MutableStateFlow(false)
  val isDownloadingTorrent: StateFlow<Boolean> = _isDownloadingTorrent.asStateFlow()

  private val _events = MutableSharedFlow<BrowserUiEvent>()
  val events: SharedFlow<BrowserUiEvent> = _events.asSharedFlow()

  val prefsState =
    preferences.preferencesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = com.example.core.preferences.UserPreferencesState(),
    )

  init {
    loadInitialHome()
  }

  private fun loadInitialHome() {
    historyStack.clear()
    historyStack.add(NavigationGuard.HOME_URL)
    viewModelScope.launch {
      loadIntoState(NavigationGuard.HOME_URL, isRefresh = false, pushHistory = false)
    }
  }

  fun navigateTo(rawUrl: String) {
    val current = _uiState.value.currentUrl
    viewModelScope.launch {
      loadIntoState(rawUrl, isRefresh = false, pushHistory = true, baseUrl = current)
    }
  }

  fun navigateToTypedUrl(input: String) {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return
    val target =
      when {
        trimmed.startsWith("http://", true) ||
          trimmed.startsWith("https://", true) ||
          trimmed.startsWith("magnet:", true) -> trimmed
        trimmed.startsWith("torrentclaw.com", true) ||
          trimmed.startsWith("www.torrentclaw.com", true) -> "https://$trimmed"
        trimmed.startsWith("/") -> "${NavigationGuard.BASE_ORIGIN}$trimmed"
        else -> "${NavigationGuard.BASE_ORIGIN}/$trimmed"
      }
    navigateTo(target)
  }

  fun submitSearch(query: String) {
    val trimmed = query.trim()
    val currentPage = _uiState.value.page
    val searchForm = currentPage?.searchForm
    if (searchForm != null) {
      val queryFieldName =
        searchForm.fields.firstOrNull { !it.isHidden }?.name ?: "q"
      submitForm(searchForm, mapOf(queryFieldName to trimmed))
    } else {
      val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
      navigateTo("${NavigationGuard.BASE_ORIGIN}/search?q=$encoded")
    }
  }

  fun submitForm(form: WebFormModel, values: Map<String, String>) {
    val current = _uiState.value.currentUrl
    viewModelScope.launch {
      _uiState.update {
        it.copy(isNavigating = true, errorMessage = null, blockedReason = null)
      }
      val outcome = repository.submitForm(form, values, current)
      applyOutcome(outcome, pushHistory = true)
    }
  }

  fun refresh() {
    val current = _uiState.value.currentUrl
    viewModelScope.launch {
      loadIntoState(current, isRefresh = true, pushHistory = false)
    }
  }

  fun goBack(): Boolean {
    if (historyStack.size <= 1) return false
    historyStack.removeAt(historyStack.lastIndex)
    val previousUrl = historyStack.last()
    viewModelScope.launch {
      loadIntoState(previousUrl, isRefresh = false, pushHistory = false)
    }
    return true
  }

  fun openTorrentItem(item: WebTorrentItem) {
    if (item.magnetUrl != null || item.torrentDownloadUrl != null) {
      presentMagnetChooser(
        MagnetTarget(
          title = item.title,
          magnetUrl = item.magnetUrl,
          torrentDownloadUrl = item.torrentDownloadUrl,
          infoHash = item.infoHash,
          resolution = item.resolution,
          size = item.size,
          seeders = item.seeders,
          source = item.source,
        )
      )
    } else if (!item.detailUrl.isNullOrBlank()) {
      navigateTo(item.detailUrl)
    }
  }

  fun handleIncomingIntent(uriString: String?, sharedText: String?) {
    val decision = NavigationGuard.normalizeIncomingIntent(uriString, sharedText) ?: return
    viewModelScope.launch {
      when (decision) {
        is UrlDecision.InternalPage ->
          loadIntoState(decision.normalizedUrl, isRefresh = false, pushHistory = true)
        is UrlDecision.MagnetAction ->
          presentMagnetChooser(
            MagnetTarget(
              title = decision.displayName ?: "Magnet Link",
              magnetUrl = decision.magnetUri,
            )
          )
        is UrlDecision.TorrentFileAction ->
          presentMagnetChooser(
            MagnetTarget(
              title = decision.fileName ?: "Torrent File",
              torrentDownloadUrl = decision.torrentUrl,
            )
          )
        is UrlDecision.BlockedExternal ->
          _events.emit(BrowserUiEvent.Message(decision.reason))
      }
    }
  }

  fun dismissMagnetSheet() {
    _magnetTarget.value = null
  }

  fun launchInTorrentClient(
    target: MagnetTarget,
    clientPackage: String? = null,
    rememberDefault: Boolean = false,
  ) {
    viewModelScope.launch {
      if (rememberDefault && !clientPackage.isNullOrBlank()) {
        val label = _installedClients.value.firstOrNull { it.packageName == clientPackage }?.label
        preferences.setPreferredTorrentClient(clientPackage, label)
      }
      val magnet = target.magnetUrl
      if (!magnet.isNullOrBlank()) {
        val opened = TorrentIntentHelper.openMagnetLink(appContext, magnet, clientPackage)
        if (opened) {
          _magnetTarget.value = null
        } else {
          _events.emit(BrowserUiEvent.Message("Could not open torrent app. Use Copy Link below."))
        }
      } else if (!target.torrentDownloadUrl.isNullOrBlank()) {
        downloadAndOpenTorrentFile(target, clientPackage)
      }
    }
  }

  fun downloadAndOpenTorrentFile(
    target: MagnetTarget,
    clientPackage: String? = prefsState.value.preferredTorrentClientPackage,
  ) {
    val downloadUrl = target.torrentDownloadUrl ?: return
    viewModelScope.launch {
      _isDownloadingTorrent.value = true
      val res = repository.downloadTorrentFile(downloadUrl, appContext.cacheDir, target.title)
      _isDownloadingTorrent.value = false
      res.fold(
        onSuccess = { file ->
          _magnetTarget.value = null
          _events.emit(BrowserUiEvent.OpenTorrentFile(file, clientPackage))
        },
        onFailure = { err ->
          _events.emit(BrowserUiEvent.Message(err.message ?: "Failed to download .torrent file"))
        },
      )
    }
  }

  fun copyTargetLink(target: MagnetTarget) {
    val link = target.primaryCopyUrl
    if (link.isBlank()) return
    TorrentIntentHelper.copyToClipboard(appContext, target.title, link)
    _magnetTarget.value = null
    viewModelScope.launch {
      _events.emit(
        BrowserUiEvent.Message(
          if (target.magnetUrl != null) "Magnet link copied" else "Torrent URL copied"
        )
      )
    }
  }

  fun shareTargetLink(target: MagnetTarget) {
    TorrentIntentHelper.shareMagnetTarget(appContext, target)
    _magnetTarget.value = null
  }

  fun shareCurrentPage() {
    val state = _uiState.value
    TorrentIntentHelper.sharePageUrl(
      appContext,
      state.page?.pageTitle ?: "TorrentClaw",
      state.currentUrl,
    )
  }

  fun copyCurrentPageUrl() {
    TorrentIntentHelper.copyToClipboard(appContext, "TorrentClaw URL", _uiState.value.currentUrl)
    viewModelScope.launch {
      _events.emit(BrowserUiEvent.Message("Page URL copied"))
    }
  }

  fun signOutLocalCookies() {
    repository.clearSessionCookies()
    refresh()
    viewModelScope.launch {
      _events.emit(BrowserUiEvent.Message("Signed out and cleared saved session cookies"))
    }
  }

  private suspend fun loadIntoState(
    rawUrl: String,
    isRefresh: Boolean,
    pushHistory: Boolean,
    baseUrl: String = _uiState.value.currentUrl,
  ) {
    _uiState.update { state ->
      state.copy(
        isInitialLoading = state.page == null,
        isNavigating = !isRefresh,
        isRefreshing = isRefresh,
        errorMessage = null,
        blockedReason = null,
      )
    }
    val outcome = repository.loadUrl(rawUrl, baseUrl)
    applyOutcome(outcome, pushHistory)
  }

  private suspend fun applyOutcome(outcome: PageLoadOutcome, pushHistory: Boolean) {
    when (outcome) {
      is PageLoadOutcome.Success -> {
        val finalUrl = outcome.page.url
        if (pushHistory && historyStack.lastOrNull() != finalUrl) {
          historyStack.add(finalUrl)
          if (historyStack.size > 40) historyStack.removeAt(0)
        }
        _uiState.update {
          it.copy(
            currentUrl = finalUrl,
            page = outcome.page,
            isInitialLoading = false,
            isNavigating = false,
            isRefreshing = false,
            errorMessage = null,
            blockedReason = null,
            canGoBack = historyStack.size > 1,
            hasSessionCookies = repository.hasSessionCookies(),
          )
        }
      }
      is PageLoadOutcome.MagnetIntercepted -> {
        _uiState.update {
          it.copy(isInitialLoading = false, isNavigating = false, isRefreshing = false)
        }
        presentMagnetChooser(outcome.target)
      }
      is PageLoadOutcome.TorrentFileIntercepted -> {
        _uiState.update {
          it.copy(isInitialLoading = false, isNavigating = false, isRefreshing = false)
        }
        presentMagnetChooser(outcome.target)
      }
      is PageLoadOutcome.Blocked -> {
        _uiState.update {
          it.copy(
            isInitialLoading = false,
            isNavigating = false,
            isRefreshing = false,
            blockedReason = outcome.reason,
          )
        }
        _events.emit(BrowserUiEvent.Message(outcome.reason))
      }
      is PageLoadOutcome.Failure -> {
        _uiState.update {
          it.copy(
            currentUrl = outcome.url,
            isInitialLoading = false,
            isNavigating = false,
            isRefreshing = false,
            errorMessage = outcome.message,
            canGoBack = historyStack.size > 1,
          )
        }
      }
    }
  }

  private fun presentMagnetChooser(target: MagnetTarget) {
    _installedClients.value = TorrentIntentHelper.findInstalledTorrentClients(appContext)
    _magnetTarget.value = target
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return WebsiteBrowserViewModel(
        appContext = container.appContext,
        repository = container.websitePageRepository,
        preferences = container.preferences,
      )
        as T
    }
  }
}
