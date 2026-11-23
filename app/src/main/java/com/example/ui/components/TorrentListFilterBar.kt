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
import com.example.data.model.TorrentListSort
import com.example.data.model.TorrentSizeFilter

@Composable
fun TorrentListFilterBar(
  totalCount: Int,
  availableResolutions: List<Pair<String, Int>>,
  selectedResolution: String?,
  selectedSizeFilter: TorrentSizeFilter,
  selectedSort: TorrentListSort,
  onSelectResolution: (String?) -> Unit,
  onSelectSizeFilter: (TorrentSizeFilter) -> Unit,
  onSelectSort: (TorrentListSort) -> Unit,
  onResetFilters: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var sortMenuExpanded by remember { mutableStateOf(false) }
  val hasActiveFilter = selectedResolution != null || selectedSizeFilter != TorrentSizeFilter.ALL

  Column(modifier = modifier.fillMaxWidth().padding(bottom = 6.dp)) {
    // Row 1: Sort dropdown chip + Quality (Resolution) chips
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      item {
        Box {
          FilterChip(
            selected = selectedSort != TorrentListSort.BEST_QUALITY,
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
            label = { Text(selectedSort.label) },
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
                  if (option == selectedSort) {
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
                  onSelectSort(option)
                },
              )
            }
          }
        }
      }

      item {
        FilterChip(
          selected = selectedResolution == null,
          onClick = { onSelectResolution(null) },
          label = { Text("All ($totalCount)") },
          modifier = Modifier.testTag("torrent_filter_res_all"),
        )
      }

      items(availableResolutions, key = { it.first }) { (res, count) ->
        val isSelected = selectedResolution.equals(res, ignoreCase = true)
        FilterChip(
          selected = isSelected,
          onClick = { onSelectResolution(if (isSelected) null else res) },
          label = { Text("$res ($count)") },
          modifier = Modifier.testTag("torrent_filter_res_$res"),
        )
      }
    }

    Spacer(Modifier.height(2.dp))

    // Row 2: File Size filter chips + Clear chip
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items(TorrentSizeFilter.entries) { sizeBucket ->
        val isSelected = selectedSizeFilter == sizeBucket
        FilterChip(
          selected = isSelected,
          onClick = {
            if (sizeBucket == TorrentSizeFilter.ALL) {
              onSelectSizeFilter(TorrentSizeFilter.ALL)
            } else {
              onSelectSizeFilter(if (isSelected) TorrentSizeFilter.ALL else sizeBucket)
            }
          },
          label = { Text(sizeBucket.label) },
          modifier = Modifier.testTag("torrent_filter_size_${sizeBucket.name.lowercase()}"),
        )
      }

      if (hasActiveFilter) {
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
