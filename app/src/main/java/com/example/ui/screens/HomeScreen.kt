package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.preferences.InterfaceDensity
import com.example.core.preferences.PosterSize
import com.example.data.model.CollectionSummary
import com.example.data.model.MediaItem
import com.example.data.model.UserAccount
import com.example.ui.components.ApiHealthBanner
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TorrentClawEmptyState
import com.example.ui.theme.LocalTorrentClawColors
import com.example.ui.viewmodel.HomeUiState

@Composable
fun HomeScreen(
  uiState: HomeUiState,
  account: UserAccount,
  posterSize: PosterSize,
  density: InterfaceDensity,
  onSearchClick: () -> Unit,
  onMediaClick: (MediaItem) -> Unit,
  onGenreClick: (String) -> Unit,
  onCollectionClick: (CollectionSummary) -> Unit,
  onRefresh: () -> Unit,
  onAccountClick: () -> Unit,
) {
  val semantic = LocalTorrentClawColors.current
  val sectionSpacing = if (density == InterfaceDensity.COMPACT) 12.dp else 18.dp

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    // Top Header + Search Bar Trigger
    item {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            painter = painterResource(id = R.drawable.torrentclaw_icon_fg_1795456049965),
            contentDescription = "TorrentClaw",
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
          )
          Spacer(Modifier.width(10.dp))
          Text(
            text = "TorrentClaw",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.clickable(onClick = onAccountClick),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = if (account.isSignedIn) semantic.verified else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              Spacer(Modifier.width(5.dp))
              Text(
                text = account.tier.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          IconButton(onClick = onRefresh) {
            if (uiState.isRefreshing) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Rounded.Refresh, contentDescription = "Refresh discovery")
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        // Prominent Search Bar
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(14.dp),
          tonalElevation = 2.dp,
          modifier =
            Modifier.fillMaxWidth()
              .height(50.dp)
              .clip(RoundedCornerShape(14.dp))
              .clickable(onClick = onSearchClick)
              .testTag("home_search_bar"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.Search,
              contentDescription = "Search",
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Search movies, shows, IMDb / TMDB URLs…",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    item {
      ApiHealthBanner(health = uiState.apiHealth, onRetry = onRefresh)
    }

    // Subtle Discovery Banner with Live Stats
    item {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSearchClick),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_discovery_hero_1795456050067),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.horizontalGradient(
                  listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.35f))
                )
              ),
        )
        Column(
          modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp),
        ) {
          Text(
            text = "Quality-Scored Media Index",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
          )
          val stats = uiState.snapshot.stats
          val statsLine =
            if (stats != null && stats.totalTorrents > 0) {
              "${stats.totalTitles} titles • ${stats.totalTorrents} torrents • ${stats.indexedSources}+ sources"
            } else {
              "30+ aggregated trackers • TrueSpec verified releases"
            }
          Text(
            text = statsLine,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
          )
        }
      }
    }

    // Browse by Genre Chips
    if (uiState.snapshot.genres.isNotEmpty()) {
      item {
        Spacer(Modifier.height(6.dp))
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(uiState.snapshot.genres) { genre ->
            AssistChip(
              onClick = { onGenreClick(genre) },
              label = { Text(genre) },
            )
          }
        }
      }
    }

    if (uiState.isLoading && uiState.snapshot.trending.isEmpty() && uiState.snapshot.popularMovies.isEmpty()) {
      item {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(10.dp))
            Text(
              "Loading TorrentClaw catalog…",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else if (
      uiState.snapshot.trending.isEmpty() &&
        uiState.snapshot.popularMovies.isEmpty() &&
        uiState.snapshot.popularShows.isEmpty() &&
        uiState.snapshot.recentTorrents.isEmpty()
    ) {
      item {
        TorrentClawEmptyState(
          title = "Catalog Unavailable",
          message = uiState.errorMessage ?: "Could not reach torrentclaw.com right now. Check your connection or retry.",
          actionLabel = "Retry Discovery",
          onAction = onRefresh,
        )
      }
    } else {
      if (uiState.snapshot.trending.isNotEmpty()) {
        item {
          Spacer(Modifier.height(sectionSpacing))
          SectionHeader(title = "Trending Now", subtitle = "Highest swarm momentum")
          PosterRail(
            items = uiState.snapshot.trending,
            posterSize = posterSize,
            onMediaClick = onMediaClick,
          )
        }
      }

      if (uiState.recommendedForYou.isNotEmpty()) {
        item {
          Spacer(Modifier.height(sectionSpacing))
          SectionHeader(
            title = "Recommended For You",
            subtitle = "Ranked by your genres & quality score",
          )
          PosterRail(
            items = uiState.recommendedForYou,
            posterSize = posterSize,
            onMediaClick = onMediaClick,
          )
        }
      }

      if (uiState.snapshot.popularMovies.isNotEmpty()) {
        item {
          Spacer(Modifier.height(sectionSpacing))
          SectionHeader(title = "Popular Movies")
          PosterRail(
            items = uiState.snapshot.popularMovies,
            posterSize = posterSize,
            onMediaClick = onMediaClick,
          )
        }
      }

      if (uiState.snapshot.popularShows.isNotEmpty()) {
        item {
          Spacer(Modifier.height(sectionSpacing))
          SectionHeader(title = "Popular TV Shows")
          PosterRail(
            items = uiState.snapshot.popularShows,
            posterSize = posterSize,
            onMediaClick = onMediaClick,
          )
        }
      }

      if (uiState.snapshot.recentTorrents.isNotEmpty()) {
        item {
          Spacer(Modifier.height(sectionSpacing))
          SectionHeader(title = "New Releases & Recently Indexed")
          PosterRail(
            items = uiState.snapshot.recentTorrents,
            posterSize = posterSize,
            onMediaClick = onMediaClick,
          )
        }
      }

      if (uiState.snapshot.collections.isNotEmpty()) {
        item {
          Spacer(Modifier.height(sectionSpacing))
          SectionHeader(title = "Featured Collections & Franchises")
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(uiState.snapshot.collections, key = { it.id }) { collection ->
              CollectionBannerCard(collection = collection, onClick = { onCollectionClick(collection) })
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PosterRail(
  items: List<MediaItem>,
  posterSize: PosterSize,
  onMediaClick: (MediaItem) -> Unit,
) {
  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(items, key = { it.id }) { media ->
      MediaPosterCard(
        item = media,
        posterSize = posterSize,
        onClick = { onMediaClick(media) },
      )
    }
  }
}

@Composable
private fun CollectionBannerCard(
  collection: CollectionSummary,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.width(220.dp)
        .height(112.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick),
  ) {
    val img = collection.backdropUrl ?: collection.posterUrl
    if (!img.isNullOrBlank()) {
      AsyncImage(
        model = img,
        contentDescription = collection.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
          ),
    )
    Row(
      modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Rounded.CollectionsBookmark,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      Column {
        Text(
          text = collection.name,
          style = MaterialTheme.typography.titleSmall,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (collection.itemCount > 0) {
          Text(
            text = "${collection.itemCount} titles",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f),
          )
        }
      }
    }
  }
}
