package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.preferences.AppPreferences
import com.example.core.preferences.UserPreferencesState
import com.example.data.model.CollectionSummary
import com.example.data.model.DiscoverySnapshot
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.ParsedExternalMediaUrl
import com.example.data.model.SearchFilter
import com.example.data.model.TorrentRelease
import com.example.data.model.parseImdbOrTmdbUrl
import com.example.data.repository.TorrentClawRepository
import com.example.util.InstalledTorrentClient
import com.example.util.TorrentIntentHelper
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

enum class TopSectionTab(val label: String) {
  DISCOVER("Discover"),
  POPULAR("Popular"),
  RECENT("Recent"),
  COLLECTIONS("Collections"),
  FAVORITES("Favorites"),
  SEARCH("Search"),
}

data class SectionListState(
  val items: List<MediaItem> = emptyList(),
  val torrents: List<TorrentRelease> = emptyList(),
  val mediaType: MediaType = MediaType.ALL,
  val page: Int = 1,
  val hasMore: Boolean = false,
  val isLoading: Boolean = false,
  val isLoadingMore: Boolean = false,
  val error: String? = null,
)

data class CollectionsTabState(
  val collections: List<CollectionSummary> = emptyList(),
  val selectedCollection: CollectionSummary? = null,
  val selectedItems: List<MediaItem> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
)

sealed interface CatalogUiEvent {
  data class Message(val text: String) : CatalogUiEvent

  data class OpenTorrentFile(val file: File, val preferredPackage: String?) : CatalogUiEvent
}

