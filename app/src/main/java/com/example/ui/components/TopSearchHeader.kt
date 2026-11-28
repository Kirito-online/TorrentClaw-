package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CatalogStats
import com.example.data.model.MediaItem
import com.example.data.model.ParsedExternalMediaUrl
import com.example.data.model.SearchFilter
import com.example.ui.theme.LocalTorrentClawColors
import com.example.ui.viewmodel.TopSectionTab

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopSearchHeader(
  activeTab: TopSectionTab,
  stats: CatalogStats,
  queryText: String,
  searchFocused: Boolean,
  searchFilter: SearchFilter,
  suggestions: List<MediaItem>,
  parsedExternalUrl: ParsedExternalMediaUrl?,
  recentSearches: List<String>,
  favoriteCount: Int = 0,
  onSelectTab: (TopSectionTab) -> Unit,
  onQueryChange: (String) -> Unit,
  onFocusChange: (Boolean) -> Unit,
  onSubmitSearch: (String) -> Unit,
  onOpenFilterSheet: () -> Unit,
  onSelectSuggestion: (MediaItem) -> Unit,
  onRemoveRecent: (String) -> Unit,
  onClearRecents: () -> Unit,
  onClearSearch: () -> Unit,
) {
  val semantic = LocalTorrentClawColors.current

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
      // Brand row (pure native header, NO URL bar)
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .clickable { onSelectTab(TopSectionTab.DISCOVER) }
              .padding(vertical = 2.dp),
        ) {
          Box(
            modifier =
              Modifier.size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
          )
          Text(
            text = "TorrentClaw",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }

        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Box(
              modifier = Modifier.size(6.dp).clip(CircleShape).background(semantic.verified)
            )
            Text(
              text =
                if (stats.totalTorrents > 0) "${stats.activeSources} sources"
                else "Public Index",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      // Persistent Search Bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = queryText,
          onValueChange = onQueryChange,
          placeholder = {
            Text(
              text = stringResource(R.string.search_placeholder),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = "Search",
              tint = MaterialTheme.colorScheme.primary,
            )
          },
          trailingIcon = {
            if (queryText.isNotEmpty() || searchFocused) {
              IconButton(
                onClick = {
                  if (queryText.isNotEmpty()) {
                    onClearSearch()
                  } else {
                    onFocusChange(false)
                  }
                },
                modifier = Modifier.testTag("search_clear_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Clear search",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(queryText) }),
          modifier =
            Modifier.weight(1f)
              .onFocusChanged { onFocusChange(it.isFocused) }
              .testTag("persistent_search_input"),
        )

        Surface(
          modifier =
            Modifier.size(52.dp)
              .clip(RoundedCornerShape(14.dp))
              .clickable(onClick = onOpenFilterSheet)
              .testTag("search_filter_button"),
          shape = RoundedCornerShape(14.dp),
          color =
            if (searchFilter.activeFilterCount > 0) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
          border =
            BorderStroke(
              1.dp,
              if (searchFilter.activeFilterCount > 0) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
          Box(contentAlignment = Alignment.Center) {
            BadgedBox(
              badge = {
                if (searchFilter.activeFilterCount > 0) {
                  Badge { Text(searchFilter.activeFilterCount.toString()) }
                }
              }
            ) {
              Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Search filters",
                tint =
                  if (searchFilter.activeFilterCount > 0) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Live Autocomplete / IMDb-TMDB Detection / Recent Search Panel
      AnimatedVisibility(
        visible =
          searchFocused &&
            (parsedExternalUrl != null || suggestions.isNotEmpty() || recentSearches.isNotEmpty()),
      ) {
        Surface(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (parsedExternalUrl != null) {
              val label =
                when {
                  parsedExternalUrl.imdbId != null -> "Lookup IMDb ID: ${parsedExternalUrl.imdbId}"
                  parsedExternalUrl.tmdbId != null ->
                    "Lookup TMDB ${parsedExternalUrl.mediaType?.label ?: "Title"} #${parsedExternalUrl.tmdbId}"
                  else -> "Lookup URL"
                }
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .clickable { onSubmitSearch(queryText) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }

            if (suggestions.isNotEmpty()) {
              Text(
                text = "Suggestions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              suggestions.take(6).forEach { item ->
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(10.dp))
                      .clickable { onSelectSuggestion(item) }
                      .padding(horizontal = 6.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier =
                      Modifier.size(width = 34.dp, height = 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = item.title,
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(item.year?.toString(), item.mediaType.label, item.rating?.let { "★ ${"%.1f".format(it)}" })
                      .joinToString(" • ")
                    if (meta.isNotBlank()) {
                      Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }

            if (suggestions.isEmpty() && recentSearches.isNotEmpty()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Recent Searches",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                  onClick = onClearRecents,
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                  Text("Clear all", style = MaterialTheme.typography.labelMedium)
                }
              }
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                recentSearches.forEach { term ->
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                      )
                      Spacer(Modifier.width(6.dp))
                      Text(
                        text = term,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable { onSubmitSearch(term) },
                      )
                      IconButton(
                        onClick = { onRemoveRecent(term) },
                        modifier = Modifier.size(28.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Rounded.DeleteOutline,
                          contentDescription = "Remove $term",
                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.size(14.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      // Top Section Tabs: Discover, Popular, Recent, Collections, Favorites (+ Search when active)
      val visibleTabs =
        if (activeTab == TopSectionTab.SEARCH || queryText.isNotBlank() || searchFilter.activeFilterCount > 0) {
          listOf(
            TopSectionTab.DISCOVER,
            TopSectionTab.POPULAR,
            TopSectionTab.RECENT,
            TopSectionTab.COLLECTIONS,
            TopSectionTab.FAVORITES,
            TopSectionTab.SEARCH,
          )
        } else {
          listOf(
            TopSectionTab.DISCOVER,
            TopSectionTab.POPULAR,
            TopSectionTab.RECENT,
            TopSectionTab.COLLECTIONS,
            TopSectionTab.FAVORITES,
          )
        }

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        visibleTabs.forEach { tab ->
          val selected = activeTab == tab
          val tabLabel =
            if (tab == TopSectionTab.FAVORITES && favoriteCount > 0) {
              "${tab.label} ($favoriteCount)"
            } else {
              tab.label
            }
          Surface(
            modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                .clickable { onSelectTab(tab) }
                .testTag("top_tab_${tab.name.lowercase()}"),
            shape = RoundedCornerShape(10.dp),
            color =
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surface,
            border =
              BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
              ),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              if (tab == TopSectionTab.FAVORITES) {
                Icon(
                  imageVector = Icons.Rounded.Favorite,
                  contentDescription = null,
                  tint =
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else if (favoriteCount > 0) semantic.favoriteRose
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(14.dp),
                )
              }
              Text(
                text = tabLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Spacer(Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
  }
}
