package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.core.preferences.ListingLayout
import com.example.data.model.SearchFilter
import com.example.data.model.TorrentRelease
import com.example.ui.components.MissingTorrentClientDialog
import com.example.ui.components.TorrentClientPickerDialog
import com.example.ui.components.TorrentInspectorSheet
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MediaDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TorrentClawTheme
import com.example.ui.viewmodel.AlertsViewModel
import com.example.ui.viewmodel.AppEvent
import com.example.ui.viewmodel.DetailViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SearchViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.util.InstalledTorrentClient
import com.example.util.TorrentIntentHelper
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object SearchRoute

@Serializable data object LibraryRoute

@Serializable data object AlertsRoute

@Serializable data object SettingsRoute

@Serializable data class MediaDetailRoute(val mediaId: String)

private data class TopTab(
  val route: Any,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

class MainActivity : ComponentActivity() {

  private var mainViewModelRef: MainViewModel? = null
  private var pendingIntentRef by mutableStateOf<Intent?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingIntentRef = intent
    val container = (application as TorrentClawApp).container

    setContent {
      val mainVm: MainViewModel = viewModel(factory = MainViewModel.Factory(container))
      mainViewModelRef = mainVm

      val prefs by mainVm.preferences.collectAsStateWithLifecycle()
      val account by mainVm.account.collectAsStateWithLifecycle()

      TorrentClawTheme(
        themeMode = prefs.themeMode,
        accentPalette = prefs.accentPalette,
      ) {
        if (!prefs.hasCompletedOnboarding) {
          val busy by mainVm.authBusy.collectAsStateWithLifecycle()
          val authErr by mainVm.authError.collectAsStateWithLifecycle()
          AuthScreen(
            isBusy = busy,
            errorMessage = authErr,
            onSignInWithApiKey = mainVm::signInWithApiKey,
            onContinueWithoutAccount = mainVm::continueWithoutAccount,
            onClearError = mainVm::clearAuthError,
          )
        } else {
          TorrentClawMainScaffold(
            container = container,
            mainVm = mainVm,
            incomingIntent = pendingIntentRef,
            onConsumeIntent = { pendingIntentRef = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingIntentRef = intent
  }
}

@Composable
private fun TorrentClawMainScaffold(
  container: AppContainer,
  mainVm: MainViewModel,
  incomingIntent: Intent?,
  onConsumeIntent: () -> Unit,
) {
  val context = LocalContext.current
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val prefs by mainVm.preferences.collectAsStateWithLifecycle()
  val account by mainVm.account.collectAsStateWithLifecycle()
  val debridAccounts by mainVm.debridAccounts.collectAsStateWithLifecycle()
  val selectedTorrent by mainVm.selectedTorrentForSheet.collectAsStateWithLifecycle()
  val trueSpecScanning by mainVm.trueSpecScanning.collectAsStateWithLifecycle()

  val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
  val searchVm: SearchViewModel = viewModel(factory = SearchViewModel.Factory(container))
  val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(container))
  val alertsVm: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory(container))
  val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))

  var clientPickerState by
    remember { mutableStateOf<Pair<TorrentRelease, List<InstalledTorrentClient>>?>(null) }
  var missingClientRelease by remember { mutableStateOf<TorrentRelease?>(null) }

  // Handle incoming Intents (deep links, magnet links, shared URLs, widget taps)
  LaunchedEffect(incomingIntent) {
    val intent = incomingIntent ?: return@LaunchedEffect
    when {
      intent.data != null -> mainVm.handleIncomingUri(intent.data!!)
      intent.action == Intent.ACTION_SEND -> {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { mainVm.handleSharedText(it) }
      }
      intent.hasExtra("open_media_id") -> {
        val id = intent.getStringExtra("open_media_id")
        if (!id.isNullOrBlank()) {
          navController.navigate(MediaDetailRoute(id))
        }
      }
      intent.getStringExtra("open_tab") == "alerts" -> {
        navController.navigate(AlertsRoute) {
          popUpTo(navController.graph.findStartDestination().id) { saveState = true }
          launchSingleTop = true
          restoreState = true
        }
      }
    }
    onConsumeIntent()
  }

  // Handle ViewModel one-shot UI events
  LaunchedEffect(Unit) {
    mainVm.events.collect { event ->
      when (event) {
        is AppEvent.Message -> snackbarHostState.showSnackbar(event.text)
        is AppEvent.NavigateToSearch -> {
          searchVm.submitSearch(event.query)
          navController.navigate(SearchRoute) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
          }
        }
        is AppEvent.OpenTorrentInspector -> mainVm.inspectTorrent(event.release)
        is AppEvent.ChooseTorrentClient -> clientPickerState = event.release to event.clients
        is AppEvent.MissingTorrentClient -> missingClientRelease = event.release
      }
    }
  }

  val tabs = remember {
    listOf(
      TopTab(HomeRoute, "Home", Icons.Rounded.Home, Icons.Outlined.Home, "tab_home"),
      TopTab(SearchRoute, "Search", Icons.Rounded.Search, Icons.Outlined.Search, "tab_search"),
      TopTab(LibraryRoute, "Library", Icons.Rounded.Bookmarks, Icons.Outlined.Bookmarks, "tab_library"),
      TopTab(AlertsRoute, "Alerts", Icons.Rounded.Notifications, Icons.Outlined.Notifications, "tab_alerts"),
      TopTab(SettingsRoute, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "tab_settings"),
    )
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = navBackStackEntry?.destination

  val navigateToTab: (Any) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 640.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            tabs.forEach { tab ->
              val selected = currentDest?.hasRoute(tab.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label, maxLines = 1) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            tabs.forEach { tab ->
              val selected = currentDest?.hasRoute(tab.route::class) == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
              val homeState by homeVm.uiState.collectAsStateWithLifecycle()
              HomeScreen(
                uiState = homeState,
                account = account,
                posterSize = prefs.posterSize,
                density = prefs.density,
                onSearchClick = { navigateToTab(SearchRoute) },
                onMediaClick = { media -> navController.navigate(MediaDetailRoute(media.id)) },
                onGenreClick = { genre ->
                  searchVm.applyFilter(
                    SearchFilter(query = "", genre = genre, sortBy = prefs.defaultSort),
                    executeNow = true,
                  )
                  navigateToTab(SearchRoute)
                },
                onCollectionClick = { col ->
                  searchVm.submitSearch(col.name)
                  navigateToTab(SearchRoute)
                },
                onRefresh = { homeVm.refresh() },
                onAccountClick = { navigateToTab(SettingsRoute) },
              )
            }

            composable<SearchRoute> {
              val homeState by homeVm.uiState.collectAsStateWithLifecycle()
              val queryText by searchVm.queryText.collectAsStateWithLifecycle()
              val filter by searchVm.filter.collectAsStateWithLifecycle()
              val hasActiveSearch by searchVm.hasActiveSearch.collectAsStateWithLifecycle()
              val suggestions by searchVm.suggestions.collectAsStateWithLifecycle()
              val parsedUrl by searchVm.parsedUrl.collectAsStateWithLifecycle()
              val history by searchVm.searchHistory.collectAsStateWithLifecycle()
              val pagedMedia = searchVm.pagedResults.collectAsLazyPagingItems()
              val directTorrents by searchVm.directTorrents.collectAsStateWithLifecycle()
              val compareSelection by searchVm.compareSelection.collectAsStateWithLifecycle()

              SearchScreen(
                queryText = queryText,
                filter = filter,
                hasActiveSearch = hasActiveSearch,
                suggestions = suggestions,
                parsedUrl = parsedUrl,
                searchHistory = history,
                pagedMedia = pagedMedia,
                directTorrents = directTorrents,
                availableGenres = homeState.snapshot.genres,
                listingLayout = prefs.defaultListingLayout,
                density = prefs.density,
                autocompleteEnabled = prefs.autocompleteEnabled,
                compareSelection = compareSelection,
                onQueryChange = searchVm::onQueryChange,
                onSubmitSearch = searchVm::submitSearch,
                onApplyFilter = { searchVm.applyFilter(it) },
                onResetFilters = searchVm::resetAllFilters,
                onCancelOrClear = searchVm::cancelOrClearSearch,
                onDeleteHistory = searchVm::deleteHistoryEntry,
                onClearHistory = searchVm::clearHistory,
                onMediaClick = { media -> navController.navigate(MediaDetailRoute(media.id)) },
                onToggleLayout = {
                  val next =
                    if (prefs.defaultListingLayout == ListingLayout.COMPACT) ListingLayout.EXPANDED
                    else ListingLayout.COMPACT
                  settingsVm.setListingLayout(next)
                },
                onInspectTorrent = mainVm::inspectTorrent,
                onQuickMagnet = mainVm::triggerMagnetAction,
                onQuickCopy = mainVm::copyMagnet,
                onToggleCompare = searchVm::toggleCompare,
                onClearCompare = searchVm::clearCompare,
              )
            }

            composable<LibraryRoute> {
              val savedMedia by libraryVm.savedMedia.collectAsStateWithLifecycle()
              val savedTorrents by libraryVm.savedTorrents.collectAsStateWithLifecycle()
              val history by libraryVm.downloadHistory.collectAsStateWithLifecycle()

              LibraryScreen(
                savedMedia = savedMedia,
                savedTorrents = savedTorrents,
                downloadHistory = history,
                onOpenMedia = { id -> navController.navigate(MediaDetailRoute(id)) },
                onRemoveMedia = libraryVm::removeSavedMedia,
                onInspectSavedTorrent = { st ->
                  with(libraryVm) { mainVm.inspectTorrent(st.toDomain()) }
                },
                onRemoveSavedTorrent = libraryVm::removeSavedTorrent,
                onInspectHistoryItem = { hist ->
                  with(libraryVm) { mainVm.inspectTorrent(hist.toDomain()) }
                },
                onOpenHistoryMagnet = { hist ->
                  with(libraryVm) { mainVm.triggerMagnetAction(hist.toDomain()) }
                },
                onDeleteHistoryItem = libraryVm::deleteHistoryEntry,
                onClearHistory = libraryVm::clearHistory,
              )
            }

            composable<AlertsRoute> {
              val alerts by alertsVm.alerts.collectAsStateWithLifecycle()
              val checking by alertsVm.isChecking.collectAsStateWithLifecycle()
              val summary by alertsVm.lastCheckSummary.collectAsStateWithLifecycle()

              AlertsScreen(
                alerts = alerts,
                account = account,
                isChecking = checking,
                lastCheckSummary = summary,
                onCheckNow = alertsVm::checkNow,
                onToggleAlert = alertsVm::setEnabled,
                onDeleteAlert = alertsVm::deleteAlert,
                onUpdateAlertPrefs = alertsVm::updatePreferences,
                onOpenMedia = { id -> navController.navigate(MediaDetailRoute(id)) },
                onNavigateSearch = { navigateToTab(SearchRoute) },
              )
            }

            composable<SettingsRoute> {
              val installedClients by settingsVm.installedClients.collectAsStateWithLifecycle()
              val cacheSize by settingsVm.cacheSizeLabel.collectAsStateWithLifecycle()
              val debridStatus by settingsVm.debridStatusMessage.collectAsStateWithLifecycle()

              SettingsScreen(
                prefs = prefs,
                account = account,
                debridAccounts = debridAccounts,
                settingsViewModel = settingsVm,
                installedClientsCount = installedClients.size,
                cacheSizeLabel = cacheSize,
                debridStatusMessage = debridStatus,
                onSignInOrSwitch = mainVm::switchAccount,
                onLogout = mainVm::logout,
              )
            }

            composable<MediaDetailRoute> { backStackEntry ->
              val route: MediaDetailRoute = backStackEntry.toRoute()
              val detailVm: DetailViewModel =
                viewModel(
                  key = "detail_${route.mediaId}",
                  factory = DetailViewModel.Factory(container, route.mediaId),
                )
              val detailState by detailVm.uiState.collectAsStateWithLifecycle()
              val compareSelection by detailVm.compareReleases.collectAsStateWithLifecycle()

              MediaDetailScreen(
                uiState = detailState,
                compareSelection = compareSelection,
                listingLayout = prefs.defaultListingLayout,
                density = prefs.density,
                onBack = { navController.popBackStack() },
                onRetry = { detailVm.loadDetail() },
                onSelectSeason = detailVm::selectSeason,
                onSelectEpisode = detailVm::selectEpisode,
                onUpdateTorrentFilter = detailVm::updateTorrentFilter,
                onResetTorrentFilters = detailVm::resetTorrentFilters,
                onToggleFavorite = detailVm::toggleFavorite,
                onToggleWatchlist = detailVm::toggleWatchlist,
                onToggleAlert = {
                  detailVm.toggleAlert { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                  }
                },
                onToggleLayout = {
                  val next =
                    if (prefs.defaultListingLayout == ListingLayout.COMPACT) ListingLayout.EXPANDED
                    else ListingLayout.COMPACT
                  settingsVm.setListingLayout(next)
                },
                onInspectTorrent = mainVm::inspectTorrent,
                onQuickMagnet = mainVm::triggerMagnetAction,
                onQuickCopy = mainVm::copyMagnet,
                onToggleCompare = detailVm::toggleCompare,
                onClearCompare = detailVm::clearCompare,
              )
            }
          }
        }
      }
    }
  }

  // Torrent Inspector Bottom Sheet
  selectedTorrent?.let { release ->
    val isSaved by
      container.libraryRepository
        .observeIsTorrentSaved(release.infoHash)
        .collectAsStateWithLifecycle(initialValue = false)

    TorrentInspectorSheet(
      release = release,
      isSaved = isSaved,
      isScanningTrueSpec = trueSpecScanning,
      debridAccounts = debridAccounts,
      onDismiss = mainVm::dismissTorrentInspector,
      onOpenMagnet = { mainVm.openMagnetWithClientSelection(release, prefs.preferredTorrentClientPackage) },
      onCopyMagnet = { mainVm.copyMagnet(release) },
      onCopyHash = {
        TorrentIntentHelper.copyToClipboard(context, "Info Hash", release.infoHash)
        scope.launch { snackbarHostState.showSnackbar("Info hash copied") }
      },
      onDownloadTorrentFile = { mainVm.downloadTorrentFile(release) },
      onShare = { TorrentIntentHelper.shareTorrentRelease(context, release) },
      onToggleSave = {
        scope.launch {
          container.libraryRepository.toggleSavedTorrent(release, isSaved)
        }
      },
      onRequestTrueSpecScan = { mainVm.requestTrueSpecScan(release) },
      onSendToDebrid = { provider -> mainVm.sendToDebrid(provider, release) },
    )
  }

  // Installed Torrent Client Picker
  clientPickerState?.let { (release, clients) ->
    TorrentClientPickerDialog(
      clients = clients,
      onSelect = { client, saveDefault ->
        mainVm.launchMagnetInPackage(release, client.packageName, saveDefault)
        clientPickerState = null
      },
      onDismiss = { clientPickerState = null },
    )
  }

  // Graceful Missing Client Dialog
  missingClientRelease?.let { release ->
    MissingTorrentClientDialog(
      release = release,
      onCopyMagnet = { mainVm.copyMagnet(release) },
      onShareMagnet = { TorrentIntentHelper.shareTorrentRelease(context, release) },
      onDismiss = { missingClientRelease = null },
    )
  }
}
