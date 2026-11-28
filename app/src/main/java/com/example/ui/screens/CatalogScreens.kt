package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CollectionSummary
import com.example.data.model.DiscoverySnapshot
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SearchFilter
import com.example.data.model.TorrentRelease
import com.example.data.model.isSameFavoriteMedia
import com.example.ui.components.CollectionCard
import com.example.ui.components.GridMediaCard
import com.example.ui.components.HeroSpotlightCard
import com.example.ui.components.PosterMediaCard
import com.example.ui.components.TorrentReleaseRow
import com.example.ui.theme.LocalTorrentClawColors
import com.example.ui.viewmodel.CollectionsTabState
import com.example.ui.viewmodel.SectionListState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
  snapshot: DiscoverySnapshot,
  isLoading: Boolean,
  error: String?,
  favoriteItems: List<MediaItem> = emptyList(),
  onMediaClick: (MediaItem) -> Unit,
  onToggleFavorite: (MediaItem) -> Unit = {},
  onTorrentClick: (TorrentRelease) -> Unit,
  onMagnetClick: (TorrentRelease) -> Unit = onTorrentClick,
  onGenreClick: (String) -> Unit,
  onCollectionClick: (CollectionSummary) -> Unit,
  onSeePopularMovies: () -> Unit,
  onSeePopularShows: () -> Unit,
  onSeeRecent: () -> Unit,
  onRetry: () -> Unit,
) {
  if (isLoading && snapshot.trending.isEmpty() && snapshot.popularMovies.isEmpty()) {
    CenterLoadingState("Loading Discover & Popular…")
    return
  }

  if (error != null && snapshot.trending.isEmpty() && snapshot.popularMovies.isEmpty()) {
    CenterErrorState(error = error, onRetry = onRetry)
    return
  }

  fun isFav(item: MediaItem): Boolean = favoriteItems.any { isSameFavoriteMedia(it, item) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    snapshot.trending.firstOrNull()?.let { spotlight ->
      item {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(16.dp)) {
          HeroSpotlightCard(
            item = spotlight,
            onClick = { onMediaClick(spotlight) },
            isFavorite = isFav(spotlight),
            onToggleFavorite = { onToggleFavorite(spotlight) },
          )
        }
      }
    }

    if (snapshot.trending.size > 1) {
      item {
        CatalogSectionHeader(title = "Trending Now", actionLabel = null, onAction = null)
        LazyRow(
          modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(snapshot.trending.drop(1), key = { "tr_${it.id}" }) { item ->
            PosterMediaCard(
              item = item,
              onClick = { onMediaClick(item) },
              isFavorite = isFav(item),
              onToggleFavorite = { onToggleFavorite(item) },
            )
          }
        }
      }
    }

    if (snapshot.popularMovies.isNotEmpty()) {
      item {
        CatalogSectionHeader(title = "Popular Movies", actionLabel = "See all", onAction = onSeePopularMovies)
        LazyRow(
          modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(snapshot.popularMovies, key = { "pm_${it.id}" }) { item ->
            PosterMediaCard(
              item = item,
              onClick = { onMediaClick(item) },
              isFavorite = isFav(item),
              onToggleFavorite = { onToggleFavorite(item) },
            )
          }
        }
      }
    }

    if (snapshot.popularShows.isNotEmpty()) {
      item {
        CatalogSectionHeader(title = "Popular TV Shows", actionLabel = "See all", onAction = onSeePopularShows)
        LazyRow(
          modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(snapshot.popularShows, key = { "ps_${it.id}" }) { item ->
            PosterMediaCard(
              item = item,
              onClick = { onMediaClick(item) },
              isFavorite = isFav(item),
              onToggleFavorite = { onToggleFavorite(item) },
            )
          }
        }
      }
    }

    if (snapshot.genres.isNotEmpty()) {
      item {
        CatalogSectionHeader(title = "Browse by Genre", actionLabel = null, onAction = null)
        FlowRow(
          modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          snapshot.genres.take(18).forEach { genre ->
            FilterChip(
              selected = false,
              onClick = { onGenreClick(genre) },
              label = { Text(genre) },
            )
          }
        }
      }
    }

    if (snapshot.collections.isNotEmpty()) {
      item {
        CatalogSectionHeader(title = "Featured Collections", actionLabel = null, onAction = null)
        LazyRow(
          modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(snapshot.collections, key = { "col_${it.id}" }) { col ->
            CollectionCard(
              collection = col,
              onClick = { onCollectionClick(col) },
              modifier = Modifier.widthIn(min = 240.dp, max = 280.dp),
            )
          }
        }
      }
    }

    if (snapshot.recentMedia.isNotEmpty()) {
      item {
        CatalogSectionHeader(title = "Recent Additions", actionLabel = "See all", onAction = onSeeRecent)
        LazyRow(
          modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(snapshot.recentMedia, key = { "rm_${it.id}" }) { item ->
            PosterMediaCard(
              item = item,
              onClick = { onMediaClick(item) },
              isFavorite = isFav(item),
              onToggleFavorite = { onToggleFavorite(item) },
            )
          }
        }
      }
    }

    if (snapshot.recentTorrents.isNotEmpty()) {
      item {
        CatalogSectionHeader(title = "Recently Indexed Torrents", actionLabel = "See all", onAction = onSeeRecent)
      }
      itemsIndexed(
        snapshot.recentTorrents.take(12),
        key = { idx, it -> "rt_${idx}_${it.infoHash}_${it.releaseName.hashCode()}" },
      ) { _, release ->
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(horizontal = 16.dp, vertical = 5.dp)) {
          TorrentReleaseRow(
            release = release,
            onClick = { onTorrentClick(release) },
            onMagnetClick = { onMagnetClick(release) },
          )
        }
      }
    }
  }
}

