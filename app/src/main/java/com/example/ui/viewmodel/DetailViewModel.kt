package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.preferences.UserPreferencesState
import com.example.data.model.CastMember
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SmartMatchRecommendation
import com.example.data.model.TorrentFilterFacets
import com.example.data.model.TorrentListFilterState
import com.example.data.model.TorrentListSort
import com.example.data.model.TorrentRelease
import com.example.data.model.WatchProvider
import com.example.data.model.computeSmartRecommendation
import com.example.data.model.extractSeasonEpisodeFromName
import com.example.data.model.extractTorrentFilterFacets
import com.example.data.model.filterAndSortTorrents
import com.example.data.model.matchesSeasonAndEpisode
import com.example.data.repository.TorrentClawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaDetailUiState(
  val media: MediaItem? = null,
  val allTorrents: List<TorrentRelease> = emptyList(),
  val filteredTorrents: List<TorrentRelease> = emptyList(),
  val facets: TorrentFilterFacets = TorrentFilterFacets(),
  val filter: TorrentListFilterState = TorrentListFilterState(),
  val availableSeasons: List<Int> = emptyList(),
  val availableEpisodes: List<Int> = emptyList(),
  val selectedSeason: Int? = null,
  val selectedEpisode: Int? = null,
  val cast: List<CastMember> = emptyList(),
  val watchProviders: List<WatchProvider> = emptyList(),
  val recommendation: SmartMatchRecommendation? = null,
  val isLoading: Boolean = true,
  val isRefreshing: Boolean = false,
  val canLoadMoreTorrents: Boolean = false,
  val isLoadingMoreTorrents: Boolean = false,
  val loadMoreMessage: String? = null,
  val error: String? = null,
)

