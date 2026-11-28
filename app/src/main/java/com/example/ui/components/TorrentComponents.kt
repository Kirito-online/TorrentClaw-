package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MediaType
import com.example.data.model.STANDARD_RESOLUTION_OPTIONS
import com.example.data.model.SearchFilter
import com.example.data.model.SortOption
import com.example.data.model.TorrentFilterFacets
import com.example.data.model.TorrentListFilterState
import com.example.data.model.TorrentListSort
import com.example.data.model.TorrentRelease
import com.example.data.model.TorrentSizeFilter
import com.example.data.model.TrueSpecStatus
import com.example.data.model.formatDisplayDate
import com.example.data.model.formatFileSize
import com.example.data.model.formatSeasonEpisodeLabel
import com.example.data.model.mergeFacetOptionsWithDefaults
import com.example.ui.theme.LocalTorrentClawColors
import com.example.util.InstalledTorrentClient

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TorrentReleaseRow(
  release: TorrentRelease,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onMagnetClick: (() -> Unit)? = null,
) {
  val semantic = LocalTorrentClawColors.current
  val scoreColor =
    when {
      release.qualityScore >= 85 -> semantic.verified
      release.qualityScore >= 65 -> MaterialTheme.colorScheme.primary
      else -> semantic.warning
    }

  val episodeLabel = formatSeasonEpisodeLabel(release.seasonNumber, release.episodeNumber)
  val formattedDate = formatDisplayDate(release.releaseDate)

  Card(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = scoreColor.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = release.qualityScore.toString(),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = scoreColor,
            )
            Text(
              text = "Q",
              style = MaterialTheme.typography.labelSmall,
              color = scoreColor,
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = release.releaseName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            episodeLabel?.let { ep ->
              TorrentTagBadge(ep, MaterialTheme.colorScheme.tertiary)
            }
            if (release.resolution.isNotBlank()) {
              TorrentTagBadge(release.resolution, MaterialTheme.colorScheme.primary)
            }
            if (release.sourceType.isNotBlank()) {
              TorrentTagBadge(release.sourceType, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (release.videoCodec.isNotBlank()) {
              TorrentTagBadge(release.videoCodec, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            release.hdrFormat?.takeIf { it.isNotBlank() }?.let { hdr ->
              TorrentTagBadge(hdr, semantic.hdrGold)
            }
            val audioTag =
              listOf(release.audioCodec, release.audioChannels)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (audioTag.isNotBlank()) {
              TorrentTagBadge(audioTag, semantic.dolbyViolet)
            }
            release.language?.takeIf { it.isNotBlank() }?.let { lang ->
              TorrentTagBadge(lang.uppercase(), MaterialTheme.colorScheme.secondary)
            }
            release.editionTags.forEach { edition ->
              TorrentTagBadge(edition, semantic.verified)
            }
            if (release.trueSpecStatus == TrueSpecStatus.VERIFIED) {
              TorrentTagBadge("TrueSpec", semantic.verified)
            } else if (release.trueSpecStatus == TrueSpecStatus.FAILED) {
              TorrentTagBadge("Spec Mismatch", MaterialTheme.colorScheme.error)
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FlowRow(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = formatFileSize(release.sizeBytes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "▲ ${release.seeders}",
            style = MaterialTheme.typography.labelMedium,
            color = semantic.seederHigh,
          )
          Text(
            text = "▼ ${release.leechers}",
            style = MaterialTheme.typography.labelMedium,
            color = semantic.seederLow,
          )
          formattedDate?.let { dateStr ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp),
              )
              Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          release.fileCount?.takeIf { it > 0 }?.let { files ->
            Text(
              text = if (files == 1) "1 file" else "$files files",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (release.trackerSource.isNotBlank()) {
            Text(
              text = release.trackerSource,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Spacer(Modifier.width(8.dp))

        Button(
          onClick = { (onMagnetClick ?: onClick).invoke() },
          shape = RoundedCornerShape(10.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag("torrent_magnet_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Link,
            contentDescription = "Open magnet link",
            modifier = Modifier.size(15.dp),
          )
          Spacer(Modifier.width(5.dp))
          Text(
            text = "Magnet",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
fun TorrentTagBadge(label: String, tint: Color) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = tint.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

@Composable
fun TorrentFilterBar(
  filter: TorrentListFilterState,
  facets: TorrentFilterFacets,
  totalCount: Int,
  onUpdateFilter: (TorrentListFilterState) -> Unit,
  onOpenSheet: () -> Unit,
) {
  val resolutionOptions =
    remember(facets.resolutions) {
      mergeFacetOptionsWithDefaults(STANDARD_RESOLUTION_OPTIONS, facets.resolutions)
    }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenSheet),
        shape = RoundedCornerShape(10.dp),
        color =
          if (filter.activeCount > 0) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surface,
        border =
          BorderStroke(
            1.dp,
            if (filter.activeCount > 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
          ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          BadgedBox(
            badge = {
              if (filter.activeCount > 0) Badge { Text(filter.activeCount.toString()) }
            }
          ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Filter torrents", modifier = Modifier.size(16.dp))
          }
          Text("Filters & Sort", style = MaterialTheme.typography.labelLarge)
        }
      }

      FilterChip(
        selected = filter.resolutions.isEmpty(),
        onClick = { onUpdateFilter(filter.copy(resolutions = emptySet())) },
        label = { Text("All ($totalCount)") },
      )
      resolutionOptions.forEach { (res, count) ->
        val selected = res in filter.resolutions
        FilterChip(
          selected = selected,
          onClick = {
            val next = if (selected) filter.resolutions - res else filter.resolutions + res
            onUpdateFilter(filter.copy(resolutions = next))
          },
          label = { Text("$res ($count)") },
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TorrentSizeFilter.entries.forEach { sizeBucket ->
        FilterChip(
          selected = filter.sizeFilter == sizeBucket,
          onClick = {
            onUpdateFilter(
              filter.copy(
                sizeFilter = sizeBucket,
                customMinSizeGb = null,
                customMaxSizeGb = null,
              )
            )
          },
          label = { Text(sizeBucket.label) },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TorrentFilterBottomSheet(
  filter: TorrentListFilterState,
  facets: TorrentFilterFacets,
  isTvShow: Boolean,
  onApply: (TorrentListFilterState) -> Unit,
  onReset: () -> Unit,
  onDismiss: () -> Unit,
) {
  var draft by remember(filter) { mutableStateOf(filter) }
  val resolutionOptions =
    remember(facets.resolutions) {
      mergeFacetOptionsWithDefaults(STANDARD_RESOLUTION_OPTIONS, facets.resolutions)
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Filter & Sort Torrents", style = MaterialTheme.typography.titleLarge)
        TextButton(
          onClick = {
            onReset()
            onDismiss()
          }
        ) {
          Text("Reset")
        }
      }

      OutlinedTextField(
        value = draft.keyword,
        onValueChange = { draft = draft.copy(keyword = it) },
        label = { Text("Keyword or release group") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      )

      Text("Sort Order", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val sortOptions =
          if (isTvShow) TorrentListSort.entries
          else TorrentListSort.entries.filter { it != TorrentListSort.EPISODE_ASC }
        sortOptions.forEach { sort ->
          FilterChip(
            selected = draft.sortBy == sort,
            onClick = { draft = draft.copy(sortBy = sort) },
            label = { Text(sort.label) },
          )
        }
      }

      Text("Resolution", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        resolutionOptions.forEach { (res, count) ->
          val selected = res in draft.resolutions
          FilterChip(
            selected = selected,
            onClick = {
              val next = if (selected) draft.resolutions - res else draft.resolutions + res
              draft = draft.copy(resolutions = next)
            },
            label = { Text("$res ($count)") },
          )
        }
      }

      Text("File Size", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TorrentSizeFilter.entries.forEach { bucket ->
          FilterChip(
            selected = draft.sizeFilter == bucket,
            onClick = { draft = draft.copy(sizeFilter = bucket) },
            label = { Text(bucket.label) },
          )
        }
      }

      if (facets.codecs.isNotEmpty()) {
        Text("Video Codec", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          facets.codecs.forEach { (codec, count) ->
            val selected = codec in draft.codecs
            FilterChip(
              selected = selected,
              onClick = {
                val next = if (selected) draft.codecs - codec else draft.codecs + codec
                draft = draft.copy(codecs = next)
              },
              label = { Text("$codec ($count)") },
            )
          }
        }
      }

      if (facets.hdrFormats.isNotEmpty()) {
        Text("HDR Format", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          facets.hdrFormats.forEach { (hdr, count) ->
            val selected = hdr in draft.hdrFormats
            FilterChip(
              selected = selected,
              onClick = {
                val next = if (selected) draft.hdrFormats - hdr else draft.hdrFormats + hdr
                draft = draft.copy(hdrFormats = next)
              },
              label = { Text("$hdr ($count)") },
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("TrueSpec Verified Only", style = MaterialTheme.typography.titleSmall)
          Text(
            "${facets.verifiedCount} verified releases",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = draft.verifiedOnly,
          onCheckedChange = { draft = draft.copy(verifiedOnly = it) },
        )
      }

      Button(
        onClick = {
          onApply(draft)
          onDismiss()
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text("Apply Filters")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterBottomSheet(
  filter: SearchFilter,
  availableGenres: List<String>,
  onApply: (SearchFilter) -> Unit,
  onReset: () -> Unit,
  onDismiss: () -> Unit,
) {
  var draft by remember(filter) { mutableStateOf(filter) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Search Filters", style = MaterialTheme.typography.titleLarge)
        TextButton(
          onClick = {
            onReset()
            onDismiss()
          }
        ) {
          Text("Reset")
        }
      }

      Text("Media Type", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaType.entries.forEach { t ->
          FilterChip(
            selected = draft.mediaType == t,
            onClick = { draft = draft.copy(mediaType = t) },
            label = { Text(t.label) },
          )
        }
      }

      Text("Sort Results", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SortOption.entries.forEach { s ->
          FilterChip(
            selected = draft.sortBy == s,
            onClick = { draft = draft.copy(sortBy = s) },
            label = { Text(s.label) },
          )
        }
      }

      Text("Quality", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to "Any", "2160p" to "2160p / 4K", "1080p" to "1080p", "720p" to "720p", "480p" to "480p").forEach { (res, lbl) ->
          FilterChip(
            selected = draft.resolution == res,
            onClick = { draft = draft.copy(resolution = res) },
            label = { Text(lbl) },
          )
        }
      }

      Text("HDR", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to "Any", "hdr10" to "HDR10", "dolby_vision" to "Dolby Vision").forEach { (h, lbl) ->
          FilterChip(
            selected = draft.hdr == h,
            onClick = { draft = draft.copy(hdr = h) },
            label = { Text(lbl) },
          )
        }
      }

      if (availableGenres.isNotEmpty()) {
        Text("Genre", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = draft.genre == null,
            onClick = { draft = draft.copy(genre = null) },
            label = { Text("All") },
          )
          availableGenres.take(14).forEach { g ->
            FilterChip(
              selected = draft.genre.equals(g, true),
              onClick = { draft = draft.copy(genre = if (draft.genre.equals(g, true)) null else g) },
              label = { Text(g) },
            )
          }
        }
      }

      Button(
        onClick = {
          onApply(draft)
          onDismiss()
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text("Apply Search Filters")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MagnetChooserSheet(
  release: TorrentRelease,
  installedClients: List<InstalledTorrentClient>,
  preferredPackage: String?,
  isDownloading: Boolean,
  hasSavedApiKey: Boolean = false,
  onDismiss: () -> Unit,
  onOpenInClient: (clientPackage: String?, rememberDefault: Boolean) -> Unit,
  onCopyMagnet: () -> Unit,
  onCopyHash: () -> Unit,
  onDownloadTorrentFile: () -> Unit,
  onShare: () -> Unit,
  onOpenOnWeb: () -> Unit = {},
  onCopyReleaseTitle: () -> Unit = {},
  onSaveApiKey: (String?) -> Unit = {},
) {
  var rememberDefault by remember(preferredPackage) { mutableStateOf(false) }
  var showApiKeyEditor by remember(release.releaseName, hasSavedApiKey) { mutableStateOf(false) }
  var apiKeyInput by remember { mutableStateOf("") }
  val semantic = LocalTorrentClawColors.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = release.releaseName,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )

      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        formatSeasonEpisodeLabel(release.seasonNumber, release.episodeNumber)?.let {
          TorrentTagBadge(it, MaterialTheme.colorScheme.tertiary)
        }
        if (release.resolution.isNotBlank()) TorrentTagBadge(release.resolution, MaterialTheme.colorScheme.primary)
        if (release.sourceType.isNotBlank()) TorrentTagBadge(release.sourceType, MaterialTheme.colorScheme.onSurfaceVariant)
        if (release.videoCodec.isNotBlank()) TorrentTagBadge(release.videoCodec, MaterialTheme.colorScheme.onSurfaceVariant)
        release.hdrFormat?.takeIf { it.isNotBlank() }?.let { TorrentTagBadge(it, semantic.hdrGold) }
        val sheetAudio = listOf(release.audioCodec, release.audioChannels).filter { it.isNotBlank() }.joinToString(" ")
        if (sheetAudio.isNotBlank()) TorrentTagBadge(sheetAudio, semantic.dolbyViolet)
        TorrentTagBadge(formatFileSize(release.sizeBytes), MaterialTheme.colorScheme.onSurfaceVariant)
        TorrentTagBadge("Score ${release.qualityScore}/100", semantic.verified)
        TorrentTagBadge("▲ ${release.seeders} / ▼ ${release.leechers}", semantic.seederHigh)
        formatDisplayDate(release.releaseDate)?.let { dateStr ->
          TorrentTagBadge(dateStr, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (release.trackerSource.isNotBlank()) {
          TorrentTagBadge(release.trackerSource, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        release.language?.takeIf { it.isNotBlank() }?.let {
          TorrentTagBadge(it.uppercase(), MaterialTheme.colorScheme.secondary)
        }
        release.editionTags.forEach { edition ->
          TorrentTagBadge(edition, semantic.verified)
        }
        if (release.trueSpecStatus == TrueSpecStatus.VERIFIED) {
          TorrentTagBadge("TrueSpec Verified", semantic.verified)
        }
      }

      if (release.hasDirectMagnet) {
        if (release.infoHash.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Hash: ${release.infoHash}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(10.dp),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        if (installedClients.isNotEmpty()) {
          val primaryClient =
            installedClients.firstOrNull { it.packageName == preferredPackage } ?: installedClients.first()
          Button(
            onClick = { onOpenInClient(primaryClient.packageName, rememberDefault) },
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("magnet_open_app_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in ${primaryClient.label}")
          }

          if (installedClients.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Or pick torrent client",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                installedClients.forEach { client ->
                  OutlinedButton(
                    onClick = { onOpenInClient(client.packageName, rememberDefault) },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                  ) {
                    Text(client.label)
                  }
                }
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).clickable { rememberDefault = !rememberDefault },
              ) {
                Checkbox(checked = rememberDefault, onCheckedChange = { rememberDefault = it })
                Text(
                  text = "Remember preferred torrent app",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "No torrent client app detected on this device. Copy or share the magnet link below.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onCopyMagnet,
            modifier = Modifier.weight(1f).height(48.dp).testTag("magnet_copy_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.magnet_sheet_copy), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          OutlinedButton(
            onClick = onShare,
            modifier = Modifier.weight(0.7f).height(48.dp).testTag("magnet_share_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.magnet_sheet_share))
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onDownloadTorrentFile,
            enabled = !isDownloading,
            modifier = Modifier.weight(1f).height(48.dp).testTag("magnet_download_torrent_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            if (isDownloading) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              Spacer(Modifier.width(8.dp))
              Text("Downloading…")
            } else {
              Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(".torrent File", maxLines = 1)
            }
          }
          OutlinedButton(
            onClick = onCopyHash,
            modifier = Modifier.weight(0.9f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(Icons.Rounded.Tag, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy Hash", maxLines = 1)
          }
        }
      } else {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text =
              "TorrentClaw requires opening this title on TorrentClaw.com or using a free API key (torrentclaw.com/register) to unlock direct magnet links and .torrent downloads.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        }

        Button(
          onClick = onOpenOnWeb,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("magnet_open_web_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Open on TorrentClaw.com")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onCopyReleaseTitle,
            modifier = Modifier.weight(1f).height(48.dp).testTag("magnet_copy_title_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy Title", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          OutlinedButton(
            onClick = onShare,
            modifier = Modifier.weight(0.8f).height(48.dp).testTag("magnet_share_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.magnet_sheet_share))
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        OutlinedButton(
          onClick = { showApiKeyEditor = !showApiKeyEditor },
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("magnet_toggle_api_key_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          Text(if (hasSavedApiKey) "Update or Clear Saved API Key" else "Add Free TorrentClaw API Key")
        }

        if (showApiKeyEditor) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = apiKeyInput,
              onValueChange = { apiKeyInput = it },
              label = { Text("TorrentClaw API Key") },
              placeholder = { Text("Paste key from torrentclaw.com/register") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("magnet_api_key_input"),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { onSaveApiKey(apiKeyInput) },
                enabled = apiKeyInput.isNotBlank(),
                modifier = Modifier.weight(1f).height(48.dp).testTag("magnet_save_api_key_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Text("Save Key")
              }
              if (hasSavedApiKey) {
                OutlinedButton(
                  onClick = { onSaveApiKey(null) },
                  modifier = Modifier.height(48.dp).testTag("magnet_clear_api_key_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Text("Clear")
                }
              }
            }
          }
        }
      }
    }
  }
}
