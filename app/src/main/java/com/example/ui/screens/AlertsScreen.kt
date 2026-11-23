package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.ContentAlertEntity
import com.example.data.model.UserAccount
import com.example.ui.components.TorrentClawEmptyState
import com.example.ui.theme.LocalTorrentClawColors
import com.example.util.NotificationHelper

@Composable
fun AlertsScreen(
  alerts: List<ContentAlertEntity>,
  account: UserAccount,
  isChecking: Boolean,
  lastCheckSummary: String?,
  onCheckNow: () -> Unit,
  onToggleAlert: (String, Boolean) -> Unit,
  onDeleteAlert: (String) -> Unit,
  onUpdateAlertPrefs: (ContentAlertEntity, String, Int) -> Unit,
  onOpenMedia: (String) -> Unit,
  onNavigateSearch: () -> Unit,
) {
  val context = LocalContext.current
  val semantic = LocalTorrentClawColors.current
  var hasPermission by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
  var editingAlert by remember { mutableStateOf<ContentAlertEntity?>(null) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
    }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Release Alerts", style = MaterialTheme.typography.headlineSmall)
        Text(
          text = "${alerts.size} / ${account.tier.maxAlerts} alerts (${account.tier.label})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
        onClick = onCheckNow,
        enabled = !isChecking && alerts.isNotEmpty(),
        modifier = Modifier.testTag("alerts_check_now_button"),
      ) {
        if (isChecking) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text("Check Now")
      }
    }

    LinearProgressIndicator(
      progress = { (alerts.size.toFloat() / account.tier.maxAlerts.coerceAtLeast(1)).coerceIn(0f, 1f) },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )

    if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(10.dp))
          Text(
            text = "Allow notifications so TorrentClaw can alert you when new matching releases appear.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Text("Allow")
          }
        }
      }
    }

    lastCheckSummary?.let { msg ->
      Text(
        text = msg,
        style = MaterialTheme.typography.labelMedium,
        color = semantic.verified,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
      )
    }

    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    if (alerts.isEmpty()) {
      TorrentClawEmptyState(
        title = "No Followed Titles",
        message = "Tap the Bell icon on any movie or TV show detail page to monitor new releases matching your preferred resolution and quality score.",
        actionLabel = "Discover Titles",
        onAction = onNavigateSearch,
      )
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(alerts, key = { it.mediaId }) { alert ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onOpenMedia(alert.mediaId) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (!alert.posterUrl.isNullOrBlank()) {
              AsyncImage(
                model = alert.posterUrl,
                contentDescription = alert.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp, 64.dp).clip(RoundedCornerShape(8.dp)),
              )
              Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(alert.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                text = "Target: ${alert.preferredResolution} • Score ≥ ${alert.minQualityScore}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (alert.newMatchesCount > 0) {
                Text(
                  text = "+${alert.newMatchesCount} new matching release(s)",
                  style = MaterialTheme.typography.labelSmall,
                  color = semantic.verified,
                )
              }
            }
            IconButton(onClick = { editingAlert = alert }) {
              Icon(Icons.Rounded.Tune, contentDescription = "Alert preferences")
            }
            Switch(
              checked = alert.enabled,
              onCheckedChange = { onToggleAlert(alert.mediaId, it) },
            )
            IconButton(onClick = { onDeleteAlert(alert.mediaId) }) {
              Icon(Icons.Rounded.DeleteOutline, contentDescription = "Unfollow")
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
      }
    }
  }

  editingAlert?.let { target ->
    var selectedRes by remember(target) { mutableStateOf(target.preferredResolution) }
    var score by remember(target) { mutableIntStateOf(target.minQualityScore) }
    AlertDialog(
      onDismissRequest = { editingAlert = null },
      title = { Text("Alert Rules: ${target.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
      text = {
        Column {
          Text("Preferred Resolution", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("2160p", "1080p", "720p").forEach { res ->
              FilterChip(
                selected = selectedRes == res,
                onClick = { selectedRes = res },
                label = { Text(res) },
              )
            }
          }
          Spacer(Modifier.height(10.dp))
          Text("Minimum Quality Score: $score", style = MaterialTheme.typography.labelLarge)
          Slider(
            value = score.toFloat(),
            onValueChange = { score = it.toInt() },
            valueRange = 0f..95f,
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          onUpdateAlertPrefs(target, selectedRes, score)
          editingAlert = null
        }) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { editingAlert = null }) { Text("Cancel") }
      },
    )
  }
}
