package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.model.ApiHealthState
import com.example.data.model.MediaItem
import com.example.data.repository.DiscoverySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
  val isLoading: Boolean = true,
  val isRefreshing: Boolean = false,
  val snapshot: DiscoverySnapshot = DiscoverySnapshot(),
  val recommendedForYou: List<MediaItem> = emptyList(),
  val apiHealth: ApiHealthState = ApiHealthState.Connected,
  val errorMessage: String? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

  private val _isLoading = MutableStateFlow(true)
  private val _isRefreshing = MutableStateFlow(false)
  private val _snapshot = MutableStateFlow(DiscoverySnapshot())
  private val _error = MutableStateFlow<String?>(null)

  val uiState: StateFlow<HomeUiState> =
    combine(
        _isLoading,
        _isRefreshing,
        _snapshot,
        container.libraryRepository.savedMediaFlow,
        container.mediaRepository.apiHealth,
      ) { loading, refreshing, snap, savedMedia, health ->
        val favoriteGenres =
          savedMedia
            .flatMap { it.genresCsv.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()

        val allDiscovery = (snap.trending + snap.popularMovies + snap.popularShows).distinctBy { it.id }
        val recommended =
          if (favoriteGenres.isNotEmpty()) {
            allDiscovery
              .filter { item -> savedMedia.none { it.id == item.id } }
              .sortedByDescending { item ->
                item.genres.sumOf { g -> favoriteGenres[g] ?: 0 } * 10 + (item.bestQualityScore ?: 0)
              }
              .take(12)
          } else {
            allDiscovery
              .sortedByDescending { (it.bestQualityScore ?: 0) + ((it.rating ?: 0.0) * 5).toInt() }
              .take(12)
          }

        HomeUiState(
          isLoading = loading,
          isRefreshing = refreshing,
          snapshot = snap,
          recommendedForYou = recommended,
          apiHealth = health,
          errorMessage = _error.value,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

  init {
    refresh(initial = true)
  }

  fun refresh(initial: Boolean = false) {
    viewModelScope.launch {
      if (initial) _isLoading.value = true else _isRefreshing.value = true
      _error.value = null
      runCatching { container.mediaRepository.loadDiscovery(forceRefresh = !initial) }
        .onSuccess { snap ->
          _snapshot.value = snap
          if (snap.trending.isEmpty() && snap.popularMovies.isEmpty() && snap.popularShows.isEmpty()) {
            val health = container.mediaRepository.apiHealth.value
            if (health is ApiHealthState.Error) _error.value = health.message
            else if (health is ApiHealthState.RateLimited) {
              _error.value = "Rate limit reached. Please retry in ${health.retryAfterSeconds}s."
            }
          }
        }
        .onFailure { err ->
          _error.value = err.localizedMessage ?: "Failed to connect to TorrentClaw"
        }
      _isLoading.value = false
      _isRefreshing.value = false
    }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
  }
}
