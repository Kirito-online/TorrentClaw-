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
import com.example.ui.components.WebsiteTopUrlBar
import com.example.ui.screens.WebsitePageHost
import com.example.ui.theme.TorrentClawTheme
import com.example.ui.viewmodel.BrowserUiEvent
import com.example.ui.viewmodel.WebsiteBrowserViewModel
import com.example.util.TorrentIntentHelper

class MainActivity : ComponentActivity() {

  private var pendingIntentRef by mutableStateOf<Intent?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingIntentRef = intent
    val container = (application as TorrentClawApp).container

    setContent {
      val browserVm: WebsiteBrowserViewModel =
        viewModel(factory = WebsiteBrowserViewModel.Factory(container))
      val prefs by browserVm.prefsState.collectAsStateWithLifecycle()

      TorrentClawTheme(
        themeMode = prefs.themeMode,
        accentPalette = prefs.accentPalette,
      ) {
        TorrentClawWebsiteApp(
          browserVm = browserVm,
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
private fun TorrentClawWebsiteApp(
  browserVm: WebsiteBrowserViewModel,
  incomingIntent: Intent?,
  onConsumeIntent: () -> Unit,
) {
  val context = LocalContext.current
  val uiState by browserVm.uiState.collectAsStateWithLifecycle()
  val magnetTarget by browserVm.magnetTarget.collectAsStateWithLifecycle()
  val installedClients by browserVm.installedClients.collectAsStateWithLifecycle()
  val isDownloadingTorrent by browserVm.isDownloadingTorrent.collectAsStateWithLifecycle()
  val prefs by browserVm.prefsState.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  var searchQuery by remember(uiState.currentUrl) {
    val initialQuery =
      uiState.page?.searchForm?.fields?.firstOrNull { !it.isHidden }?.value.orEmpty()
    mutableStateOf(initialQuery)
  }

  BackHandler(enabled = uiState.canGoBack) {
    browserVm.goBack()
  }

  LaunchedEffect(incomingIntent) {
    val intent = incomingIntent ?: return@LaunchedEffect
    val uriStr = intent.dataString
    val shared =
      if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
    if (!uriStr.isNullOrBlank() || !shared.isNullOrBlank()) {
      browserVm.handleIncomingIntent(uriStr, shared)
    }
    onConsumeIntent()
  }

  LaunchedEffect(Unit) {
    browserVm.events.collect { event ->
      when (event) {
        is BrowserUiEvent.Message -> snackbarHostState.showSnackbar(event.text)
        is BrowserUiEvent.OpenTorrentFile -> {
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
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
    ) {
      WebsiteTopUrlBar(
        currentUrl = uiState.currentUrl,
        canGoBack = uiState.canGoBack,
        isLoading = uiState.isNavigating || uiState.isInitialLoading,
        onBack = { browserVm.goBack() },
        onRefresh = { browserVm.refresh() },
        onShare = { browserVm.shareCurrentPage() },
        onCopyUrl = { browserVm.copyCurrentPageUrl() },
        onNavigateUrl = { typed -> browserVm.navigateToTypedUrl(typed) },
      )

      PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { browserVm.refresh() },
        modifier = Modifier.weight(1f),
      ) {
        WebsitePageHost(
          page = uiState.page,
          isInitialLoading = uiState.isInitialLoading,
          errorMessage = uiState.errorMessage,
          hasSessionCookies = uiState.hasSessionCookies,
          searchQuery = searchQuery,
          onSearchQueryChange = { searchQuery = it },
          onSubmitSearch = { browserVm.submitSearch(searchQuery) },
          onNavigate = { url -> browserVm.navigateTo(url) },
          onSubmitForm = { form, values -> browserVm.submitForm(form, values) },
          onTorrentClick = { item -> browserVm.openTorrentItem(item) },
          onRetry = { browserVm.refresh() },
          onGoBack = { browserVm.goBack() },
        )
      }
    }
  }

  magnetTarget?.let { target ->
    MagnetChooserSheet(
      target = target,
      installedClients = installedClients,
      preferredPackage = prefs.preferredTorrentClientPackage,
      isDownloading = isDownloadingTorrent,
      onDismiss = browserVm::dismissMagnetSheet,
      onOpenInClient = { pkg, rememberDefault ->
        browserVm.launchInTorrentClient(target, pkg, rememberDefault)
      },
      onCopyLink = { browserVm.copyTargetLink(target) },
      onDownloadTorrentFile = { browserVm.downloadAndOpenTorrentFile(target) },
      onShareLink = { browserVm.shareTargetLink(target) },
    )
  }
}
