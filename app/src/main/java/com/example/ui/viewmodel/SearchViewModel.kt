package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.AppContainer
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.AutocompleteSuggestion
import com.example.data.model.MediaItem
import com.example.data.model.ParsedMediaLookup
import com.example.data.model.SearchFilter
import com.example.data.model.TorrentRelease
import com.example.data.model.parseImdbOrTmdbUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {

  private val _queryText = MutableStateFlow("")
  val queryText: StateFlow<String> = _queryText.asStateFlow()

  private val _filter = MutableStateFlow(SearchFilter())
  val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

  private val _submittedFilter = MutableStateFlow<SearchFilter?>(null)
  val hasActiveSearch: StateFlow<Boolean> =
    _submittedFilter
      .flatMapLatest { MutableStateFlow(it != null) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  private val _suggestions = MutableStateFlow<List<AutocompleteSuggestion>>(emptyList())
  val suggestions: StateFlow<List<AutocompleteSuggestion>> = _suggestions.asStateFlow()

  private val _parsedUrl = MutableStateFlow<ParsedMediaLookup?>(null)
  val parsedUrl: StateFlow<ParsedMediaLookup?> = _parsedUrl.asStateFlow()

  private val _directTorrents = MutableStateFlow<List<TorrentRelease>>(emptyList())
  val directTorrents: StateFlow<List<TorrentRelease>> = _directTorrents.asStateFlow()

  private val _compareSelection = MutableStateFlow<List<TorrentRelease>>(emptyList())
  val compareSelection: StateFlow<List<TorrentRelease>> = _compareSelection.asStateFlow()

  val searchHistory: StateFlow<List<SearchHistoryEntity>> =
    container.historyRepository.searchHistoryFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val pagedResults: Flow<PagingData<MediaItem>> =
    _submittedFilter
      .flatMapLatest { active ->
        if (active == null) emptyFlow() else container.mediaRepository.createSearchPager(active)
      }
      .cachedIn(viewModelScope)

  private var autocompleteJob: Job? = null
  private var directTorrentJob: Job? = null

  init {
    viewModelScope.launch {
      val prefs = container.preferences.preferencesFlow
      prefs.collect { state ->
        if (_submittedFilter.value == null) {
          _filter.value = _filter.value.copy(sortBy = state.defaultSort)
        }
      }
    }

    viewModelScope.launch {
      _queryText.debounce(260L).distinctUntilChanged().collect { q ->
        _parsedUrl.value = parseImdbOrTmdbUrl(q)
        autocompleteJob?.cancel()
        if (q.trim().length >= 2 && _parsedUrl.value == null) {
          autocompleteJob =
            launch {
              _suggestions.value = container.mediaRepository.autocomplete(q)
            }
        } else {
          _suggestions.value = emptyList()
        }
      }
    }
  }

  fun onQueryChange(value: String) {
    _queryText.value = value
    _filter.value = _filter.value.copy(query = value)
  }

  fun submitSearch(overrideQuery: String? = null) {
    val q = (overrideQuery ?: _queryText.value).trim()
    _queryText.value = q
    val updated = _filter.value.copy(query = q)
    _filter.value = updated
    if (q.isBlank() && updated.activeFilterCount == 0) return

    _suggestions.value = emptyList()
    _submittedFilter.value = updated

    viewModelScope.launch {
      if (q.isNotBlank()) {
        container.historyRepository.recordSearch(q, updated.mediaType.name)
      }
    }

    // Also fetch flat torrent releases if query yields direct torrent entries
    directTorrentJob?.cancel()
    directTorrentJob =
      viewModelScope.launch {
        runCatching { container.mediaRepository.searchPage(updated, page = 1, limit = 25) }
          .onSuccess { _directTorrents.value = it.torrents }
          .onFailure { _directTorrents.value = emptyList() }
      }
  }

  fun applyFilter(newFilter: SearchFilter, executeNow: Boolean = true) {
    _filter.value = newFilter
    if (executeNow && (newFilter.query.isNotBlank() || newFilter.activeFilterCount > 0)) {
      _submittedFilter.value = newFilter
    }
  }

  fun cancelOrClearSearch() {
    autocompleteJob?.cancel()
    directTorrentJob?.cancel()
    _queryText.value = ""
    _filter.value = _filter.value.copy(query = "")
    _submittedFilter.value = null
    _suggestions.value = emptyList()
    _directTorrents.value = emptyList()
  }

  fun resetAllFilters() {
    val q = _queryText.value
    val reset = SearchFilter(query = q)
    _filter.value = reset
    if (_submittedFilter.value != null && q.isNotBlank()) {
      _submittedFilter.value = reset
    }
  }

  fun deleteHistoryEntry(query: String) {
    viewModelScope.launch { container.historyRepository.deleteSearchQuery(query) }
  }

  fun clearHistory() {
    viewModelScope.launch { container.historyRepository.clearSearchHistory() }
  }

  fun toggleCompare(release: TorrentRelease) {
    val list = _compareSelection.value.toMutableList()
    val idx = list.indexOfFirst { it.infoHash == release.infoHash }
    if (idx >= 0) {
      list.removeAt(idx)
    } else if (list.size < 4) {
      list.add(release)
    }
    _compareSelection.value = list
  }

  fun clearCompare() {
    _compareSelection.value = emptyList()
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(container) as T
  }
}
