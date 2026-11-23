package com.example.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DebridAccount
import com.example.data.model.DebridProvider
import com.example.data.model.TorrentRelease
import com.example.data.model.TrueSpecStatus
import com.example.data.model.formatFileSize
import com.example.ui.theme.LocalTorrentClawColors
import com.example.util.InstalledTorrentClient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TorrentInspectorSheet(
  release: TorrentRelease,
  isSaved: Boolean,
  isScanningTrueSpec: Boolean,
  debridAccounts: List<DebridAccount>,
  onDismiss: () -> Unit,
  onOpenMagnet: () -> Unit,
  onCopyMagnet: () -> Unit,
  onCopyHash: () -> Unit,
  onDownloadTorrentFile: () -> Unit,
  onShare: () -> Unit,
  onToggleSave: () -> Unit,
  onRequestTrueSpecScan: () -> Unit,
  onSendToDebrid: (DebridProvider) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val semantic = LocalTorrentClawColors.current

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
      Row(verticalAlignment = Alignment.CenterVertically) {
        QualityScoreBadge(score = release.qualityScore)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = release.releaseName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          release.mediaTitle?.let {
            Text(
              text = it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      // Primary actions grid
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = onOpenMagnet,
          modifier = Modifier.weight(1f).testTag("inspector_open_magnet"),
        ) {
          Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Open Magnet")
        }
        OutlinedButton(
          onClick = onDownloadTorrentFile,
          modifier = Modifier.weight(1f).testTag("inspector_download_torrent"),
        ) {
          Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(".torrent")
        }
      }

      Spacer(Modifier.height(8.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onCopyMagnet, modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Copy Magnet")
        }
        OutlinedButton(onClick = onToggleSave, modifier = Modifier.weight(1f)) {
          Icon(
            if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(if (isSaved) "Saved" else "Save")
        }
        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Share")
        }
      }

      // Connected Debrid one-click queue
      val connectedDebrid = debridAccounts.filter { it.isConnected }
      if (connectedDebrid.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          connectedDebrid.forEach { acc ->
            OutlinedButton(onClick = { onSendToDebrid(acc.provider) }) {
              Icon(Icons.Rounded.Bolt, contentDescription = null, tint = semantic.debridCached, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Send to ${acc.provider.displayName}")
            }
          }
        }
      }

      Spacer(Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(Modifier.height(14.dp))

      // TrueSpec Verification Panel (clearly separates Verified vs Filename-Estimated)
      TrueSpecPanel(
        release = release,
        isScanning = isScanningTrueSpec,
        onRequestScan = onRequestTrueSpecScan,
      )

      Spacer(Modifier.height(16.dp))

      Text(
        text = "Technical Specifications",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(8.dp))

      val specOrigin =
        if (release.trueSpecStatus == TrueSpecStatus.VERIFIED) "Verified by TrueSpec"
        else "Estimated from release filename"
      Text(
        text = specOrigin,
        style = MaterialTheme.typography.labelSmall,
        color =
          if (release.trueSpecStatus == TrueSpecStatus.VERIFIED) semantic.verified
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(8.dp))

      KeyValueRow("Resolution", release.resolution)
      KeyValueRow("Source Type", release.sourceType ?: "Unknown")
      KeyValueRow("Video Codec", release.videoCodec ?: "Unknown")
      KeyValueRow(
        "Audio",
        listOfNotNull(release.audioCodec, release.audioChannels).joinToString(" ").ifBlank { "Unknown" },
      )
      KeyValueRow("HDR Format", release.hdrFormat ?: "SDR / None detected")
      KeyValueRow("File Size", formatFileSize(release.sizeBytes))
      KeyValueRow("Swarm Health", "${release.seeders} seeders / ${release.leechers} leechers")
      KeyValueRow("Tracker / Source", release.trackerSource)
      if (release.subtitles.isNotEmpty()) {
        KeyValueRow("Subtitles", release.subtitles.joinToString(", "))
      }
      if (release.languages.isNotEmpty()) {
        KeyValueRow("Languages", release.languages.joinToString(", "))
      }
      release.indexedAt?.let { KeyValueRow("Indexed", it) }

      Spacer(Modifier.height(8.dp))
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onCopyHash)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Info Hash", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(release.infoHash, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Hash", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

@Composable
private fun TrueSpecPanel(
  release: TorrentRelease,
  isScanning: Boolean,
  onRequestScan: () -> Unit,
) {
  val semantic = LocalTorrentClawColors.current
  val report = release.trueSpecReport
  val (accent, icon, description) =
    when (release.trueSpecStatus) {
      TrueSpecStatus.VERIFIED ->
        Triple(
          semantic.verified,
          Icons.Rounded.VerifiedUser,
          "Swarm header inspected via FFprobe & security check. Media codecs and container match release claims.",
        )
      TrueSpecStatus.PENDING ->
        Triple(
          MaterialTheme.colorScheme.primary,
          Icons.Rounded.Shield,
          "TrueSpec analysis is queued or inspecting swarm sample…",
        )
      TrueSpecStatus.WARNING ->
        Triple(
          semantic.warning,
          Icons.Rounded.WarningAmber,
          "TrueSpec detected discrepancies between the release filename and actual media stream headers.",
        )
      TrueSpecStatus.SUSPICIOUS ->
        Triple(
          semantic.suspicious,
          Icons.Rounded.GppBad,
          "Security warning: suspicious files, fake resolution, or decoy executables detected. Avoid downloading.",
        )
      TrueSpecStatus.UNVERIFIED ->
        Triple(
          MaterialTheme.colorScheme.onSurfaceVariant,
          Icons.Rounded.Shield,
          "Not yet inspected by TrueSpec. Metadata above is parsed from the release filename.",
        )
    }

  Surface(
    color = accent.copy(alpha = 0.11f),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text = release.trueSpecStatus.label,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = accent,
          modifier = Modifier.weight(1f),
        )
        if (isScanning) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else if (release.trueSpecStatus != TrueSpecStatus.VERIFIED) {
          TextButton(onClick = onRequestScan) {
            Text("Run TrueSpec")
          }
        }
      }
      Spacer(Modifier.height(4.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (report != null) {
        if (report.warnings.isNotEmpty() || report.mismatchReasons.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          (report.warnings + report.mismatchReasons).forEach { w ->
            Text(
              text = "• $w",
              style = MaterialTheme.typography.bodySmall,
              color = semantic.warning,
            )
          }
        }
        report.primaryFileName?.let { file ->
          Spacer(Modifier.height(6.dp))
          Text(
            text = "Primary file: $file",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(12.dp))
    Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
fun TorrentClientPickerDialog(
  clients: List<InstalledTorrentClient>,
  onSelect: (InstalledTorrentClient, Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  var saveAsDefault by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Choose Torrent Client") },
    text = {
      Column {
        clients.forEach { client ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onSelect(client, saveAsDefault) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
              Text(client.label, style = MaterialTheme.typography.titleSmall)
              Text(client.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { saveAsDefault = !saveAsDefault },
        ) {
          Checkbox(checked = saveAsDefault, onCheckedChange = { saveAsDefault = it })
          Text("Set as preferred client", style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun MissingTorrentClientDialog(
  release: TorrentRelease,
  onCopyMagnet: () -> Unit,
  onShareMagnet: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("No Torrent Client Found") },
    text = {
      Text(
        "No compatible BitTorrent app (e.g., LibreTorrent, Flud, qBittorrent) is installed on this device to open magnet links directly. You can copy or share the magnet link, or download the .torrent file instead.",
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Button(onClick = {
        onCopyMagnet()
        onDismiss()
      }) {
        Text("Copy Magnet")
      }
    },
    dismissButton = {
      Row {
        TextButton(onClick = {
          onShareMagnet()
          onDismiss()
        }) {
          Text("Share")
        }
        TextButton(onClick = onDismiss) { Text("Close") }
      }
    },
  )
}
