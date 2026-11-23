package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import com.example.core.preferences.InterfaceDensity
import com.example.core.preferences.ListingLayout
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.AutocompleteSuggestion
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.ParsedMediaLookup
import com.example.data.model.SearchFilter
import com.example.data.model.SortOption
import com.example.data.model.TorrentListSort
import com.example.data.model.TorrentRelease
import com.example.data.model.TorrentSizeFilter
import com.example.data.model.extractAvailableResolutions
import com.example.data.model.filterAndSortTorrents
import com.example.ui.components.ReleaseComparisonSheet
import com.example.ui.components.SearchFilterSheet
import com.example.ui.components.TorrentClawEmptyState
import com.example.ui.components.TorrentListFilterBar
import com.example.ui.components.TorrentReleaseRow
import com.example.ui.theme.LocalTorrentClawColors

@Composable
fun SearchScreen(
  queryText: String,
  filter: SearchFilter,
  hasActiveSearch: Boolean,
  suggestions: List<AutocompleteSuggestion>,
  parsedUrl: ParsedMediaLookup?,
  searchHistory: List<SearchHistoryEntity>,
  pagedMedia: LazyPagingItems<MediaItem>,
  directTorrents: List<TorrentRelease>,
  availableGenres: List<String>,
  listingLayout: ListingLayout,
  density: InterfaceDensity,
  autocompleteEnabled: Boolean,
  compareSelection: List<TorrentRelease>,
  onQueryChange: (String) -> Unit,
  onSubmitSearch: (String?) -> Unit,
  onApplyFilter: (SearchFilter) -> Unit,
  onResetFilters: () -> Unit,
  onCancelOrClear: () -> Unit,
  onDeleteHistory: (String) -> Unit,
  onClearHistory: () -> Unit,
  onMediaClick: (MediaItem) -> Unit,
  onToggleLayout: () -> Unit,
  onInspectTorrent: (TorrentRelease) -> Unit,
  onQuickMagnet: (TorrentRelease) -> Unit,
  onQuickCopy: (TorrentRelease) -> Unit,
  onToggleCompare: (TorrentRelease) -> Unit,
  onClearCompare: () -> Unit,
) {
  val keyboard = LocalSoftwareKeyboardController.current
  val semantic = LocalTorrentClawColors.current
  var showFilters by remember { mutableStateOf(false) }
  var isCompareMode by remember { mutableStateOf(false) }
  var showComparisonSheet by remember { mutableStateOf(false) }
  var torrentResFilter by remember { mutableStateOf<String?>(null) }
  var torrentSizeFilter by remember { mutableStateOf(TorrentSizeFilter.ALL) }
  var torrentSort by remember { mutableStateOf(TorrentListSort.BEST_QUALITY) }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Search Input Header
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = queryText,
        onValueChange = onQueryChange,
        placeholder = { Text("Title, IMDb/TMDB URL, or keywords…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (queryText.isNotEmpty() || hasActiveSearch) {
            IconButton(
              onClick = onCancelOrClear,
              modifier = Modifier.testTag("search_clear_button"),
            ) {
              Icon(Icons.Rounded.Clear, contentDescription = "Cancel or clear search")
            }
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
          KeyboardActions(
            onSearch = {
              keyboard?.hide()
              onSubmitSearch(null)
            }
          ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(1f).testTag("search_input_field"),
      )

      Spacer(Modifier.width(8.dp))

      IconButton(
        onClick = { showFilters = true },
        modifier = Modifier.size(48.dp).testTag("search_filter_button"),
      ) {
        BadgedBox(
          badge = {
            if (filter.activeFilterCount > 0) {
              Badge { Text("${filter.activeFilterCount}") }
            }
          }
        ) {
          Icon(Icons.Rounded.FilterList, contentDescription = "Advanced Filters")
        }
      }
    }

    // IMDb / TMDB URL Detection Chip
    if (parsedUrl != null) {
      Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable {
              keyboard?.hide()
              onSubmitSearch(null)
            },
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          val detectedLabel =
            parsedUrl.imdbId?.let { "IMDb ID $it detected — Tap for direct lookup" }
              ?: "TMDB ${parsedUrl.mediaType?.label ?: "Media"} #${parsedUrl.tmdbId} detected — Tap for lookup"
          Text(detectedLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    // Quick Filter & Sort Strip
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      item {
        FilterChip(
          selected = filter.mediaType == MediaType.MOVIE,
          onClick = {
            val next = if (filter.mediaType == MediaType.MOVIE) MediaType.ALL else MediaType.MOVIE
            onApplyFilter(filter.copy(mediaType = next))
          },
          label = { Text("Movies") },
        )
      }
      item {
        FilterChip(
          selected = filter.mediaType == MediaType.SHOW,
          onClick = {
            val next = if (filter.mediaType == MediaType.SHOW) MediaType.ALL else MediaType.SHOW
            onApplyFilter(filter.copy(mediaType = next))
          },
          label = { Text("TV Shows") },
        )
      }
      listOf("2160p", "1080p", "720p").forEach { res ->
        item {
          FilterChip(
            selected = res in filter.resolutions,
            onClick = {
              val next =
                if (res in filter.resolutions) filter.resolutions - res else filter.resolutions + res
              onApplyFilter(filter.copy(resolutions = next))
            },
            label = { Text(res) },
          )
        }
      }
      item {
        FilterChip(
          selected = filter.sortBy == SortOption.SEEDERS,
          onClick = {
            val nextSort =
              if (filter.sortBy == SortOption.SEEDERS) SortOption.QUALITY_SCORE else SortOption.SEEDERS
            onApplyFilter(filter.copy(sortBy = nextSort))
          },
          label = { Text(if (filter.sortBy == SortOption.SEEDERS) "Most Seeded" else filter.sortBy.label) },
        )
      }
    }

    // Autocomplete Suggestions Overlay
    if (autocompleteEnabled && suggestions.isNotEmpty() && !hasActiveSearch) {
      LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
          Text(
            text = "Suggestions",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
          )
        }
        items(suggestions) { sug ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  keyboard?.hide()
                  onSubmitSearch(sug.title)
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(
              text = sug.title,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.weight(1f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(sug.year?.toString(), sug.mediaType.label).joinToString(" • ")
            if (meta.isNotEmpty()) {
              Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
      }
    } else if (!hasActiveSearch) {
      // Recent Search History / Empty Prompt
      LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (searchHistory.isNotEmpty()) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Recent Searches",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = onClearHistory) { Text("Clear All") }
            }
          }
          items(searchHistory, key = { it.query }) { entry ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    keyboard?.hide()
                    onSubmitSearch(entry.query)
                  }
                  .padding(horizontal = 16.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(12.dp))
              Text(
                text = entry.query,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              IconButton(onClick = { onDeleteHistory(entry.query) }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove search", modifier = Modifier.size(18.dp))
              }
            }
          }
        } else {
          item {
            TorrentClawEmptyState(
              title = "Search TorrentClaw",
              message = "Type any movie or TV series title, paste an IMDb or TMDB URL, or use filters to browse verified releases.",
            )
          }
        }
      }
    } else {
      // Active Search Results (Paging 3 + Direct Torrent Releases)
      val refreshState = pagedMedia.loadState.refresh

      if (refreshState is LoadState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(10.dp))
            Text("Searching 30+ sources…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else if (refreshState is LoadState.Error && pagedMedia.itemCount == 0 && directTorrents.isEmpty()) {
        TorrentClawEmptyState(
          title = "Search Error",
          message = refreshState.error.localizedMessage ?: "Unable to fetch search results.",
          actionLabel = "Retry Search",
          onAction = { pagedMedia.retry() },
        )
      } else if (pagedMedia.itemCount == 0 && directTorrents.isEmpty()) {
        TorrentClawEmptyState(
          title = "No Matching Releases",
          message = "Try relaxing resolution/codec filters or searching an alternative title.",
          actionLabel = "Reset Filters",
          onAction = onResetFilters,
        )
      } else {
        // Result Toolbar (Layout toggle + Compare button)
        if (directTorrents.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${pagedMedia.itemCount} titles • ${directTorrents.size} releases",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
              isCompareMode = !isCompareMode
              if (!isCompareMode) onClearCompare()
            }) {
              Icon(Icons.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(if (isCompareMode) "Done" else "Compare")
            }
            IconButton(onClick = onToggleLayout, modifier = Modifier.size(40.dp)) {
              Icon(
                if (listingLayout == ListingLayout.COMPACT) Icons.Rounded.ViewAgenda else Icons.Rounded.ViewStream,
                contentDescription = "Toggle compact/expanded layout",
              )
            }
          }
        }

        if (isCompareMode && compareSelection.size >= 2) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${compareSelection.size} selected for comparison",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
              )
              Button(onClick = { showComparisonSheet = true }) {
                Text("Compare Now")
              }
            }
          }
        }

        val availRes = remember(directTorrents) { extractAvailableResolutions(directTorrents) }
        val filteredDirectTorrents =
          remember(directTorrents, torrentResFilter, torrentSizeFilter, torrentSort) {
            filterAndSortTorrents(directTorrents, torrentResFilter, torrentSizeFilter, torrentSort)
          }
        val isDirectFiltered = torrentResFilter != null || torrentSizeFilter != TorrentSizeFilter.ALL

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
          items(count = pagedMedia.itemCount, key = { idx -> pagedMedia[idx]?.id ?: "media_$idx" }) { index ->
            val media = pagedMedia[index]
            if (media != null) {
              SearchMediaRow(item = media, onClick = { onMediaClick(media) })
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
          }

          if (directTorrents.isNotEmpty()) {

            item {
              Text(
                text =
                  if (isDirectFiltered) "Matching Torrent Releases (${filteredDirectTorrents.size} of ${directTorrents.size})"
                  else "Matching Torrent Releases (${directTorrents.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
              )
              TorrentListFilterBar(
                totalCount = directTorrents.size,
                availableResolutions = availRes,
                selectedResolution = torrentResFilter,
                selectedSizeFilter = torrentSizeFilter,
                selectedSort = torrentSort,
                onSelectResolution = { torrentResFilter = it },
                onSelectSizeFilter = { torrentSizeFilter = it },
                onSelectSort = { torrentSort = it },
                onResetFilters = {
                  torrentResFilter = null
                  torrentSizeFilter = TorrentSizeFilter.ALL
                  torrentSort = TorrentListSort.BEST_QUALITY
                },
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
            if (filteredDirectTorrents.isEmpty()) {
              item {
                TorrentClawEmptyState(
                  title = "No Torrents Match Filters",
                  message = "Adjust quality or size filters to see matching releases.",
                  actionLabel = "Reset Filters",
                  onAction = {
                    torrentResFilter = null
                    torrentSizeFilter = TorrentSizeFilter.ALL
                    torrentSort = TorrentListSort.BEST_QUALITY
                  },
                )
              }
            } else {
              items(filteredDirectTorrents, key = { it.infoHash }) { rel ->
                TorrentReleaseRow(
                  release = rel,
                  layout = listingLayout,
                  density = density,
                  isCompareMode = isCompareMode,
                  isSelectedForCompare = compareSelection.any { it.infoHash == rel.infoHash },
                  onToggleCompare = { onToggleCompare(rel) },
                  onClick = { onInspectTorrent(rel) },
                  onQuickMagnet = { onQuickMagnet(rel) },
                  onQuickCopy = { onQuickCopy(rel) },
                )
              }
            }
          }

          when (val append = pagedMedia.loadState.append) {
            is LoadState.Loading -> {
              item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
              }
            }
            is LoadState.Error -> {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  horizontalArrangement = Arrangement.Center,
                ) {
                  TextButton(onClick = { pagedMedia.retry() }) {
                    Text("Failed to load more — Tap to retry")
                  }
                }
              }
            }
            else -> Unit
          }
        }
      }
    }
  }

  if (showFilters) {
    SearchFilterSheet(
      initialFilter = filter,
      availableGenres = availableGenres,
      onApply = onApplyFilter,
      onReset = onResetFilters,
      onDismiss = { showFilters = false },
    )
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

@Composable
private fun SearchMediaRow(item: MediaItem, onClick: () -> Unit) {
  val semantic = LocalTorrentClawColors.current
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .testTag("search_media_${item.id}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.width(62.dp)
          .aspectRatio(2f / 3f)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      if (!item.posterUrl.isNullOrBlank()) {
        AsyncImage(
          model = item.posterUrl,
          contentDescription = item.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Icon(
          if (item.mediaType == MediaType.SHOW) Icons.Rounded.Tv else Icons.Rounded.Movie,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Spacer(Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val meta =
        listOfNotNull(
            item.year?.toString(),
            item.mediaType.label,
            item.genres.take(2).joinToString(", ").takeIf { it.isNotEmpty() },
          )
          .joinToString(" • ")
      Text(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(4.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item.rating?.takeIf { it > 0.0 }?.let { r ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = semantic.hdrGold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text(String.format("%.1f", r), style = MaterialTheme.typography.labelSmall)
          }
        }
        Text(
          text = if (item.hasTorrents) "${maxOf(item.torrentCount, item.torrents.size)} releases" else "No indexed releases",
          style = MaterialTheme.typography.labelSmall,
          color = if (item.hasTorrents) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.bestQualityScore?.takeIf { it > 0 }?.let { score ->
          Text(
            text = "Top $score/100",
            style = MaterialTheme.typography.labelSmall,
            color = semantic.verified,
          )
        }
      }
    }
  }
}