class DetailViewModel(
  private val repository: TorrentClawRepository,
  private val initialMedia: MediaItem,
  private val prefs: UserPreferencesState,
) : ViewModel() {

  private val defaultSort =
    if (initialMedia.mediaType == MediaType.SHOW) {
      TorrentListSort.RELEASE_DATE_AND_EPISODE
    } else {
      TorrentListSort.BEST_QUALITY
    }

  private val _uiState =
    MutableStateFlow(
      MediaDetailUiState(
        media = initialMedia,
        filter = TorrentListFilterState(sortBy = defaultSort),
        isLoading = true,
      )
    )
  val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

  private val autoFetchedSeasons = mutableSetOf<Int>()
  private val autoFetchedEpisodes = mutableSetOf<Pair<Int, Int>>()

  init {
    loadDetail(isRefresh = false)
  }

  fun loadDetail(isRefresh: Boolean = false) {
    val season = _uiState.value.selectedSeason
    val episode = _uiState.value.selectedEpisode
    if (isRefresh) {
      autoFetchedSeasons.clear()
      autoFetchedEpisodes.clear()
    }
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = !isRefresh && it.allTorrents.isEmpty(),
          isRefreshing = isRefresh,
          isLoadingMoreTorrents = false,
          loadMoreMessage = null,
          error = null,
        )
      }
      repository.loadMediaDetail(initialMedia.id, initialMedia, season, episode, forceRefresh = isRefresh).fold(
        onSuccess = { bundle ->
          val media = bundle.media
          val effectiveDefaultSort =
            if (media.mediaType == MediaType.SHOW) {
              TorrentListSort.RELEASE_DATE_AND_EPISODE
            } else {
              TorrentListSort.BEST_QUALITY
            }
          val activeFilter =
            if (_uiState.value.filter.sortBy == TorrentListSort.BEST_QUALITY &&
              media.mediaType == MediaType.SHOW
            ) {
              _uiState.value.filter.copy(sortBy = effectiveDefaultSort)
            } else {
              _uiState.value.filter
            }

          val seasons = computeAvailableSeasons(media, bundle.torrents)

          _uiState.update { state ->
            applyFiltersInternal(
              state.copy(
                media = media,
                allTorrents = bundle.torrents,
                cast = bundle.cast,
                watchProviders = bundle.watchProviders,
                availableSeasons = seasons,
                filter = activeFilter,
                isLoading = false,
                isRefreshing = false,
                canLoadMoreTorrents = bundle.hasMoreTorrents,
                isLoadingMoreTorrents = false,
                loadMoreMessage = null,
                error = bundle.warningError,
              )
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(
              isLoading = false,
              isRefreshing = false,
              isLoadingMoreTorrents = false,
              error = err.message ?: "Unable to load details",
            )
          }
        },
      )
    }
  }

  fun loadMoreTorrents() {
    val snapshot = _uiState.value
    val media = snapshot.media ?: return
    if (snapshot.isLoading || snapshot.isLoadingMoreTorrents) return

    val focusQuality = snapshot.filter.resolutions.singleOrNull()
    val focusSeason = snapshot.selectedSeason
    val focusEpisode = snapshot.selectedEpisode

    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingMoreTorrents = true, loadMoreMessage = null) }
      repository
        .loadMoreMediaTorrents(
          media = media,
          currentTorrents = snapshot.allTorrents,
          focusQuality = focusQuality,
          focusSeason = focusSeason,
          focusEpisode = focusEpisode,
        )
        .fold(
          onSuccess = { result ->
            val updatedMedia =
              media.copy(torrentCount = maxOf(media.torrentCount, result.torrents.size))
            val seasons = computeAvailableSeasons(updatedMedia, result.torrents)
            _uiState.update { state ->
              applyFiltersInternal(
                state.copy(
                  media = updatedMedia,
                  allTorrents = result.torrents,
                  availableSeasons = seasons,
                  canLoadMoreTorrents = result.hasMoreTorrents,
                  isLoadingMoreTorrents = false,
                  loadMoreMessage = result.warningMessage,
                )
              )
            }
          },
          onFailure = { err ->
            _uiState.update {
              it.copy(
                isLoadingMoreTorrents = false,
                loadMoreMessage = err.message ?: "Unable to load more releases right now.",
              )
            }
          },
        )
    }
  }

  fun selectSeason(season: Int?) {
    val updatedState = applyFiltersInternal(_uiState.value.copy(selectedSeason = season, selectedEpisode = null))
    _uiState.value = updatedState
    if (season != null &&
      season > 0 &&
      updatedState.media?.hasTorrents != false &&
      updatedState.filteredTorrents.size <= 3 &&
      autoFetchedSeasons.add(season)
    ) {
      loadMoreTorrents()
    }
  }

  fun selectEpisode(episode: Int?) {
    val updatedState = applyFiltersInternal(_uiState.value.copy(selectedEpisode = episode))
    _uiState.value = updatedState
    val season = updatedState.selectedSeason
    if (season != null &&
      episode != null &&
      episode > 0 &&
      updatedState.media?.hasTorrents != false &&
      updatedState.filteredTorrents.size <= 2 &&
      autoFetchedEpisodes.add(season to episode)
    ) {
      loadMoreTorrents()
    }
  }

  fun updateFilter(newFilter: TorrentListFilterState) {
    _uiState.update {
      applyFiltersInternal(it.copy(filter = newFilter))
    }
  }

  fun resetFilters() {
    val currentMedia = _uiState.value.media
    val sort =
      if (currentMedia?.mediaType == MediaType.SHOW) {
        TorrentListSort.RELEASE_DATE_AND_EPISODE
      } else {
        TorrentListSort.BEST_QUALITY
      }
    _uiState.update {
      applyFiltersInternal(
        it.copy(
          filter = TorrentListFilterState(sortBy = sort),
          selectedSeason = null,
          selectedEpisode = null,
        )
      )
    }
  }

  private fun computeAvailableSeasons(media: MediaItem, torrents: List<TorrentRelease>): List<Int> {
    val metadataSeasons =
      if (media.mediaType == MediaType.SHOW) {
        (1..(media.numberOfSeasons ?: 0)).toList()
      } else {
        emptyList()
      }
    val torrentSeasons =
      torrents.mapNotNull { it.seasonNumber ?: extractSeasonEpisodeFromName(it.releaseName).first }
    return (metadataSeasons + torrentSeasons).filter { it > 0 }.distinct().sorted()
  }

  private fun applyFiltersInternal(state: MediaDetailUiState): MediaDetailUiState {
    val seasonScoped =
      state.allTorrents.filter {
        matchesSeasonAndEpisode(it, state.selectedSeason, state.selectedEpisode)
      }
    val episodesInSeason =
      state.allTorrents
        .filter { matchesSeasonAndEpisode(it, state.selectedSeason, null) }
        .mapNotNull { it.episodeNumber ?: extractSeasonEpisodeFromName(it.releaseName).second }
        .distinct()
        .sorted()

    val facets = extractTorrentFilterFacets(seasonScoped)
    val sorted = filterAndSortTorrents(seasonScoped, state.filter)
    val rec = computeSmartRecommendation(seasonScoped, prefs)

    return state.copy(
      filteredTorrents = sorted,
      facets = facets,
      availableEpisodes = episodesInSeason,
      recommendation = rec,
    )
  }

  class Factory(
    private val container: AppContainer,
    private val initialMedia: MediaItem,
    private val prefs: UserPreferencesState,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DetailViewModel(container.repository, initialMedia, prefs) as T
    }
  }
}
