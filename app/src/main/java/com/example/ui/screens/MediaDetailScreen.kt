package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.preferences.InterfaceDensity
import com.example.core.preferences.ListingLayout
import com.example.data.model.MediaType
import com.example.data.model.TorrentRelease
import com.example.ui.components.ReleaseComparisonSheet
import com.example.ui.components.SmartRecommendationCard
import com.example.ui.components.TorrentClawEmptyState
import com.example.ui.components.TorrentReleaseRow
import com.example.ui.theme.LocalTorrentClawColors
import com.example.ui.viewmodel.DetailUiState
import com.example.util.TorrentIntentHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDetailScreen(
  uiState: DetailUiState,
  compareSelection: List<TorrentRelease>,
  listingLayout: ListingLayout,
  density: InterfaceDensity,
  onBack: () -> Unit,
  onRetry: () -> Unit,
  onSelectSeason: (Int) -> Unit,
  onSelectEpisode: (Int?) -> Unit,
  onToggleFavorite: () -> Unit,
  onToggleWatchlist: () -> Unit,
  onToggleAlert: () -> Unit,
  onToggleLayout: () -> Unit,
  onInspectTorrent: (TorrentRelease) -> Unit,
  onQuickMagnet: (TorrentRelease) -> Unit,
  onQuickCopy: (TorrentRelease) -> Unit,
  onToggleCompare: (TorrentRelease) -> Unit,
  onClearCompare: () -> Unit,
) {
  val context = LocalContext.current
  val semantic = LocalTorrentClawColors.current
  var isCompareMode by remember { mutableStateOf(false) }
  var showComparisonSheet by remember { mutableStateOf(false) }

  if (uiState.isLoading && uiState.bundle == null) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(10.dp))
        Text("Loading title & torrents…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    return
  }

  val bundle = uiState.bundle
  if (bundle == null) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
      }
      TorrentClawEmptyState(
        title = "Details Unavailable",
        message = uiState.error ?: "Unable to fetch details from TorrentClaw.",
        actionLabel = "Retry",
        onAction = onRetry,
      )
    }
    return
  }

  val media = bundle.media

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(bottom = 32.dp),
  ) {
    // Backdrop & Top Actions
    item {
      Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
        val backdrop = media.backdropUrl ?: media.posterUrl
        if (!backdrop.isNullOrBlank()) {
          AsyncImage(
            model = backdrop,
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(
                  listOf(
                    Color.Black.copy(alpha = 0.55f),
                    Color.Transparent,
                    MaterialTheme.colorScheme.background,
                  )
                )
              ),
        )

        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(color = Color.Black.copy(alpha = 0.52f), shape = CircleShape) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = Color.Black.copy(alpha = 0.52f), shape = CircleShape) {
              IconButton(onClick = { TorrentIntentHelper.shareMediaItem(context, media) }) {
                Icon(Icons.Rounded.Share, contentDescription = "Share title", tint = Color.White)
              }
            }
            Surface(color = Color.Black.copy(alpha = 0.52f), shape = CircleShape) {
              IconButton(onClick = onToggleWatchlist, modifier = Modifier.testTag("detail_watchlist_button")) {
                Icon(
                  if (uiState.isWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                  contentDescription = "Watchlist",
                  tint = if (uiState.isWatchlist) MaterialTheme.colorScheme.primary else Color.White,
                )
              }
            }
            Surface(color = Color.Black.copy(alpha = 0.52f), shape = CircleShape) {
              IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("detail_favorite_button")) {
                Icon(
                  if (uiState.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                  contentDescription = "Favorite",
                  tint = if (uiState.isFavorite) semantic.suspicious else Color.White,
                )
              }
            }
            Surface(color = Color.Black.copy(alpha = 0.52f), shape = CircleShape) {
              IconButton(onClick = onToggleAlert, modifier = Modifier.testTag("detail_alert_button")) {
                Icon(
                  if (uiState.isFollowedAlert) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                  contentDescription = "Follow alerts",
                  tint = if (uiState.isFollowedAlert) semantic.hdrGold else Color.White,
                )
              }
            }
          }
        }

        // Poster + Core Title Block overlapping backdrop bottom
        Row(
          modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 16.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          Box(
            modifier =
              Modifier.width(96.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
          ) {
            if (!media.posterUrl.isNullOrBlank()) {
              AsyncImage(
                model = media.posterUrl,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
          Spacer(Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = media.title,
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            media.originalTitle?.let { orig ->
              Text(
                text = orig,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              media.year?.let { Text("$it", style = MaterialTheme.typography.labelLarge) }
              Text(media.mediaType.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
              media.runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
                Text("${mins}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              media.rating?.takeIf { it > 0.0 }?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.Star, contentDescription = null, tint = semantic.hdrGold, modifier = Modifier.size(14.dp))
                  Spacer(Modifier.width(2.dp))
                  Text(String.format("%.1f", rating), style = MaterialTheme.typography.labelLarge)
                }
              }
            }
          }
        }
      }
    }

    // Genres, Collection & Synopsis
    item {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (media.genres.isNotEmpty() || media.collectionName != null) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            media.collectionName?.let { col ->
              AssistChip(
                onClick = {},
                leadingIcon = { Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text(col) },
              )
            }
            media.genres.forEach { genre ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = genre,
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
              }
            }
          }
          Spacer(Modifier.height(10.dp))
        }

        if (media.overview.isNotBlank()) {
          Text(
            text = media.overview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Streaming Availability (Watch Providers)
    if (bundle.watchProviders.isNotEmpty()) {
      item {
        Text(
          text = "Streaming Availability",
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(bundle.watchProviders) { provider ->
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
              shape = RoundedCornerShape(10.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (!provider.logoUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = provider.logoUrl,
                    contentDescription = provider.providerName,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)),
                  )
                  Spacer(Modifier.width(6.dp))
                }
                Text(provider.providerName, style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
        Spacer(Modifier.height(10.dp))
      }
    }

    // Cast & Crew Rail
    if (bundle.credits.isNotEmpty()) {
      item {
        Text(
          text = "Cast & Crew",
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(bundle.credits.take(15)) { person ->
            Column(
              modifier = Modifier.width(84.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Box(
                modifier =
                  Modifier.size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
              ) {
                if (!person.profileUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = person.profileUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              Spacer(Modifier.height(4.dp))
              Text(person.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(person.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
        Spacer(Modifier.height(12.dp))
      }
    }

    // TV Show Season & Episode Selector
    if (media.mediaType == MediaType.SHOW && media.seasons.isNotEmpty()) {
      item {
        Text(
          text = "Seasons & Episodes",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(media.seasons) { seasonNum ->
            FilterChip(
              selected = uiState.selectedSeason == seasonNum,
              onClick = { onSelectSeason(seasonNum) },
              label = { Text("Season $seasonNum") },
            )
          }
        }
        val activeSeason = uiState.selectedSeason
        val episodes = if (activeSeason != null) media.episodesPerSeason[activeSeason].orEmpty() else emptyList()
        if (episodes.isNotEmpty()) {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            item {
              FilterChip(
                selected = uiState.selectedEpisode == null,
                onClick = { onSelectEpisode(null) },
                label = { Text("All Episodes") },
              )
            }
            items(episodes) { ep ->
              FilterChip(
                selected = uiState.selectedEpisode == ep.episode,
                onClick = { onSelectEpisode(ep.episode) },
                label = { Text("E${ep.episode}${ep.title?.let { " • $it" }.orEmpty()}") },
              )
            }
          }
        }
        Spacer(Modifier.height(8.dp))
      }
    }

    // Smart Recommendation Card
    uiState.recommendation?.let { rec ->
      item {
        SmartRecommendationCard(
          recommendation = rec,
          onInspect = { onInspectTorrent(rec.release) },
          onOpenMagnet = { onQuickMagnet(rec.release) },
        )
        Spacer(Modifier.height(12.dp))
      }
    }

    // Torrents Section Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Available Torrents (${bundle.torrents.size})",
            style = MaterialTheme.typography.titleLarge,
          )
          if (uiState.isLoading) {
            Text("Updating episode releases…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
          }
        }
        if (bundle.torrents.size >= 2) {
          TextButton(onClick = {
            isCompareMode = !isCompareMode
            if (!isCompareMode) onClearCompare()
          }) {
            Icon(Icons.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isCompareMode) "Done" else "Compare")
          }
        }
        IconButton(onClick = onToggleLayout) {
          Icon(
            if (listingLayout == ListingLayout.COMPACT) Icons.Rounded.ViewAgenda else Icons.Rounded.ViewStream,
            contentDescription = "Toggle layout",
          )
        }
      }
      if (isCompareMode && compareSelection.size >= 2) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${compareSelection.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Button(onClick = { showComparisonSheet = true }) { Text("Compare") }
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }

    if (bundle.torrents.isEmpty()) {
      item {
        TorrentClawEmptyState(
          title = "No Indexed Releases Yet",
          message = "TorrentClaw hasn't indexed matching torrents for this selection yet. Follow this title to receive an alert as soon as a verified release appears.",
          actionLabel = if (uiState.isFollowedAlert) "Alert Active" else "Follow for Alerts",
          onAction = onToggleAlert,
        )
      }
    } else {
      items(bundle.torrents, key = { it.infoHash }) { release ->
        TorrentReleaseRow(
          release = release,
          layout = listingLayout,
          density = density,
          isCompareMode = isCompareMode,
          isSelectedForCompare = compareSelection.any { it.infoHash == release.infoHash },
          onToggleCompare = { onToggleCompare(release) },
          onClick = { onInspectTorrent(release) },
          onQuickMagnet = { onQuickMagnet(release) },
          onQuickCopy = { onQuickCopy(release) },
        )
      }
    }
  }

  if (showComparisonSheet && compareSelection.size >= 2) {
    ReleaseComparisonSheet(
      releases = compareSelection,
      onDismiss = { showComparisonSheet = false },
      onSelectRelease = {
        showComparisonSheet = false
        onInspectTorrent(it)
      },
    )
  }
}
