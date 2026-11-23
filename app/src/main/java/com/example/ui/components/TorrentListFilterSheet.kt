package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.model.TorrentRelease
import com.example.data.model.TorrentSizeFilter
import com.example.data.model.filterAndSortTorrents
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TorrentListFilterSheet(
  torrents: List<TorrentRelease>,
  facets: TorrentFilterFacets,
  initialFilter: TorrentListFilterState,
  onApply: (TorrentListFilterState) -> Unit,
  onReset: () -> Unit,
  onDismiss: () -> Unit,
) {
  var working by remember(initialFilter) { mutableStateOf(initialFilter) }
  val maxSliderGb = facets.maxSizeGb.coerceAtLeast(10f)
  var sliderRange by remember(initialFilter, maxSliderGb) {
    mutableStateOf(
      (initialFilter.customMinSizeGb ?: 0f).coerceIn(0f, maxSliderGb)..(initialFilter.customMaxSizeGb ?: maxSliderGb).coerceIn(0f, maxSliderGb)
    )
  }

  val liveMatchCount = remember(torrents, working) {
    filterAndSortTorrents(torrents, working).size
  }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Filter & Sort Torrents",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = {
            working = TorrentListFilterState()
            sliderRange = 0f..maxSliderGb
            onReset()
          },
          modifier = Modifier.testTag("torrent_sheet_reset"),
        ) {
          Text("Reset All")
        }
      }

      Spacer(Modifier.height(10.dp))

      // Keyword / Release Group Filter
      OutlinedTextField(
        value = working.keyword,
        onValueChange = { working = working.copy(keyword = it) },
        placeholder = { Text("Keyword, release group, tracker…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (working.keyword.isNotEmpty()) {
            IconButton(onClick = { working = working.copy(keyword = "") }) {
              Icon(Icons.Rounded.Clear, contentDescription = "Clear keyword", modifier = Modifier.size(18.dp))
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("torrent_sheet_keyword"),
      )

      Spacer(Modifier.height(14.dp))
      SheetSectionLabel("Sort By")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TorrentListSort.entries.forEach { option ->
          FilterChip(
            selected = working.sortBy == option,
            onClick = { working = working.copy(sortBy = option) },
            label = { Text(option.label) },
          )
        }
      }

      if (facets.resolutions.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SheetSectionLabel("Quality / Resolution")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = working.resolutions.isEmpty(),
            onClick = { working = working.copy(resolutions = emptySet()) },
            label = { Text("All (${torrents.size})") },
          )
          facets.resolutions.forEach { (res, count) ->
            val selected = working.resolutions.any { it.equals(res, ignoreCase = true) }
            FilterChip(
              selected = selected,
              onClick = {
                val next =
                  if (selected) working.resolutions.filterNot { it.equals(res, ignoreCase = true) }.toSet()
                  else working.resolutions + res
                working = working.copy(resolutions = next)
              },
              label = { Text("$res ($count)") },
            )
          }
        }
      }

      Spacer(Modifier.height(14.dp))
      SheetSectionLabel("File Size")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TorrentSizeFilter.entries.forEach { preset ->
          val selected = !working.hasCustomSizeRange && working.sizePreset == preset
          FilterChip(
            selected = selected,
            onClick = {
              sliderRange = (preset.minGb ?: 0f).coerceIn(0f, maxSliderGb)..(preset.maxGb ?: maxSliderGb).coerceIn(0f, maxSliderGb)
              working =
                working.copy(
                  sizePreset = preset,
                  customMinSizeGb = null,
                  customMaxSizeGb = null,
                )
            },
            label = { Text(preset.label) },
          )
        }
      }

      Spacer(Modifier.height(8.dp))
      val minText = String.format(Locale.US, "%.1f GB", sliderRange.start)
      val maxText =
        if (sliderRange.endInclusive >= maxSliderGb - 0.1f) "${String.format(Locale.US, "%.0f", maxSliderGb)}+ GB"
        else String.format(Locale.US, "%.1f GB", sliderRange.endInclusive)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Custom Size Range",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = if (working.hasCustomSizeRange) "$minText – $maxText" else "Any",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      RangeSlider(
        value = sliderRange,
        onValueChange = { range ->
          sliderRange = range
          val customMin = if (range.start > 0.2f) range.start else null
          val customMax = if (range.endInclusive < maxSliderGb - 0.2f) range.endInclusive else null
          working =
            working.copy(
              sizePreset = TorrentSizeFilter.ALL,
              customMinSizeGb = customMin,
              customMaxSizeGb = customMax,
            )
        },
        valueRange = 0f..maxSliderGb,
      )

      if (facets.sources.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        SheetSectionLabel("Source Type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          facets.sources.forEach { (src, count) ->
            val selected = working.sources.any { it.equals(src, ignoreCase = true) }
            FilterChip(
              selected = selected,
              onClick = {
                val next =
                  if (selected) working.sources.filterNot { it.equals(src, ignoreCase = true) }.toSet()
                  else working.sources + src
                working = working.copy(sources = next)
              },
              label = { Text("$src ($count)") },
            )
          }
        }
      }

      if (facets.codecs.isNotEmpty() || facets.hdrFormats.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SheetSectionLabel("Video Codec & HDR")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          facets.codecs.forEach { (codec, count) ->
            val selected = working.codecs.any { it.equals(codec, ignoreCase = true) }
            FilterChip(
              selected = selected,
              onClick = {
                val next =
                  if (selected) working.codecs.filterNot { it.equals(codec, ignoreCase = true) }.toSet()
                  else working.codecs + codec
                working = working.copy(codecs = next)
              },
              label = { Text("$codec ($count)") },
            )
          }
          facets.hdrFormats.forEach { (hdr, count) ->
            val selected = working.hdrFormats.any { it.equals(hdr, ignoreCase = true) }
            FilterChip(
              selected = selected,
              onClick = {
                val next =
                  if (selected) working.hdrFormats.filterNot { it.equals(hdr, ignoreCase = true) }.toSet()
                  else working.hdrFormats + hdr
                working = working.copy(hdrFormats = next)
              },
              label = { Text("$hdr ($count)") },
            )
          }
        }
      }

      if (facets.audioFormats.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SheetSectionLabel("Audio Format")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          facets.audioFormats.forEach { (audio, count) ->
            val selected = working.audioFormats.any { it.equals(audio, ignoreCase = true) }
            FilterChip(
              selected = selected,
              onClick = {
                val next =
                  if (selected) working.audioFormats.filterNot { it.equals(audio, ignoreCase = true) }.toSet()
                  else working.audioFormats + audio
                working = working.copy(audioFormats = next)
              },
              label = { Text("$audio ($count)") },
            )
          }
        }
      }

      Spacer(Modifier.height(14.dp))
      SheetSectionLabel("Swarm & Verification")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = working.minSeeders == 1,
          onClick = {
            working = working.copy(minSeeders = if (working.minSeeders == 1) null else 1)
          },
          label = { Text("Seeders > 0") },
        )
        FilterChip(
          selected = working.minSeeders == 10,
          onClick = {
            working = working.copy(minSeeders = if (working.minSeeders == 10) null else 10)
          },
          label = { Text("Seeders ≥ 10") },
        )
        if (facets.verifiedCount > 0) {
          FilterChip(
            selected = working.verifiedOnly,
            onClick = { working = working.copy(verifiedOnly = !working.verifiedOnly) },
            label = { Text("TrueSpec Verified (${facets.verifiedCount})") },
          )
        }
        if (facets.debridCachedCount > 0) {
          FilterChip(
            selected = working.debridCachedOnly,
            onClick = { working = working.copy(debridCachedOnly = !working.debridCachedOnly) },
            label = { Text("Debrid Cached (${facets.debridCachedCount})") },
          )
        }
      }

      Spacer(Modifier.height(18.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(Modifier.height(14.dp))

      Button(
        onClick = {
          onApply(working)
          onDismiss()
        },
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("torrent_sheet_apply"),
      ) {
        Text("Show $liveMatchCount of ${torrents.size} Torrents")
      }
    }
  }
}

@Composable
private fun SheetSectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 6.dp),
  )
}
