package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.preferences.InterfaceDensity
import com.example.core.preferences.ListingLayout
import com.example.data.model.SmartRecommendation
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecStatus
import com.example.data.model.formatFileSize
import com.example.ui.theme.LocalTorrentClawColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TorrentReleaseRow(
  release: TorrentRelease,
  layout: ListingLayout,
  density: InterfaceDensity,
  isCompareMode: Boolean = false,
  isSelectedForCompare: Boolean = false,
  onToggleCompare: (() -> Unit)? = null,
  onClick: () -> Unit,
  onQuickMagnet: () -> Unit,
  onQuickCopy: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalTorrentClawColors.current
  val verticalPadding = if (density == InterfaceDensity.COMPACT) 9.dp else 13.dp

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = verticalPadding)
        .testTag("torrent_row_${release.infoHash.take(8)}"),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      if (isCompareMode && onToggleCompare != null) {
        Checkbox(
          checked = isSelectedForCompare,
          onCheckedChange = { onToggleCompare() },
          modifier = Modifier.padding(end = 6.dp).size(36.dp),
        )
      }

      // Quality Score Indicator Pill
      QualityScoreBadge(score = release.qualityScore)

      Spacer(Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = release.releaseName,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = if (layout == ListingLayout.EXPANDED) 3 else 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(5.dp))

        // Inline technical spec line (clean badges without box clutter)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SpecBadge(
            label = release.resolution,
            tint =
              if (release.resolution.contains("2160") || release.resolution.contains("4K", true)) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.secondary
              },
          )
          release.sourceType?.let { SpecBadge(label = it, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
          release.videoCodec?.let { SpecBadge(label = it, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
          release.hdrFormat?.let { hdr ->
            val hdrColor =
              if (hdr.contains("Dolby", true)) semantic.dolbyViolet else semantic.hdrGold
            SpecBadge(label = hdr, tint = hdrColor)
          }
          val audioText = listOfNotNull(release.audioCodec, release.audioChannels).joinToString(" ")
          if (audioText.isNotBlank()) {
            SpecBadge(label = audioText, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (release.debridCached) {
            SpecBadge(
              label = "${release.debridProvider?.displayName ?: "Debrid"} Cached",
              tint = semantic.debridCached,
              leadingIcon = {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = semantic.debridCached, modifier = Modifier.size(11.dp))
              },
            )
          }
        }

        Spacer(Modifier.height(6.dp))

        // Health, Size, Tracker & TrueSpec status row
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = formatFileSize(release.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          val seederColor =
            when {
              release.seeders >= 25 -> semantic.seederHigh
              release.seeders >= 5 -> semantic.seederMid
              else -> semantic.seederLow
            }
          Text(
            text = "▲ ${release.seeders}  ▼ ${release.leechers}",
            style = MaterialTheme.typography.labelSmall,
            color = seederColor,
          )
          Text(
            text = release.trackerSource,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          TrueSpecInlineBadge(status = release.trueSpecStatus)
        }

        if (layout == ListingLayout.EXPANDED) {
          Spacer(Modifier.height(6.dp))
          val extraParts =
            listOfNotNull(
              release.indexedAt?.let { "Indexed: $it" },
              release.languages.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Audio: $it" },
              release.subtitles.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Subs: $it" },
            )
          if (extraParts.isNotEmpty()) {
            Text(
              text = extraParts.joinToString("  •  "),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      if (!isCompareMode) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          IconButton(
            onClick = onQuickMagnet,
            modifier = Modifier.size(40.dp).testTag("quick_magnet_${release.infoHash.take(8)}"),
          ) {
            Icon(
              Icons.Rounded.OpenInNew,
              contentDescription = "Open Magnet",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(19.dp),
            )
          }
          IconButton(
            onClick = onQuickCopy,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              Icons.Rounded.ContentCopy,
              contentDescription = "Copy Magnet",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(17.dp),
            )
          }
        }
      }
    }
  }
  HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    thickness = 0.75.dp,
  )
}

@Composable
fun QualityScoreBadge(score: Int, modifier: Modifier = Modifier) {
  val semantic = LocalTorrentClawColors.current
  val color =
    when {
      score >= 80 -> semantic.verified
      score >= 60 -> semantic.hdrGold
      score > 0 -> semantic.warning
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
  Box(
    modifier =
      modifier
        .size(38.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(color.copy(alpha = 0.14f)),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = if (score > 0) "$score" else "--",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = color,
      )
      Text(
        text = "Q",
        style = MaterialTheme.typography.labelSmall,
        color = color.copy(alpha = 0.8f),
      )
    }
  }
}

@Composable
fun SpecBadge(
  label: String,
  tint: Color,
  leadingIcon: (@Composable () -> Unit)? = null,
) {
  Surface(
    color = tint.copy(alpha = 0.12f),
    shape = RoundedCornerShape(5.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leadingIcon != null) {
        leadingIcon()
        Spacer(Modifier.width(3.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
      )
    }
  }
}

@Composable
fun TrueSpecInlineBadge(status: TrueSpecStatus) {
  val semantic = LocalTorrentClawColors.current
  val (color, icon, shortLabel) =
    when (status) {
      TrueSpecStatus.VERIFIED -> Triple(semantic.verified, Icons.Rounded.VerifiedUser, "TrueSpec")
      TrueSpecStatus.PENDING -> Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.HourglassTop, "Scanning")
      TrueSpecStatus.WARNING -> Triple(semantic.warning, Icons.Rounded.WarningAmber, "Warning")
      TrueSpecStatus.SUSPICIOUS -> Triple(semantic.suspicious, Icons.Rounded.GppBad, "Suspicious")
      TrueSpecStatus.UNVERIFIED -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Shield, "Est.")
    }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = status.label, tint = color, modifier = Modifier.size(13.dp))
    Spacer(Modifier.width(3.dp))
    Text(text = shortLabel, style = MaterialTheme.typography.labelSmall, color = color)
  }
}

@Composable
fun SmartRecommendationCard(
  recommendation: SmartRecommendation,
  onInspect: () -> Unit,
  onOpenMagnet: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalTorrentClawColors.current
  Surface(
    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
    shape = RoundedCornerShape(14.dp),
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onInspect),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Rounded.AutoAwesome,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "Recommended Match",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = recommendation.reasons.joinToString(" • "),
          style = MaterialTheme.typography.labelSmall,
          color = semantic.verified,
        )
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = recommendation.release.releaseName,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(8.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "${recommendation.release.resolution} • ${formatFileSize(recommendation.release.sizeBytes)} • ▲ ${recommendation.release.seeders}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        Surface(
          color = MaterialTheme.colorScheme.primary,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.clickable(onClick = onOpenMagnet),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.Download,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "Magnet",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
        }
      }
    }
  }
}
