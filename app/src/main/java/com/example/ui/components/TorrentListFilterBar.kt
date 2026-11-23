package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.TorrentFilterFacets
import com.example.data.model.TorrentListFilterState
import com.example.data.model.TorrentListSort
import com.example.data.model.TorrentSizeFilter
import java.util.Locale

@Composable
fun TorrentListFilterBar(
  totalCount: Int,
  facets: TorrentFilterFacets,
  filterState: TorrentListFilterState,
  onFilterChange: (TorrentListFilterState) -> Unit,
  onOpenFilterSheet: () -> Unit,
  onResetFilters: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var sortMenuExpanded by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxWidth().padding(bottom = 6.dp)) {
    // Row 1: Advanced Filter Sheet trigger + Sort dropdown + Multi-select Quality (Resolution) chips
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      item {
        val count = filterState.activeFilterCount
        FilterChip(
          selected = filterState.hasActiveFilters,
          onClick = onOpenFilterSheet,
          leadingIcon = {
            Icon(
              Icons.Rounded.Tune,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text(if (count > 0) "Filters ($count)" else "Filters") },
          modifier = Modifier.testTag("torrent_filter_open_sheet"),
        )
      }

      item {
        Box {
          FilterChip(
            selected = filterState.sortBy != TorrentListSort.BEST_QUALITY,
            onClick = { sortMenuExpanded = true },
            leadingIcon = {
              Icon(
                Icons.AutoMirrored.Rounded.Sort,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            trailingIcon = {
              Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            label = { Text(filterState.sortBy.label) },
            modifier = Modifier.testTag("torrent_filter_sort"),
          )
          DropdownMenu(
            expanded = sortMenuExpanded,
            onDismissRequest = { sortMenuExpanded = false },
          ) {
            TorrentListSort.entries.forEach { option ->
              DropdownMenuItem(
                text = { Text(option.label) },
                trailingIcon =
                  if (option == filterState.sortBy) {
                    {
                      Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                      )
                    }
                  } else null,
                onClick = {
                  sortMenuExpanded = false
                  onFilterChange(filterState.copy(sortBy = option))
                },
              )
            }
          }
        }
      }

      item {
        FilterChip(
          selected = filterState.resolutions.isEmpty(),
          onClick = { onFilterChange(filterState.copy(resolutions = emptySet())) },
          label = { Text("All ($totalCount)") },
          modifier = Modifier.testTag("torrent_filter_res_all"),
        )
      }

      items(facets.resolutions, key = { it.first }) { (res, count) ->
        val isSelected = filterState.resolutions.any { it.equals(res, ignoreCase = true) }
        FilterChip(
          selected = isSelected,
          onClick = {
            val next =
              if (isSelected) filterState.resolutions.filterNot { it.equals(res, ignoreCase = true) }.toSet()
              else filterState.resolutions + res
            onFilterChange(filterState.copy(resolutions = next))
          },
          label = { Text("$res ($count)") },
          modifier = Modifier.testTag("torrent_filter_res_$res"),
        )
      }
    }

    Spacer(Modifier.height(2.dp))

    // Row 2: Size buckets / custom size + Quick Health/Codec toggles + Reset
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (filterState.hasCustomSizeRange) {
        item {
          val minLabel = filterState.customMinSizeGb?.let { String.format(Locale.US, "%.1f", it) } ?: "0"
          val maxLabel = filterState.customMaxSizeGb?.let { String.format(Locale.US, "%.1f", it) } ?: "∞"
          FilterChip(
            selected = true,
            onClick = {
              onFilterChange(
                filterState.copy(
                  sizePreset = TorrentSizeFilter.ALL,
                  customMinSizeGb = null,
                  customMaxSizeGb = null,
                )
              )
            },
            trailingIcon = {
              Icon(Icons.Rounded.Clear, contentDescription = "Clear custom size", modifier = Modifier.size(14.dp))
            },
            label = { Text("$minLabel–$maxLabel GB") },
            modifier = Modifier.testTag("torrent_filter_custom_size"),
          )
        }
      }

      items(TorrentSizeFilter.entries) { sizeBucket ->
        val isSelected = !filterState.hasCustomSizeRange && filterState.sizePreset == sizeBucket
        FilterChip(
          selected = isSelected,
          onClick = {
            val nextPreset =
              if (sizeBucket == TorrentSizeFilter.ALL || isSelected) TorrentSizeFilter.ALL
              else sizeBucket
            onFilterChange(
              filterState.copy(
                sizePreset = nextPreset,
                customMinSizeGb = null,
                customMaxSizeGb = null,
              )
            )
          },
          label = { Text(sizeBucket.label) },
          modifier = Modifier.testTag("torrent_filter_size_${sizeBucket.name.lowercase()}"),
        )
      }

      item {
        val activeSeedersOnly = (filterState.minSeeders ?: 0) >= 1
        FilterChip(
          selected = activeSeedersOnly,
          onClick = {
            onFilterChange(filterState.copy(minSeeders = if (activeSeedersOnly) null else 1))
          },
          label = { Text("Seeded (▲ > 0)") },
          modifier = Modifier.testTag("torrent_filter_seeded"),
        )
      }

      if (facets.verifiedCount > 0) {
        item {
          FilterChip(
            selected = filterState.verifiedOnly,
            onClick = { onFilterChange(filterState.copy(verifiedOnly = !filterState.verifiedOnly)) },
            leadingIcon = {
              Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp))
            },
            label = { Text("TrueSpec (${facets.verifiedCount})") },
            modifier = Modifier.testTag("torrent_filter_truespec"),
          )
        }
      }

      if (facets.codecs.any { it.first.equals("HEVC", ignoreCase = true) }) {
        val hevcSelected = filterState.codecs.any { it.equals("HEVC", ignoreCase = true) }
        item {
          FilterChip(
            selected = hevcSelected,
            onClick = {
              val next =
                if (hevcSelected) filterState.codecs.filterNot { it.equals("HEVC", ignoreCase = true) }.toSet()
                else filterState.codecs + "HEVC"
              onFilterChange(filterState.copy(codecs = next))
            },
            label = { Text("HEVC / x265") },
            modifier = Modifier.testTag("torrent_filter_hevc"),
          )
        }
      }

      if (filterState.hasActiveFilters) {
        item {
          AssistChip(
            onClick = onResetFilters,
            leadingIcon = {
              Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(15.dp))
            },
            label = { Text("Reset") },
            modifier = Modifier.testTag("torrent_filter_reset"),
          )
        }
      }
    }
  }
}