@Composable
fun CatalogListScreen(
  title: String,
  state: SectionListState,
  favoriteItems: List<MediaItem> = emptyList(),
  onSelectType: (MediaType) -> Unit,
  onMediaClick: (MediaItem) -> Unit,
  onToggleFavorite: (MediaItem) -> Unit = {},
  onTorrentClick: (TorrentRelease) -> Unit,
  onMagnetClick: (TorrentRelease) -> Unit = onTorrentClick,
  onLoadMore: () -> Unit,
  onRetry: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MediaType.entries.forEach { type ->
        FilterChip(
          selected = state.mediaType == type,
          onClick = { onSelectType(type) },
          label = { Text(type.label) },
        )
      }
    }

    if (state.isLoading && state.items.isEmpty() && state.torrents.isEmpty()) {
      CenterLoadingState("Loading $title…")
      return
    }
    if (state.error != null && state.items.isEmpty() && state.torrents.isEmpty()) {
      CenterErrorState(state.error, onRetry)
      return
    }

    ResponsiveCatalogLazyList(
      items = state.items,
      torrents = state.torrents,
      hasMore = state.hasMore,
      isLoadingMore = state.isLoadingMore,
      emptyMessage = "No items returned for $title.",
      favoriteItems = favoriteItems,
      onMediaClick = onMediaClick,
      onToggleFavorite = onToggleFavorite,
      onTorrentClick = onTorrentClick,
      onMagnetClick = onMagnetClick,
      onLoadMore = onLoadMore,
      onRetry = onRetry,
    )
  }
}

