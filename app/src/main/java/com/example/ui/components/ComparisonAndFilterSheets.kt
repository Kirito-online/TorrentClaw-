package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaType
import com.example.data.model.SearchFilter
import com.example.data.model.SortOption
import com.example.data.model.TorrentRelease
import com.example.data.model.formatFileSize
import com.example.ui.theme.LocalTorrentClawColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseComparisonSheet(
  releases: List<TorrentRelease>,
  onDismiss: () -> Unit,
  onSelectRelease: (TorrentRelease) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val semantic = LocalTorrentClawColors.current
  val bestScore = releases.maxOfOrNull { it.qualityScore } ?: 0
  val maxSeeders = releases.maxOfOrNull { it.seeders } ?: 0
  val minSize = releases.filter { it.sizeBytes > 0 }.minOfOrNull { it.sizeBytes }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .padding(bottom = 28.dp),
    ) {
      Text(
        text = "Smart Release Comparison (${releases.size})",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = "Side-by-side breakdown of resolution, codecs, HDR, swarm health, and TrueSpec score.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(14.dp))

      Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        releases.forEach { rel ->
          val isWinner = rel.qualityScore == bestScore && bestScore > 0
          Surface(
            color =
              if (isWinner) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.width(210.dp),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              if (isWinner) {
                Text(
                  text = "BEST MATCH",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
              }
              Text(
                text = rel.releaseName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(Modifier.height(10.dp))
              CompareMetric("Quality Score", "${rel.qualityScore}/100", highlight = isWinner, highlightColor = semantic.verified)
              CompareMetric("Resolution", rel.resolution)
              CompareMetric("Source", rel.sourceType ?: "Unknown")
              CompareMetric("Video Codec", rel.videoCodec ?: "Unknown")
              CompareMetric("Audio", listOfNotNull(rel.audioCodec, rel.audioChannels).joinToString(" ").ifBlank { "Unknown" })
              CompareMetric("HDR", rel.hdrFormat ?: "SDR")
              CompareMetric("Size", formatFileSize(rel.sizeBytes), highlight = rel.sizeBytes == minSize, highlightColor = semantic.debridCached)
              CompareMetric("Seeders", "${rel.seeders} (▼ ${rel.leechers})", highlight = rel.seeders == maxSeeders && maxSeeders > 0, highlightColor = semantic.seederHigh)
              CompareMetric("TrueSpec", rel.trueSpecStatus.label)
              Spacer(Modifier.height(10.dp))
              Button(onClick = { onSelectRelease(rel) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open / Inspect")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CompareMetric(
  label: String,
  value: String,
  highlight: Boolean = false,
  highlightColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
  Column(modifier = Modifier.padding(vertical = 3.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium),
      color = if (highlight) highlightColor else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
  initialFilter: SearchFilter,
  availableGenres: List<String>,
  onApply: (SearchFilter) -> Unit,
  onReset: () -> Unit,
  onDismiss: () -> Unit,
) {
  var working by remember { mutableStateOf(initialFilter) }
  var yearMinText by remember { mutableStateOf(initialFilter.yearMin?.toString() ?: "") }
  var yearMaxText by remember { mutableStateOf(initialFilter.yearMax?.toString() ?: "") }
  var seasonText by remember { mutableStateOf(initialFilter.season?.toString() ?: "") }
  var episodeText by remember { mutableStateOf(initialFilter.episode?.toString() ?: "") }
  var minSeedersText by remember { mutableStateOf(initialFilter.minSeeders?.toString() ?: "") }
  var maxSizeText by remember { mutableStateOf(initialFilter.maxSizeGb?.toString() ?: "") }
  var minScoreText by remember { mutableStateOf(initialFilter.minQualityScore?.toString() ?: "") }

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
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Advanced Filters & Sort", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = {
          onReset()
          onDismiss()
        }) {
          Text("Reset All")
        }
      }

      Spacer(Modifier.height(10.dp))
      FilterLabel("Sort Results")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SortOption.entries.forEach { option ->
          FilterChip(
            selected = working.sortBy == option,
            onClick = { working = working.copy(sortBy = option) },
            label = { Text(option.label) },
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      FilterLabel("Content Type")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaType.entries.forEach { type ->
          FilterChip(
            selected = working.mediaType == type,
            onClick = { working = working.copy(mediaType = type) },
            label = { Text(type.label) },
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      FilterLabel("Resolution")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("2160p", "1080p", "720p", "480p").forEach { res ->
          FilterChip(
            selected = res in working.resolutions,
            onClick = {
              val next = if (res in working.resolutions) working.resolutions - res else working.resolutions + res
              working = working.copy(resolutions = next)
            },
            label = { Text(res) },
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      FilterLabel("Source Quality")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("REMUX", "BluRay", "WEB-DL", "WEBRip").forEach { src ->
          FilterChip(
            selected = src in working.sourceQualities,
            onClick = {
              val next =
                if (src in working.sourceQualities) working.sourceQualities - src
                else working.sourceQualities + src
              working = working.copy(sourceQualities = next)
            },
            label = { Text(src) },
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      FilterLabel("Video Codec & HDR")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("HEVC", "AV1", "H.264").forEach { codec ->
          FilterChip(
            selected = codec in working.codecs,
            onClick = {
              val next = if (codec in working.codecs) working.codecs - codec else working.codecs + codec
              working = working.copy(codecs = next)
            },
            label = { Text(codec) },
          )
        }
        listOf("Dolby Vision", "HDR10+", "HDR10").forEach { hdr ->
          FilterChip(
            selected = hdr in working.hdrFormats,
            onClick = {
              val next = if (hdr in working.hdrFormats) working.hdrFormats - hdr else working.hdrFormats + hdr
              working = working.copy(hdrFormats = next)
            },
            label = { Text(hdr) },
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      FilterLabel("Audio Format")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("atmos", "truehd", "flac", "opus", "aac").forEach { audio ->
          FilterChip(
            selected = audio in working.audioFormats,
            onClick = {
              val next =
                if (audio in working.audioFormats) working.audioFormats - audio
                else working.audioFormats + audio
              working = working.copy(audioFormats = next)
            },
            label = { Text(audio.uppercase()) },
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      FilterLabel("Language")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "ja" to "Japanese", "ko" to "Korean")
          .forEach { (code, name) ->
            FilterChip(
              selected = code in working.languages,
              onClick = {
                val next =
                  if (code in working.languages) working.languages - code
                  else working.languages + code
                working = working.copy(languages = next)
              },
              label = { Text(name) },
            )
          }
      }

      if (availableGenres.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        FilterLabel("Genre")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          availableGenres.take(14).forEach { g ->
            FilterChip(
              selected = working.genre.equals(g, ignoreCase = true),
              onClick = { working = working.copy(genre = if (working.genre == g) null else g) },
              label = { Text(g) },
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      FilterLabel("Year, Episode & Thresholds")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = yearMinText,
          onValueChange = { yearMinText = it.filter(Char::isDigit).take(4) },
          label = { Text("Year Min") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = yearMaxText,
          onValueChange = { yearMaxText = it.filter(Char::isDigit).take(4) },
          label = { Text("Year Max") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = seasonText,
          onValueChange = { seasonText = it.filter(Char::isDigit).take(3) },
          label = { Text("Season") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = episodeText,
          onValueChange = { episodeText = it.filter(Char::isDigit).take(3) },
          label = { Text("Episode") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = minSeedersText,
          onValueChange = { minSeedersText = it.filter(Char::isDigit).take(4) },
          label = { Text("Min Seeders") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = maxSizeText,
          onValueChange = { maxSizeText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
          label = { Text("Max GB") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = minScoreText,
          onValueChange = { minScoreText = it.filter(Char::isDigit).take(3) },
          label = { Text("Min Score") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(Modifier.height(18.dp))
      Button(
        onClick = {
          onApply(
            working.copy(
              yearMin = yearMinText.toIntOrNull(),
              yearMax = yearMaxText.toIntOrNull(),
              season = seasonText.toIntOrNull(),
              episode = episodeText.toIntOrNull(),
              minSeeders = minSeedersText.toIntOrNull(),
              maxSizeGb = maxSizeText.toFloatOrNull(),
              minQualityScore = minScoreText.toIntOrNull(),
            )
          )
          onDismiss()
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
      ) {
        Text("Apply Filters")
      }
    }
  }
}

@Composable
private fun FilterLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 6.dp),
  )
}
