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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.web.MagnetTarget
import com.example.data.web.NavigationGuard
import com.example.data.web.ParsedWebsitePage
import com.example.ui.theme.LocalTorrentClawColors
import com.example.util.InstalledTorrentClient

@Composable
fun WebsiteTopUrlBar(
  currentUrl: String,
  canGoBack: Boolean,
  isLoading: Boolean,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
  onShare: () -> Unit,
  onCopyUrl: () -> Unit,
  onNavigateUrl: (String) -> Unit,
) {
  var showUrlDialog by remember { mutableStateOf(false) }
  var editableUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
  val displayPath = remember(currentUrl) { NavigationGuard.formatDisplayPath(currentUrl) }
  val semantic = LocalTorrentClawColors.current

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onBack,
        enabled = canGoBack,
        modifier = Modifier.size(48.dp).testTag("url_bar_back"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.url_bar_back),
          tint =
            if (canGoBack) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
      }

      Surface(
        modifier =
          Modifier.weight(1f)
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .clickable {
              editableUrl = currentUrl
              showUrlDialog = true
            }
            .testTag("url_bar_address_pill"),
        shape = RoundedCornerShape(19.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = stringResource(R.string.url_bar_verified_host),
            tint = semantic.verified,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = displayPath,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      IconButton(
        onClick = onRefresh,
        modifier = Modifier.size(48.dp).testTag("url_bar_refresh"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Refresh,
          contentDescription = stringResource(R.string.url_bar_refresh),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      IconButton(
        onClick = onShare,
        modifier = Modifier.size(48.dp).testTag("url_bar_share"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Share,
          contentDescription = stringResource(R.string.url_bar_share),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    AnimatedVisibility(visible = isLoading) {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
      )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
  }

  if (showUrlDialog) {
    AlertDialog(
      onDismissRequest = { showUrlDialog = false },
      title = { Text("torrentclaw.com Address") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = editableUrl,
            onValueChange = { editableUrl = it },
            singleLine = true,
            label = { Text("Path or torrentclaw.com URL") },
            modifier = Modifier.fillMaxWidth().testTag("url_dialog_input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions =
              KeyboardActions(
                onGo = {
                  showUrlDialog = false
                  onNavigateUrl(editableUrl)
                }
              ),
          )
          Text(
            text = "External domains and popup redirects are blocked automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showUrlDialog = false
            onNavigateUrl(editableUrl)
          },
          modifier = Modifier.testTag("url_dialog_go"),
        ) {
          Text("Go")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(
            onClick = {
              onCopyUrl()
              showUrlDialog = false
            }
          ) {
            Text("Copy")
          }
          TextButton(onClick = { showUrlDialog = false }) {
            Text("Cancel")
          }
        }
      },
    )
  }
}

@Composable
fun WebsiteSiteHeader(
  page: ParsedWebsitePage?,
  hasSessionCookies: Boolean,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onSubmitSearch: () -> Unit,
  onNavigate: (String) -> Unit,
) {
  val semantic = LocalTorrentClawColors.current
  val isSignedIn = page?.isSignedIn == true || hasSessionCookies

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Website brand row + account pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .clickable { onNavigate(NavigationGuard.HOME_URL) }
              .padding(vertical = 4.dp, horizontal = 2.dp)
              .testTag("website_brand_home"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier =
              Modifier.size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
          )
          Text(
            text = page?.brandTitle ?: "TorrentClaw",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }

        val accountTarget =
          page?.accountLink?.url
            ?: if (isSignedIn) "${NavigationGuard.BASE_ORIGIN}/account" else "${NavigationGuard.BASE_ORIGIN}/login"
        val accountLabel =
          page?.accountLink?.label
            ?: if (isSignedIn) "Account" else "Sign In"

        Surface(
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .clickable { onNavigate(accountTarget) }
              .testTag("website_account_button"),
          shape = RoundedCornerShape(10.dp),
          color =
            if (isSignedIn) semantic.verified.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant,
          border =
            BorderStroke(
              1.dp,
              if (isSignedIn) semantic.verified.copy(alpha = 0.45f)
              else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Person,
              contentDescription = accountLabel,
              tint = if (isSignedIn) semantic.verified else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = accountLabel,
              style = MaterialTheme.typography.labelLarge,
              color = if (isSignedIn) semantic.verified else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      // Website nav links row
      val navLinks =
        page?.headerLinks?.filter { link ->
          link.url != page.accountLink?.url
        }.orEmpty()
      if (navLinks.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          navLinks.forEach { link ->
            Surface(
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .clickable { onNavigate(link.url) },
              shape = RoundedCornerShape(8.dp),
              color =
                if (link.isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
              border =
                BorderStroke(
                  1.dp,
                  if (link.isActive) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
              Text(
                text = link.label,
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (link.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              )
            }
          }
        }
      }

      // Embedded website search bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = {
            Text(
              text = stringResource(R.string.search_placeholder),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
          modifier = Modifier.weight(1f).testTag("website_search_input"),
        )
        Button(
          onClick = onSubmitSearch,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
          modifier = Modifier.testTag("website_search_button"),
        ) {
          Text("Search")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MagnetChooserSheet(
  target: MagnetTarget,
  installedClients: List<InstalledTorrentClient>,
  preferredPackage: String?,
  isDownloading: Boolean,
  onDismiss: () -> Unit,
  onOpenInClient: (clientPackage: String?, rememberDefault: Boolean) -> Unit,
  onCopyLink: () -> Unit,
  onDownloadTorrentFile: () -> Unit,
  onShareLink: () -> Unit,
) {
  var rememberDefault by remember(preferredPackage) { mutableStateOf(false) }
  val semantic = LocalTorrentClawColors.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = target.title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        target.resolution?.let { InfoPill(it.uppercase(), MaterialTheme.colorScheme.primary) }
        target.size?.let { InfoPill(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        target.seeders?.let { InfoPill("▲ $it seeders", semantic.seederHigh) }
        target.source?.let { InfoPill(it, MaterialTheme.colorScheme.secondary) }
      }

      if (!target.infoHash.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Info Hash: ${target.infoHash}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (installedClients.isNotEmpty() && !target.magnetUrl.isNullOrBlank()) {
        val preferredClient =
          installedClients.firstOrNull { it.packageName == preferredPackage }
            ?: installedClients.first()

        Button(
          onClick = { onOpenInClient(preferredClient.packageName, rememberDefault) },
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("magnet_open_app_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text =
              if (installedClients.size == 1 || preferredPackage != null) {
                "Open in ${preferredClient.label}"
              } else {
                stringResource(R.string.magnet_sheet_open)
              }
          )
        }

        if (installedClients.size > 1) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Choose installed torrent client",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              installedClients.forEach { client ->
                OutlinedButton(
                  onClick = { onOpenInClient(client.packageName, rememberDefault) },
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(client.label)
                }
              }
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { rememberDefault = !rememberDefault },
            ) {
              Checkbox(checked = rememberDefault, onCheckedChange = { rememberDefault = it })
              Text(
                text = "Remember selected torrent client",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else if (!target.magnetUrl.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "No torrent client app installed on this device. Copy or share the magnet link below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onCopyLink,
          modifier = Modifier.weight(1f).height(48.dp).testTag("magnet_copy_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.magnet_sheet_copy))
        }

        OutlinedButton(
          onClick = onShareLink,
          modifier = Modifier.weight(1f).height(48.dp).testTag("magnet_share_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.magnet_sheet_share))
        }
      }

      if (!target.torrentDownloadUrl.isNullOrBlank()) {
        OutlinedButton(
          onClick = onDownloadTorrentFile,
          enabled = !isDownloading,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("magnet_download_torrent_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Downloading .torrent…")
          } else {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.magnet_sheet_download_torrent))
          }
        }
      }
    }
  }
}

@Composable
private fun InfoPill(text: String, tint: androidx.compose.ui.graphics.Color) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = tint.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}