@Composable
fun CollectionsScreen(
  state: CollectionsTabState,
  favoriteItems: List<MediaItem> = emptyList(),
  onOpenCollection: (CollectionSummary) -> Unit,
  onBackFromCollection: () -> Unit,
  onMediaClick: (MediaItem) -> Unit,
  onToggleFavorite: (MediaItem) -> Unit = {},
  onRetry: () -> Unit,
) {
  val selected = state.selectedCollection
  if (selected != null) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBackFromCollection) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to collections")
        }
        Column {
          Text(selected.name, style = MaterialTheme.typography.titleLarge)
          if (selected.description.isNotBlank()) {
            Text(
              selected.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      if (state.isLoading) {
        CenterLoadingState("Loading collection…")
      } else {
        ResponsiveCatalogLazyList(
          items = state.selectedItems,
          torrents = emptyList(),
          hasMore = false,
          isLoadingMore = false,
          emptyMessage = "No titles in this collection.",
          favoriteItems = favoriteItems,
          onMediaClick = onMediaClick,
          onToggleFavorite = onToggleFavorite,
          onTorrentClick = {},
          onLoadMore = {},
          onRetry = { onOpenCollection(selected) },
        )
      }
    }
    return
  }

  if (state.isLoading && state.collections.isEmpty()) {
    CenterLoadingState("Loading collections…")
    return
  }
  if (state.error != null && state.collections.isEmpty()) {
    CenterErrorState(state.error, onRetry)
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    items(state.collections, key = { it.id }) { col ->
      CollectionCard(
        collection = col,
        onClick = { onOpenCollection(col) },
        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen(
  favorites: List<MediaItem>,
  onMediaClick: (MediaItem) -> Unit,
  onToggleFavorite: (MediaItem) -> Unit,
  onClearFavorites: () -> Unit,
  onExploreDiscover: () -> Unit,
) {
  var selectedType by remember { mutableStateOf(MediaType.ALL) }
  val semantic = LocalTorrentClawColors.current

  val movieFavorites = remember(favorites) { favorites.filter { it.mediaType != MediaType.SHOW } }
  val showFavorites = remember(favorites) { favorites.filter { it.mediaType == MediaType.SHOW } }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val counts =
          mapOf(
            MediaType.ALL to favorites.size,
            MediaType.MOVIE to movieFavorites.size,
            MediaType.SHOW to showFavorites.size,
          )
        MediaType.entries.forEach { type ->
          val count = counts[type] ?: 0
          FilterChip(
            selected = selectedType == type,
            onClick = { selectedType = type },
            label = { Text("${type.label} ($count)") },
            modifier = Modifier.testTag("favorites_filter_${type.name.lowercase()}"),
          )
        }
      }

      if (favorites.isNotEmpty()) {
        TextButton(
          onClick = onClearFavorites,
          modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("favorites_clear_all_button"),
        ) {
          Text("Clear all")
        }
      }
    }

    if (favorites.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.widthIn(max = 420.dp),
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.FavoriteBorder,
              contentDescription = null,
              tint = semantic.favoriteRose,
              modifier = Modifier.size(40.dp),
            )
            Text(
              text = stringResource(R.string.favorites_empty_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.favorites_empty_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onExploreDiscover,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
              Text("Explore Discover")
            }
          }
        }
      }
      return
    }

    val showMoviesSection =
      (selectedType == MediaType.ALL || selectedType == MediaType.MOVIE) && movieFavorites.isNotEmpty()
    val showTvSection =
      (selectedType == MediaType.ALL || selectedType == MediaType.SHOW) && showFavorites.isNotEmpty()

    if (!showMoviesSection && !showTvSection) {
      val categoryLabel = if (selectedType == MediaType.MOVIE) "movies" else "TV shows"
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.widthIn(max = 420.dp),
        ) {
          Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = if (selectedType == MediaType.SHOW) Icons.Rounded.Tv else Icons.Rounded.Movie,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(36.dp),
            )
            Text(
              text = "No favorite $categoryLabel yet",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(
              onClick = { selectedType = MediaType.ALL },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
              Text("Show All Favorites")
            }
          }
        }
      }
      return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val columns = if (maxWidth >= 720.dp) 4 else if (maxWidth >= 520.dp) 3 else 2
      val movieRows = if (showMoviesSection) movieFavorites.chunked(columns) else emptyList()
      val showRows = if (showTvSection) showFavorites.chunked(columns) else emptyList()

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (showMoviesSection) {
          item(key = "fav_header_movies") {
            Row(
              modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp).padding(top = 4.dp).testTag("favorites_section_movies"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = "Movies (${movieFavorites.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
            }
          }
          items(movieRows) { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              rowItems.forEach { item ->
                GridMediaCard(
                  item = item,
                  onClick = { onMediaClick(item) },
                  isFavorite = true,
                  onToggleFavorite = { onToggleFavorite(item) },
                  modifier = Modifier.weight(1f),
                )
              }
              repeat(columns - rowItems.size) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }

        if (showTvSection) {
          item(key = "fav_header_shows") {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .widthIn(max = 960.dp)
                  .padding(top = if (showMoviesSection) 10.dp else 4.dp)
                  .testTag("favorites_section_shows"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = "TV Shows (${showFavorites.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
            }
          }
          items(showRows) { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              rowItems.forEach { item ->
                GridMediaCard(
                  item = item,
                  onClick = { onMediaClick(item) },
                  isFavorite = true,
                  onToggleFavorite = { onToggleFavorite(item) },
                  modifier = Modifier.weight(1f),
                )
              }
              repeat(columns - rowItems.size) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResultsScreen(
  filter: SearchFilter,
  state: SectionListState,
  favoriteItems: List<MediaItem> = emptyList(),
  onUpdateType: (MediaType) -> Unit,
  onOpenFilterSheet: () -> Unit,
  onResetFilters: () -> Unit,
  onMediaClick: (MediaItem) -> Unit,
  onToggleFavorite: (MediaItem) -> Unit = {},
  onTorrentClick: (TorrentRelease) -> Unit,
  onMagnetClick: (TorrentRelease) -> Unit = onTorrentClick,
  onLoadMore: () -> Unit,
  onRetry: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    FlowRow(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      MediaType.entries.forEach { t ->
        FilterChip(
          selected = filter.mediaType == t,
          onClick = { onUpdateType(t) },
          label = { Text(t.label) },
        )
      }
      filter.genre?.let { g ->
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
          Text(
            text = g,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
      if (filter.activeFilterCount > 0) {
        TextButton(onClick = onOpenFilterSheet) { Text("More Filters") }
        TextButton(onClick = onResetFilters) { Text("Reset") }
      }
    }

    if (state.isLoading && state.items.isEmpty() && state.torrents.isEmpty()) {
      CenterLoadingState("Searching TorrentClaw…")
      return
    }
    if (state.error != null && state.items.isEmpty() && state.torrents.isEmpty()) {
      CenterErrorState(state.error, onRetry)
      return
    }

    ResponsiveCatalogLazyList(
      items = state.items,
      torrents = state.torrents,
      hasMore = state.hasMore,
      isLoadingMore = state.isLoadingMore,
      emptyMessage = "No matching movies, shows, or torrents found.",
      favoriteItems = favoriteItems,
      onMediaClick = onMediaClick,
      onToggleFavorite = onToggleFavorite,
      onTorrentClick = onTorrentClick,
      onMagnetClick = onMagnetClick,
      onLoadMore = onLoadMore,
      onRetry = onRetry,
    )
  }
}

@Composable
private fun ResponsiveCatalogLazyList(
  items: List<MediaItem>,
  torrents: List<TorrentRelease>,
  hasMore: Boolean,
  isLoadingMore: Boolean,
  emptyMessage: String,
  favoriteItems: List<MediaItem> = emptyList(),
  onMediaClick: (MediaItem) -> Unit,
  onToggleFavorite: (MediaItem) -> Unit = {},
  onTorrentClick: (TorrentRelease) -> Unit,
  onMagnetClick: (TorrentRelease) -> Unit = onTorrentClick,
  onLoadMore: () -> Unit,
  onRetry: () -> Unit,
) {
  if (items.isEmpty() && torrents.isEmpty()) {
    CenterErrorState(error = emptyMessage, onRetry = onRetry)
    return
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val columns = if (maxWidth >= 720.dp) 4 else if (maxWidth >= 520.dp) 3 else 2
    val rows = items.chunked(columns)

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      items(rows) { rowItems ->
        Row(
          modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          rowItems.forEach { item ->
            GridMediaCard(
              item = item,
              onClick = { onMediaClick(item) },
              isFavorite = favoriteItems.any { isSameFavoriteMedia(it, item) },
              onToggleFavorite = { onToggleFavorite(item) },
              modifier = Modifier.weight(1f),
            )
          }
          repeat(columns - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }

      if (torrents.isNotEmpty()) {
        itemsIndexed(
          torrents,
          key = { idx, it -> "tor_${idx}_${it.infoHash}_${it.releaseName.hashCode()}" },
        ) { _, release ->
          Box(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
            TorrentReleaseRow(
              release = release,
              onClick = { onTorrentClick(release) },
              onMagnetClick = { onMagnetClick(release) },
            )
          }
        }
      }

      if (hasMore) {
        item {
          OutlinedButton(
            onClick = onLoadMore,
            enabled = !isLoadingMore,
            shape = RoundedCornerShape(12.dp),
          ) {
            if (isLoadingMore) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              Spacer(Modifier.size(8.dp))
            }
            Text(if (isLoadingMore) "Loading more…" else "Load more")
          }
        }
      }
    }
  }
}

@Composable
private fun CatalogSectionHeader(
  title: String,
  actionLabel: String?,
  onAction: (() -> Unit)?,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .widthIn(max = 960.dp)
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (actionLabel != null && onAction != null) {
      TextButton(onClick = onAction) { Text(actionLabel) }
    }
  }
}

@Composable
private fun CenterLoadingState(message: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun CenterErrorState(error: String, onRetry: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.widthIn(max = 420.dp),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.ErrorOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(36.dp),
        )
        Text(
          text = error,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(10.dp)) {
          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Retry")
        }
      }
    }
  }
}
