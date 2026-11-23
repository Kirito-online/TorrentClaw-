package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.model.MediaItem
import com.example.data.model.SmartRecommendation
import com.example.data.model.TorrentRelease
import com.example.data.model.computeSmartRecommendation
import com.example.data.repository.MediaDetailBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
  val isLoading: Boolean = true,
  val bundle: MediaDetailBundle? = null,
  val selectedSeason: Int? = null,
  val selectedEpisode: Int? = null,
  val recommendation: SmartRecommendation? = null,
  val isFavorite: Boolean = false,
  val isWatchlist: Boolean = false,
  val isFollowedAlert: Boolean = false,
  val error: String? = null,
)

class DetailViewModel(
  private val container: AppContainer,
  private val mediaId: String,
) : ViewModel() {

  private val _isLoading = MutableStateFlow(true)
  private val _bundle = MutableStateFlow<MediaDetailBundle?>(null)
  private val _selectedSeason = MutableStateFlow<Int?>(null)
  private val _selectedEpisode = MutableStateFlow<Int?>(null)
  private val _error = MutableStateFlow<String?>(null)
  private val _compareReleases = MutableStateFlow<List<TorrentRelease>>(emptyList())
  val compareReleases: StateFlow<List<TorrentRelease>> = _compareReleases.asStateFlow()

  val uiState: StateFlow<DetailUiState> =
    combine(
        _isLoading,
        _bundle,
        _selectedSeason,
        _selectedEpisode,
        container.preferences.preferencesFlow,
        container.libraryRepository.observeIsSaved(mediaId, "FAVORITE"),
        container.libraryRepository.observeIsSaved(mediaId, "WATCHLIST"),
        container.alertRepository.observeIsFollowed(mediaId),
      ) { values ->
        val loading = values[0] as Boolean
        val bundle = values[1] as MediaDetailBundle?
        val season = values[2] as Int?
        val episode = values[3] as Int?
        val prefs = values[4] as com.example.core.preferences.UserPreferencesState
        val isFav = values[5] as Boolean
        val isWatch = values[6] as Boolean
        val isAlert = values[7] as Boolean

        val rec = bundle?.let { computeSmartRecommendation(it.torrents, prefs) }
        DetailUiState(
          isLoading = loading,
          bundle = bundle,
          selectedSeason = season,
          selectedEpisode = episode,
          recommendation = rec,
          isFavorite = isFav,
          isWatchlist = isWatch,
          isFollowedAlert = isAlert,
          error = _error.value,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

  init {
    loadDetail()
  }

  fun loadDetail(season: Int? = _selectedSeason.value, episode: Int? = _selectedEpisode.value) {
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null
      val region = container.preferences.preferencesFlow.first().preferredRegion
      val res = container.mediaRepository.loadMediaDetail(mediaId, season, episode, region)
      res.onSuccess { loaded ->
        _bundle.value = loaded
        if (_selectedSeason.value == null && loaded.media.seasons.isNotEmpty()) {
          _selectedSeason.value = loaded.media.seasons.first()
        }
      }.onFailure { err ->
        _error.value = err.localizedMessage ?: "Failed to load media details"
      }
      _isLoading.value = false
    }
  }

  fun selectSeason(season: Int?) {
    _selectedSeason.value = season
    _selectedEpisode.value = null
    loadDetail(season = season, episode = null)
  }

  fun selectEpisode(episode: Int?) {
    _selectedEpisode.value = episode
    loadDetail(season = _selectedSeason.value, episode = episode)
  }

  fun toggleFavorite() {
    val item = uiState.value.bundle?.media ?: return
    viewModelScope.launch {
      container.libraryRepository.toggleMediaCategory(item, "FAVORITE", uiState.value.isFavorite)
    }
  }

  fun toggleWatchlist() {
    val item = uiState.value.bundle?.media ?: return
    viewModelScope.launch {
      container.libraryRepository.toggleMediaCategory(item, "WATCHLIST", uiState.value.isWatchlist)
    }
  }

  fun toggleAlert(onResult: (String) -> Unit) {
    val item: MediaItem = uiState.value.bundle?.media ?: return
    viewModelScope.launch {
      if (uiState.value.isFollowedAlert) {
        container.alertRepository.unfollowTitle(item.id)
        onResult("Removed release alert for ${item.title}")
      } else {
        val prefs = container.preferences.preferencesFlow.first()
        val res =
          container.alertRepository.followTitle(
            item,
            prefs.alertDefaultQuality,
            prefs.alertMinQualityScore,
          )
        res.onSuccess {
          onResult("Following ${item.title} (${prefs.alertDefaultQuality}, score ≥ ${prefs.alertMinQualityScore})")
        }.onFailure { err ->
          onResult(err.localizedMessage ?: "Unable to follow title")
        }
      }
    }
  }

  fun toggleCompare(release: TorrentRelease) {
    val list = _compareReleases.value.toMutableList()
    val idx = list.indexOfFirst { it.infoHash == release.infoHash }
    if (idx >= 0) list.removeAt(idx)
    else if (list.size < 4) list.add(release)
    _compareReleases.value = list
  }

  fun clearCompare() {
    _compareReleases.value = emptyList()
  }

  class Factory(
    private val container: AppContainer,
    private val mediaId: String,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      DetailViewModel(container, mediaId) as T
  }
}
