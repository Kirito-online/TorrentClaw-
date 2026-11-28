package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.MagnetChooserSheet
import com.example.ui.components.SearchFilterBottomSheet
import com.example.ui.components.TopSearchHeader
import com.example.ui.screens.CatalogListScreen
import com.example.ui.screens.CollectionsScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.MediaDetailScreen
import com.example.ui.screens.SearchResultsScreen
import com.example.ui.theme.TorrentClawTheme
import com.example.ui.viewmodel.CatalogUiEvent
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.DetailViewModel
import com.example.ui.viewmodel.TopSectionTab
import com.example.util.TorrentIntentHelper

class MainActivity : ComponentActivity() {

  private var pendingIntentRef by mutableStateOf<Intent?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingIntentRef = intent
    val container = (application as TorrentClawApp).container

    setContent {
      val catalogVm: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory(container))
      val prefs by catalogVm.prefsState.collectAsStateWithLifecycle()

      TorrentClawTheme(
        themeMode = prefs.themeMode,
        accentPalette = prefs.accentPalette,
      ) {
        TorrentClawNativeApp(
          container = container,
          catalogVm = catalogVm,
          incomingIntent = pendingIntentRef,
          onConsumeIntent = { pendingIntentRef = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingIntentRef = intent
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorrentClawNativeApp(
  container: AppContainer,
  catalogVm: CatalogViewModel,
  incomingIntent: Intent?,
  onConsumeIntent: () -> Unit,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  val prefs by catalogVm.prefsState.collectAsStateWithLifecycle()
  val activeTab by catalogVm.activeTab.collectAsStateWithLifecycle()
  val discoverSnapshot by catalogVm.discoverSnapshot.collectAsStateWithLifecycle()
  val discoverLoading by catalogVm.discoverLoading.collectAsStateWithLifecycle()
  val discoverRefreshing by catalogVm.discoverRefreshing.collectAsStateWithLifecycle()
  val discoverError by catalogVm.discoverError.collectAsStateWithLifecycle()
  val popularState by catalogVm.popularState.collectAsStateWithLifecycle()
  val recentState by catalogVm.recentState.collectAsStateWithLifecycle()
  val collectionsState by catalogVm.collectionsState.collectAsStateWithLifecycle()
  val queryText by catalogVm.queryText.collectAsStateWithLifecycle()
  val searchFocused by catalogVm.searchFocused.collectAsStateWithLifecycle()
  val suggestions by catalogVm.suggestions.collectAsStateWithLifecycle()
  val parsedUrl by catalogVm.parsedExternalUrl.collectAsStateWithLifecycle()
  val searchFilter by catalogVm.searchFilter.collectAsStateWithLifecycle()
  val searchResultsState by catalogVm.searchResultsState.collectAsStateWithLifecycle()
  val selectedMedia by catalogVm.selectedMediaForDetail.collectAsStateWithLifecycle()
  val selectedTorrent by catalogVm.selectedTorrentForChooser.collectAsStateWithLifecycle()
  val installedClients by catalogVm.installedClients.collectAsStateWithLifecycle()
  val isDownloadingTorrent by catalogVm.isDownloadingTorrent.collectAsStateWithLifecycle()

  var showSearchFilterSheet by remember { mutableStateOf(false) }

  BackHandler(enabled = selectedMedia != null || collectionsState.selectedCollection != null || activeTab == TopSectionTab.SEARCH || searchFocused) {
    when {
      selectedMedia != null -> catalogVm.closeMediaDetail()
      searchFocused -> catalogVm.setSearchFocused(false)
      collectionsState.selectedCollection != null -> catalogVm.closeSelectedCollection()
      activeTab == TopSectionTab.SEARCH -> catalogVm.selectTab(TopSectionTab.DISCOVER)
    }
  }

  LaunchedEffect(incomingIntent) {
    val intent = incomingIntent ?: return@LaunchedEffect
    val uriStr = intent.dataString
    val shared =
      if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
    if (!uriStr.isNullOrBlank() || !shared.isNullOrBlank()) {
      catalogVm.handleIncomingIntent(uriStr, shared)
    }
    onConsumeIntent()
  }

  LaunchedEffect(Unit) {
    catalogVm.events.collect { event ->
      when (event) {
        is CatalogUiEvent.Message -> snackbarHostState.showSnackbar(event.text)
        is CatalogUiEvent.OpenTorrentFile -> {
          val ok = TorrentIntentHelper.openTorrentFile(context, event.file, event.preferredPackage)
          if (!ok) {
            snackbarHostState.showSnackbar("No torrent app found to open .torrent file")
          }
        }
      }
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val detailTarget = selectedMedia
      if (detailTarget != null) {
        val detailVm: DetailViewModel =
          viewModel(
            key = "detail_${detailTarget.id}",
            factory = DetailViewModel.Factory(container, detailTarget, prefs),
          )
        var lastSeenApiKey by remember(detailTarget.id) { mutableStateOf(prefs.torrentClawApiKey) }
        LaunchedEffect(prefs.torrentClawApiKey) {
          if (prefs.torrentClawApiKey != lastSeenApiKey) {
            lastSeenApiKey = prefs.torrentClawApiKey
            detailVm.loadDetail(isRefresh = true)
          }
        }
        val detailState by detailVm.uiState.collectAsStateWithLifecycle()
        val currentDetailMedia = detailState.media ?: detailTarget
        MediaDetailScreen(
          uiState = detailState,
          onBack = catalogVm::closeMediaDetail,
          onRefresh = { detailVm.loadDetail(isRefresh = true) },
          onSelectSeason = detailVm::selectSeason,
          onSelectEpisode = detailVm::selectEpisode,
          onUpdateFilter = detailVm::updateFilter,
          onResetFilters = detailVm::resetFilters,
          onTorrentClick = catalogVm::openTorrentChooser,
          onMagnetClick = { rel -> catalogVm.openMagnetDirectly(rel, currentDetailMedia.resolvedContentWebUrl) },
          onLoadMoreTorrents = detailVm::loadMoreTorrents,
          isFavorite = prefs.isFavorite(currentDetailMedia),
          onToggleFavorite = { catalogVm.toggleFavorite(currentDetailMedia) },
        )
      } else {
        Column(modifier = Modifier.fillMaxSize()) {
          TopSearchHeader(
            activeTab = activeTab,
            stats = discoverSnapshot.stats,
            queryText = queryText,
            searchFocused = searchFocused,
            searchFilter = searchFilter,
            suggestions = suggestions,
            parsedExternalUrl = parsedUrl,
            recentSearches = prefs.recentSearches,
            favoriteCount = prefs.favoriteItems.size,
            onSelectTab = catalogVm::selectTab,
            onQueryChange = catalogVm::onQueryChange,
            onFocusChange = catalogVm::setSearchFocused,
            onSubmitSearch = catalogVm::submitSearch,
            onOpenFilterSheet = { showSearchFilterSheet = true },
            onSelectSuggestion = catalogVm::openMediaDetail,
            onRemoveRecent = catalogVm::removeRecentSearch,
            onClearRecents = catalogVm::clearRecentSearches,
            onClearSearch = catalogVm::clearSearch,
          )

          val isTabRefreshing =
            when (activeTab) {
              TopSectionTab.DISCOVER -> discoverRefreshing
              TopSectionTab.POPULAR -> popularState.isLoading && popularState.items.isNotEmpty()
              TopSectionTab.RECENT -> recentState.isLoading && (recentState.items.isNotEmpty() || recentState.torrents.isNotEmpty())
              TopSectionTab.COLLECTIONS -> collectionsState.isLoading && collectionsState.collections.isNotEmpty()
              TopSectionTab.FAVORITES -> false
              TopSectionTab.SEARCH -> searchResultsState.isLoading && (searchResultsState.items.isNotEmpty() || searchResultsState.torrents.isNotEmpty())
            }

          PullToRefreshBox(
            isRefreshing = isTabRefreshing,
            onRefresh = catalogVm::refreshActiveTab,
            modifier = Modifier.weight(1f),
          ) {
            when (activeTab) {
              TopSectionTab.DISCOVER ->
                DiscoverScreen(
                  snapshot = discoverSnapshot,
                  isLoading = discoverLoading,
                  error = discoverError,
                  favoriteItems = prefs.favoriteItems,
                  onMediaClick = catalogVm::openMediaDetail,
                  onToggleFavorite = catalogVm::toggleFavorite,
                  onTorrentClick = catalogVm::openTorrentChooser,
                  onMagnetClick = { rel -> catalogVm.openMagnetDirectly(rel) },
                  onGenreClick = { genre ->
                    catalogVm.applySearchFilter(searchFilter.copy(query = "", genre = genre))
                  },
                  onCollectionClick = catalogVm::openCollection,
                  onSeePopularMovies = { catalogVm.setPopularType(com.example.data.model.MediaType.MOVIE) },
                  onSeePopularShows = { catalogVm.setPopularType(com.example.data.model.MediaType.SHOW) },
                  onSeeRecent = { catalogVm.selectTab(TopSectionTab.RECENT) },
                  onRetry = { catalogVm.loadDiscover(forceRefresh = true) },
                )

              TopSectionTab.POPULAR ->
                CatalogListScreen(
                  title = "Popular",
                  state = popularState,
                  favoriteItems = prefs.favoriteItems,
                  onSelectType = catalogVm::setPopularType,
                  onMediaClick = catalogVm::openMediaDetail,
                  onToggleFavorite = catalogVm::toggleFavorite,
                  onTorrentClick = catalogVm::openTorrentChooser,
                  onMagnetClick = { rel -> catalogVm.openMagnetDirectly(rel) },
                  onLoadMore = { catalogVm.loadPopular(reset = false) },
                  onRetry = { catalogVm.loadPopular(reset = true) },
                )

              TopSectionTab.RECENT ->
                CatalogListScreen(
                  title = "Recent",
                  state = recentState,
                  favoriteItems = prefs.favoriteItems,
                  onSelectType = catalogVm::setRecentType,
                  onMediaClick = catalogVm::openMediaDetail,
                  onToggleFavorite = catalogVm::toggleFavorite,
                  onTorrentClick = catalogVm::openTorrentChooser,
                  onMagnetClick = { rel -> catalogVm.openMagnetDirectly(rel) },
                  onLoadMore = { catalogVm.loadRecent(reset = false) },
                  onRetry = { catalogVm.loadRecent(reset = true) },
                )

              TopSectionTab.COLLECTIONS ->
                CollectionsScreen(
                  state = collectionsState,
                  favoriteItems = prefs.favoriteItems,
                  onOpenCollection = catalogVm::openCollection,
                  onBackFromCollection = catalogVm::closeSelectedCollection,
                  onMediaClick = catalogVm::openMediaDetail,
                  onToggleFavorite = catalogVm::toggleFavorite,
                  onRetry = catalogVm::loadCollections,
                )

              TopSectionTab.FAVORITES ->
                FavoritesScreen(
                  favorites = prefs.favoriteItems,
                  onMediaClick = catalogVm::openMediaDetail,
                  onToggleFavorite = catalogVm::toggleFavorite,
                  onClearFavorites = catalogVm::clearFavorites,
                  onExploreDiscover = { catalogVm.selectTab(TopSectionTab.DISCOVER) },
                )

              TopSectionTab.SEARCH ->
                SearchResultsScreen(
                  filter = searchFilter,
                  state = searchResultsState,
                  favoriteItems = prefs.favoriteItems,
                  onUpdateType = { t -> catalogVm.applySearchFilter(searchFilter.copy(mediaType = t)) },
                  onOpenFilterSheet = { showSearchFilterSheet = true },
                  onResetFilters = catalogVm::resetSearchFilters,
                  onMediaClick = catalogVm::openMediaDetail,
                  onToggleFavorite = catalogVm::toggleFavorite,
                  onTorrentClick = catalogVm::openTorrentChooser,
                  onMagnetClick = { rel -> catalogVm.openMagnetDirectly(rel) },
                  onLoadMore = catalogVm::loadMoreSearch,
                  onRetry = { catalogVm.applySearchFilter(searchFilter) },
                )
            }
          }
        }
      }
    }
  }

  if (showSearchFilterSheet) {
    SearchFilterBottomSheet(
      filter = searchFilter,
      availableGenres = discoverSnapshot.genres,
      onApply = catalogVm::applySearchFilter,
      onReset = catalogVm::resetSearchFilters,
      onDismiss = { showSearchFilterSheet = false },
    )
  }

  selectedTorrent?.let { release ->
    MagnetChooserSheet(
      release = release,
      installedClients = installedClients,
      preferredPackage = prefs.preferredTorrentClientPackage,
      isDownloading = isDownloadingTorrent,
      hasSavedApiKey = !prefs.torrentClawApiKey.isNullOrBlank(),
      onDismiss = catalogVm::dismissTorrentChooser,
      onOpenInClient = { pkg, rememberDefault ->
        catalogVm.launchInTorrentClient(release, pkg, rememberDefault)
      },
      onCopyMagnet = { catalogVm.copyMagnet(release) },
      onCopyHash = { catalogVm.copyInfoHash(release) },
      onDownloadTorrentFile = { catalogVm.downloadTorrentFile(release) },
      onShare = { catalogVm.shareRelease(release, selectedMedia?.resolvedContentWebUrl) },
      onOpenOnWeb = { catalogVm.openOnTorrentClawWeb(release, selectedMedia?.resolvedContentWebUrl) },
      onCopyReleaseTitle = { catalogVm.copyReleaseTitle(release) },
      onSaveApiKey = { key -> catalogVm.saveTorrentClawApiKey(key) },
    )
  }
}