class CatalogViewModel(
  private val appContext: Context,
  private val repository: TorrentClawRepository,
  private val preferences: AppPreferences,
) : ViewModel() {

  val prefsState: StateFlow<UserPreferencesState> =
    preferences.preferencesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserPreferencesState(),
    )

  private val _activeTab = MutableStateFlow(TopSectionTab.DISCOVER)
  val activeTab: StateFlow<TopSectionTab> = _activeTab.asStateFlow()

  // Discover state
  private val _discoverSnapshot = MutableStateFlow(DiscoverySnapshot())
  val discoverSnapshot: StateFlow<DiscoverySnapshot> = _discoverSnapshot.asStateFlow()
  private val _discoverLoading = MutableStateFlow(true)
  val discoverLoading: StateFlow<Boolean> = _discoverLoading.asStateFlow()
  private val _discoverRefreshing = MutableStateFlow(false)
  val discoverRefreshing: StateFlow<Boolean> = _discoverRefreshing.asStateFlow()
  private val _discoverError = MutableStateFlow<String?>(null)
  val discoverError: StateFlow<String?> = _discoverError.asStateFlow()

  // Popular & Recent tab states
  private val _popularState = MutableStateFlow(SectionListState())
  val popularState: StateFlow<SectionListState> = _popularState.asStateFlow()
  private val _recentState = MutableStateFlow(SectionListState())
  val recentState: StateFlow<SectionListState> = _recentState.asStateFlow()

  // Collections tab state
  private val _collectionsState = MutableStateFlow(CollectionsTabState())
  val collectionsState: StateFlow<CollectionsTabState> = _collectionsState.asStateFlow()

  // Search bar + results state
  private val _queryText = MutableStateFlow("")
  val queryText: StateFlow<String> = _queryText.asStateFlow()
  private val _searchFocused = MutableStateFlow(false)
  val searchFocused: StateFlow<Boolean> = _searchFocused.asStateFlow()
  private val _suggestions = MutableStateFlow<List<MediaItem>>(emptyList())
  val suggestions: StateFlow<List<MediaItem>> = _suggestions.asStateFlow()
  private val _parsedExternalUrl = MutableStateFlow<ParsedExternalMediaUrl?>(null)
  val parsedExternalUrl: StateFlow<ParsedExternalMediaUrl?> = _parsedExternalUrl.asStateFlow()
  private val _searchFilter = MutableStateFlow(SearchFilter())
  val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()
  private val _searchResultsState = MutableStateFlow(SectionListState())
  val searchResultsState: StateFlow<SectionListState> = _searchResultsState.asStateFlow()

  // Detail navigation stack
  private val _selectedMediaForDetail = MutableStateFlow<MediaItem?>(null)
  val selectedMediaForDetail: StateFlow<MediaItem?> = _selectedMediaForDetail.asStateFlow()

  // Magnet chooser sheet state
  private val _selectedTorrentForChooser = MutableStateFlow<TorrentRelease?>(null)
  val selectedTorrentForChooser: StateFlow<TorrentRelease?> = _selectedTorrentForChooser.asStateFlow()
  private val _installedClients = MutableStateFlow<List<InstalledTorrentClient>>(emptyList())
  val installedClients: StateFlow<List<InstalledTorrentClient>> = _installedClients.asStateFlow()
  private val _isDownloadingTorrent = MutableStateFlow(false)
  val isDownloadingTorrent: StateFlow<Boolean> = _isDownloadingTorrent.asStateFlow()

  private val _events = MutableSharedFlow<CatalogUiEvent>()
  val events: SharedFlow<CatalogUiEvent> = _events.asSharedFlow()

  private var autocompleteJob: Job? = null
  private var searchJob: Job? = null

  init {
    loadDiscover(forceRefresh = false)
  }

  fun selectTab(tab: TopSectionTab) {
    _activeTab.value = tab
    _searchFocused.value = false
    when (tab) {
      TopSectionTab.DISCOVER -> {
        if (_discoverSnapshot.value.trending.isEmpty() && !_discoverLoading.value) {
          loadDiscover(forceRefresh = false)
        }
      }
      TopSectionTab.POPULAR -> {
        if (_popularState.value.items.isEmpty() && !_popularState.value.isLoading) {
          loadPopular(reset = true)
        }
      }
      TopSectionTab.RECENT -> {
        if (_recentState.value.items.isEmpty() && _recentState.value.torrents.isEmpty() && !_recentState.value.isLoading) {
          loadRecent(reset = true)
        }
      }
      TopSectionTab.COLLECTIONS -> {
        if (_collectionsState.value.collections.isEmpty() && !_collectionsState.value.isLoading) {
          loadCollections()
        }
      }
      TopSectionTab.FAVORITES,
      TopSectionTab.SEARCH -> Unit
    }
  }

  fun refreshActiveTab() {
    when (_activeTab.value) {
      TopSectionTab.DISCOVER -> loadDiscover(forceRefresh = true)
      TopSectionTab.POPULAR -> loadPopular(reset = true)
      TopSectionTab.RECENT -> loadRecent(reset = true)
      TopSectionTab.COLLECTIONS -> {
        val activeCol = _collectionsState.value.selectedCollection
        if (activeCol != null) openCollection(activeCol) else loadCollections()
      }
      TopSectionTab.FAVORITES -> Unit
      TopSectionTab.SEARCH -> executeSearch(reset = true)
    }
  }

  fun loadDiscover(forceRefresh: Boolean) {
    viewModelScope.launch {
      if (forceRefresh) _discoverRefreshing.value = true else _discoverLoading.value = true
      _discoverError.value = null
      repository.loadDiscovery(forceRefresh = forceRefresh).fold(
        onSuccess = { snap ->
          _discoverSnapshot.value = snap
          _discoverLoading.value = false
          _discoverRefreshing.value = false
        },
        onFailure = { err ->
          _discoverError.value = err.message ?: "Unable to connect to TorrentClaw"
          _discoverLoading.value = false
          _discoverRefreshing.value = false
        },
      )
    }
  }

  fun setPopularType(type: MediaType) {
    _popularState.update { it.copy(mediaType = type) }
    _activeTab.value = TopSectionTab.POPULAR
    loadPopular(reset = true)
  }

  fun loadPopular(reset: Boolean) {
    val cur = _popularState.value
    if (!reset && (cur.isLoadingMore || !cur.hasMore)) return
    val nextPage = if (reset) 1 else cur.page + 1
    viewModelScope.launch {
      _popularState.update {
        it.copy(isLoading = reset, isLoadingMore = !reset, error = null)
      }
      repository.loadPopular(cur.mediaType, nextPage).fold(
        onSuccess = { parsed ->
          _popularState.update { state ->
            val merged =
              if (reset) parsed.mediaItems else (state.items + parsed.mediaItems).distinctBy { it.id }
            state.copy(
              items = merged,
              page = nextPage,
              hasMore = parsed.hasMore,
              isLoading = false,
              isLoadingMore = false,
            )
          }
        },
        onFailure = { err ->
          _popularState.update {
            it.copy(
              isLoading = false,
              isLoadingMore = false,
              error = err.message ?: "Failed to load popular titles",
            )
          }
        },
      )
    }
  }

  fun setRecentType(type: MediaType) {
    _recentState.update { it.copy(mediaType = type) }
    loadRecent(reset = true)
  }

  fun loadRecent(reset: Boolean) {
    val cur = _recentState.value
    if (!reset && (cur.isLoadingMore || !cur.hasMore)) return
    val nextPage = if (reset) 1 else cur.page + 1
    viewModelScope.launch {
      _recentState.update {
        it.copy(isLoading = reset, isLoadingMore = !reset, error = null)
      }
      repository.loadRecent(cur.mediaType, nextPage).fold(
        onSuccess = { parsed ->
          _recentState.update { state ->
            val mergedItems =
              if (reset) parsed.mediaItems else (state.items + parsed.mediaItems).distinctBy { it.id }
            val mergedTorrents =
              if (reset) parsed.torrents else (state.torrents + parsed.torrents).distinctBy { it.infoHash }
            state.copy(
              items = mergedItems,
              torrents = mergedTorrents,
              page = nextPage,
              hasMore = parsed.hasMore,
              isLoading = false,
              isLoadingMore = false,
            )
          }
        },
        onFailure = { err ->
          _recentState.update {
            it.copy(
              isLoading = false,
              isLoadingMore = false,
              error = err.message ?: "Failed to load recent releases",
            )
          }
        },
      )
    }
  }

  fun loadCollections() {
    viewModelScope.launch {
      _collectionsState.update { it.copy(isLoading = true, error = null, selectedCollection = null) }
      repository.loadCollections(1).fold(
        onSuccess = { cols ->
          _collectionsState.update { it.copy(collections = cols, isLoading = false) }
        },
        onFailure = { err ->
          _collectionsState.update {
            it.copy(isLoading = false, error = err.message ?: "Failed to load collections")
          }
        },
      )
    }
  }

  fun openCollection(collection: CollectionSummary) {
    _activeTab.value = TopSectionTab.COLLECTIONS
    viewModelScope.launch {
      _collectionsState.update {
        it.copy(selectedCollection = collection, selectedItems = emptyList(), isLoading = true, error = null)
      }
      repository.loadCollectionItems(collection.id).fold(
        onSuccess = { parsed ->
          if (parsed.mediaItems.isNotEmpty()) {
            _collectionsState.update { it.copy(selectedItems = parsed.mediaItems, isLoading = false) }
          } else {
            // Fallback search by collection title if collection endpoint returned only metadata
            _collectionsState.update { it.copy(selectedCollection = null, isLoading = false) }
            submitSearch(collection.name)
          }
        },
        onFailure = {
          _collectionsState.update { it.copy(selectedCollection = null, isLoading = false) }
          submitSearch(collection.name)
        },
      )
    }
  }

  fun closeSelectedCollection() {
    _collectionsState.update { it.copy(selectedCollection = null, selectedItems = emptyList()) }
  }

  fun setSearchFocused(focused: Boolean) {
    _searchFocused.value = focused
  }

  fun onQueryChange(newQuery: String) {
    _queryText.value = newQuery
    _parsedExternalUrl.value = parseImdbOrTmdbUrl(newQuery)
    autocompleteJob?.cancel()
    if (newQuery.trim().length < 2 || _parsedExternalUrl.value != null) {
      _suggestions.value = emptyList()
      return
    }
    autocompleteJob =
      viewModelScope.launch {
        delay(260)
        _suggestions.value = repository.autocomplete(newQuery)
      }
  }

  fun submitSearch(rawQuery: String = _queryText.value) {
    val clean = rawQuery.trim()
    _queryText.value = clean
    _searchFocused.value = false
    _suggestions.value = emptyList()
    _parsedExternalUrl.value = parseImdbOrTmdbUrl(clean)
    _searchFilter.update { it.copy(query = clean) }
    _activeTab.value = TopSectionTab.SEARCH
    if (clean.isNotBlank()) {
      viewModelScope.launch { preferences.addRecentSearch(clean) }
    }
    executeSearch(reset = true)
  }

  fun applySearchFilter(filter: SearchFilter) {
    _searchFilter.value = filter
    _queryText.value = filter.query
    _searchFocused.value = false
    _activeTab.value = TopSectionTab.SEARCH
    executeSearch(reset = true)
  }

  fun resetSearchFilters() {
    val q = _queryText.value
    _searchFilter.value = SearchFilter(query = q)
    executeSearch(reset = true)
  }

  fun clearSearch() {
    _queryText.value = ""
    _suggestions.value = emptyList()
    _parsedExternalUrl.value = null
    _searchFilter.value = SearchFilter()
    _searchResultsState.value = SectionListState()
    if (_activeTab.value == TopSectionTab.SEARCH) {
      _activeTab.value = TopSectionTab.DISCOVER
    }
  }

  fun loadMoreSearch() {
    executeSearch(reset = false)
  }

  private fun executeSearch(reset: Boolean) {
    val cur = _searchResultsState.value
    if (!reset && (cur.isLoadingMore || !cur.hasMore)) return
    val nextPage = if (reset) 1 else cur.page + 1
    val filter = _searchFilter.value

    searchJob?.cancel()
    searchJob =
      viewModelScope.launch {
        _searchResultsState.update {
          it.copy(isLoading = reset, isLoadingMore = !reset, error = null)
        }
        repository.search(filter, nextPage).fold(
          onSuccess = { parsed ->
            _searchResultsState.update { state ->
              val mergedItems =
                if (reset) parsed.mediaItems else (state.items + parsed.mediaItems).distinctBy { it.id }
              val mergedTorrents =
                if (reset) parsed.torrents else (state.torrents + parsed.torrents).distinctBy { it.infoHash }
              state.copy(
                items = mergedItems,
                torrents = mergedTorrents,
                page = nextPage,
                hasMore = parsed.hasMore,
                isLoading = false,
                isLoadingMore = false,
              )
            }
          },
          onFailure = { err ->
            _searchResultsState.update {
              it.copy(
                isLoading = false,
                isLoadingMore = false,
                error = err.message ?: "Search failed",
              )
            }
          },
        )
      }
  }

  fun removeRecentSearch(query: String) {
    viewModelScope.launch { preferences.removeRecentSearch(query) }
  }

  fun clearRecentSearches() {
    viewModelScope.launch { preferences.clearRecentSearches() }
  }

  fun openMediaDetail(item: MediaItem) {
    _searchFocused.value = false
    _selectedMediaForDetail.value = item
  }

  fun closeMediaDetail() {
    _selectedMediaForDetail.value = null
  }

  fun toggleFavorite(item: MediaItem) {
    viewModelScope.launch {
      val added = preferences.toggleFavorite(item)
      _events.emit(
        CatalogUiEvent.Message(
          if (added) "Added \"${item.title}\" to Favorites"
          else "Removed \"${item.title}\" from Favorites"
        )
      )
    }
  }

  fun clearFavorites() {
    viewModelScope.launch {
      preferences.clearFavorites()
      _events.emit(CatalogUiEvent.Message("Favorites cleared"))
    }
  }

  fun openTorrentChooser(release: TorrentRelease) {
    _installedClients.value = TorrentIntentHelper.findInstalledTorrentClients(appContext)
    _selectedTorrentForChooser.value = release
  }

  fun dismissTorrentChooser() {
    _selectedTorrentForChooser.value = null
  }

  fun openMagnetDirectly(release: TorrentRelease, fallbackMediaUrl: String? = null) {
    viewModelScope.launch {
      val preferredPkg = prefsState.value.preferredTorrentClientPackage
      val magnetUri = release.resolvedMagnetUrl
      if (magnetUri.isNotBlank()) {
        val launched = TorrentIntentHelper.openMagnetLink(appContext, magnetUri, preferredPkg)
        if (launched) {
          _selectedTorrentForChooser.value = null
          return@launch
        }
        openTorrentChooser(release)
        val installed = _installedClients.value
        _events.emit(
          CatalogUiEvent.Message(
            if (installed.isEmpty()) "No torrent app installed. Copy or share the magnet link below."
            else "Could not launch torrent app. Pick a client or copy magnet link."
          )
        )
        return@launch
      }

      if (!release.torrentDownloadUrl.isNullOrBlank()) {
        downloadTorrentFile(release, preferredPkg)
        return@launch
      }

      val searchQuery =
        URLEncoder.encode(
          release.mediaTitle?.takeIf { it.isNotBlank() } ?: release.releaseName,
          StandardCharsets.UTF_8.name(),
        )
      val url =
        release.resolvedContentWebUrl
          ?: fallbackMediaUrl?.takeIf { it.isNotBlank() }
          ?: "https://torrentclaw.com/search?q=$searchQuery"
      val openedWeb = TorrentIntentHelper.openWebUrl(appContext, url)
      if (!openedWeb) {
        openTorrentChooser(release)
        _events.emit(
          CatalogUiEvent.Message("Add a free TorrentClaw API key or copy title to get magnet links.")
        )
      }
    }
  }

  fun launchInTorrentClient(
    release: TorrentRelease,
    clientPackage: String? = null,
    rememberDefault: Boolean = false,
  ) {
    viewModelScope.launch {
      if (!release.hasDirectMagnet || release.resolvedMagnetUrl.isBlank()) {
        _events.emit(
          CatalogUiEvent.Message("Direct magnet requires a free TorrentClaw API key or opening on TorrentClaw.com.")
        )
        return@launch
      }
      if (rememberDefault && !clientPackage.isNullOrBlank()) {
        val label = _installedClients.value.firstOrNull { it.packageName == clientPackage }?.label
        preferences.setPreferredTorrentClient(clientPackage, label)
      }
      val ok = TorrentIntentHelper.openMagnetLink(appContext, release.resolvedMagnetUrl, clientPackage)
      if (ok) {
        _selectedTorrentForChooser.value = null
      } else {
        _events.emit(CatalogUiEvent.Message("Could not launch torrent app. Use Copy Magnet Link."))
      }
    }
  }

  fun copyMagnet(release: TorrentRelease) {
    if (!release.hasDirectMagnet || release.resolvedMagnetUrl.isBlank()) {
      viewModelScope.launch {
        _events.emit(
          CatalogUiEvent.Message("Direct magnet requires a free TorrentClaw API key or opening on TorrentClaw.com.")
        )
      }
      return
    }
    TorrentIntentHelper.copyToClipboard(appContext, release.releaseName, release.resolvedMagnetUrl)
    _selectedTorrentForChooser.value = null
    viewModelScope.launch { _events.emit(CatalogUiEvent.Message("Magnet link copied")) }
  }

  fun copyInfoHash(release: TorrentRelease) {
    if (release.infoHash.isBlank()) {
      viewModelScope.launch {
        _events.emit(CatalogUiEvent.Message("Info hash unavailable without an API key"))
      }
      return
    }
    TorrentIntentHelper.copyToClipboard(appContext, "Info Hash", release.infoHash)
    _selectedTorrentForChooser.value = null
    viewModelScope.launch { _events.emit(CatalogUiEvent.Message("Info hash copied")) }
  }

  fun copyReleaseTitle(release: TorrentRelease) {
    TorrentIntentHelper.copyToClipboard(appContext, "Release Title", release.releaseName)
    _selectedTorrentForChooser.value = null
    viewModelScope.launch { _events.emit(CatalogUiEvent.Message("Release title copied")) }
  }

  fun openOnTorrentClawWeb(release: TorrentRelease, fallbackMediaUrl: String? = null) {
    val searchQuery =
      URLEncoder.encode(
        release.mediaTitle?.takeIf { it.isNotBlank() } ?: release.releaseName,
        StandardCharsets.UTF_8.name(),
      )
    val url =
      release.resolvedContentWebUrl
        ?: fallbackMediaUrl?.takeIf { it.isNotBlank() }
        ?: "https://torrentclaw.com/search?q=$searchQuery"
    val ok = TorrentIntentHelper.openWebUrl(appContext, url)
    if (ok) {
      _selectedTorrentForChooser.value = null
    } else {
      viewModelScope.launch { _events.emit(CatalogUiEvent.Message("Unable to open web browser")) }
    }
  }

  fun saveTorrentClawApiKey(apiKey: String?, onSaved: () -> Unit = {}) {
    viewModelScope.launch {
      val trimmed = apiKey?.trim()?.takeIf { it.isNotBlank() }
      preferences.setTorrentClawApiKey(trimmed)
      repository.clearDetailCache()
      _selectedTorrentForChooser.value = null
      _events.emit(
        CatalogUiEvent.Message(
          if (trimmed != null) "TorrentClaw API key saved. Refreshing magnets…"
          else "TorrentClaw API key cleared"
        )
      )
      onSaved()
    }
  }

  fun downloadTorrentFile(
    release: TorrentRelease,
    preferredPackage: String? = prefsState.value.preferredTorrentClientPackage,
  ) {
    viewModelScope.launch {
      _isDownloadingTorrent.value = true
      val res = repository.downloadTorrentFile(release)
      _isDownloadingTorrent.value = false
      res.fold(
        onSuccess = { file ->
          _selectedTorrentForChooser.value = null
          _events.emit(CatalogUiEvent.OpenTorrentFile(file, preferredPackage))
        },
        onFailure = { err ->
          _events.emit(CatalogUiEvent.Message(err.message ?: "Failed to download .torrent file"))
        },
      )
    }
  }

  fun shareRelease(release: TorrentRelease, fallbackMediaUrl: String? = null) {
    TorrentIntentHelper.shareTorrentRelease(appContext, release, fallbackMediaUrl)
    _selectedTorrentForChooser.value = null
  }

  fun handleIncomingIntent(uriString: String?, sharedText: String?) {
    when {
      !uriString.isNullOrBlank() && uriString.startsWith("magnet:", true) -> {
        openTorrentChooser(
          TorrentRelease(
            infoHash = Regex("btih:([a-zA-Z0-9]{32,40})", RegexOption.IGNORE_CASE)
              .find(uriString)?.groupValues?.get(1).orEmpty(),
            releaseName = com.example.data.model.extractMagnetDisplayName(uriString) ?: "Magnet Link",
            magnetUrl = uriString,
          )
        )
      }
      !sharedText.isNullOrBlank() -> submitSearch(sharedText)
      !uriString.isNullOrBlank() && uriString.contains("imdb.com", true) -> submitSearch(uriString)
      !uriString.isNullOrBlank() && uriString.contains("themoviedb.org", true) -> submitSearch(uriString)
    }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CatalogViewModel(
        appContext = container.appContext,
        repository = container.repository,
        preferences = container.preferences,
      )
        as T
    }
  }
}
