package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.preferences.InterfaceDensity
import com.example.core.preferences.ListingLayout
import com.example.core.preferences.MagnetAction
import com.example.core.preferences.PosterSize
import com.example.core.preferences.TorrentFileAction
import com.example.core.preferences.UserPreferencesState
import com.example.data.model.AccountTier
import com.example.data.model.DebridAccount
import com.example.data.model.DebridProvider
import com.example.data.model.SortOption
import com.example.data.model.UserAccount
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.LocalTorrentClawColors
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SettingsViewModel
import com.example.util.TorrentIntentHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  prefs: UserPreferencesState,
  account: UserAccount,
  debridAccounts: List<DebridAccount>,
  settingsViewModel: SettingsViewModel,
  installedClientsCount: Int,
  cacheSizeLabel: String,
  debridStatusMessage: String?,
  onSignInOrSwitch: () -> Unit,
  onLogout: () -> Unit,
) {
  val context = LocalContext.current
  val semantic = LocalTorrentClawColors.current
  var connectProvider by remember { mutableStateOf<DebridProvider?>(null) }
  var showLicenses by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(bottom = 32.dp),
  ) {
    item {
      Text(
        text = "Settings",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      )
    }

    // 1. ACCOUNT & PRO / DEBRID
    item {
      SettingsGroupHeader("Account & Integrations")
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(account.username, style = MaterialTheme.typography.titleMedium)
              Text(account.subscriptionStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              account.apiKeyMasked?.let {
                Text("Key: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSignInOrSwitch, modifier = Modifier.weight(1f).testTag("settings_switch_account")) {
              Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(if (account.isSignedIn) "Switch Account" else "Sign In")
            }
            if (account.isSignedIn) {
              OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f).testTag("settings_logout")) {
                Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Logout")
              }
            }
          }
        }
      }
      Spacer(Modifier.height(10.dp))

      // Connected Debrid Services
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Debrid Providers (Encrypted in Android Keystore)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        debridStatusMessage?.let { msg ->
          Text(msg, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        debridAccounts.forEach { acc ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.Bolt,
              contentDescription = null,
              tint = if (acc.isConnected) semantic.debridCached else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(acc.provider.displayName, style = MaterialTheme.typography.titleSmall)
              Text(
                text =
                  if (acc.isConnected) "${acc.username ?: "Connected"} • ${acc.planLabel ?: "Active"}"
                  else "Not connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (acc.isConnected) {
              TextButton(onClick = { settingsViewModel.disconnectDebrid(acc.provider) }) {
                Text("Disconnect")
              }
            } else {
              OutlinedButton(onClick = { connectProvider = acc.provider }) {
                Text("Connect")
              }
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }

        // Pro entitlements only displayed for Pro tier
        if (account.tier == AccountTier.PRO) {
          Spacer(Modifier.height(8.dp))
          Surface(
            color = semantic.verified.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text("TorrentClaw Pro Features Active", style = MaterialTheme.typography.titleSmall, color = semantic.verified)
              Text(
                "Priority TrueSpec • Torznab & Stremio Addon Sync • 1,000 req/min",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    }

    // 2. APPEARANCE
    item {
      SettingsGroupHeader("Appearance")
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Theme", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ThemeMode.entries.forEach { mode ->
            FilterChip(
              selected = prefs.themeMode == mode,
              onClick = { settingsViewModel.setThemeMode(mode) },
              label = { Text(mode.label) },
              modifier = Modifier.testTag("theme_${mode.name}"),
            )
          }
        }

        Spacer(Modifier.height(10.dp))
        Text("Accent Palette", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AccentPalette.entries.forEach { palette ->
            FilterChip(
              selected = prefs.accentPalette == palette,
              onClick = { settingsViewModel.setAccentPalette(palette) },
              leadingIcon = {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(palette.darkPrimary))
              },
              label = { Text(palette.label) },
            )
          }
        }

        Spacer(Modifier.height(10.dp))
        Text("Interface Density & Poster Size", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          InterfaceDensity.entries.forEach { d ->
            FilterChip(
              selected = prefs.density == d,
              onClick = { settingsViewModel.setDensity(d) },
              label = { Text(d.label) },
            )
          }
          PosterSize.entries.forEach { size ->
            FilterChip(
              selected = prefs.posterSize == size,
              onClick = { settingsViewModel.setPosterSize(size) },
              label = { Text("${size.label} Poster") },
            )
          }
        }

        Spacer(Modifier.height(10.dp))
        Text("Default Release Listing Layout", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ListingLayout.entries.forEach { layout ->
            FilterChip(
              selected = prefs.defaultListingLayout == layout,
              onClick = { settingsViewModel.setListingLayout(layout) },
              label = { Text(layout.label) },
            )
          }
        }
      }
    }

    // 3. SEARCH
    item {
      SettingsGroupHeader("Search Preferences")
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Default Sorting", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SortOption.entries.forEach { sort ->
            FilterChip(
              selected = prefs.defaultSort == sort,
              onClick = { settingsViewModel.setDefaultSort(sort) },
              label = { Text(sort.label) },
            )
          }
        }

        Spacer(Modifier.height(10.dp))
        Text("Preferred Resolutions (Smart Recommendations)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("2160p", "1080p", "720p", "480p").forEach { res ->
            FilterChip(
              selected = res in prefs.preferredResolutions,
              onClick = { settingsViewModel.togglePreferredResolution(res, prefs.preferredResolutions) },
              label = { Text(res) },
            )
          }
        }

        SettingsToggleRow(
          title = "Autocomplete Suggestions",
          subtitle = "Show instant title suggestions while typing",
          checked = prefs.autocompleteEnabled,
          onCheckedChange = settingsViewModel::setAutocompleteEnabled,
        )
        SettingsToggleRow(
          title = "Save Search History",
          subtitle = "Keep recent searches locally on device",
          checked = prefs.saveSearchHistory,
          onCheckedChange = settingsViewModel::setSaveSearchHistory,
        )
      }
    }

    // 4. TORRENT HANDLING
    item {
      SettingsGroupHeader("Torrent Handling")
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
          text = "Preferred Torrent Client ($installedClientsCount detected on device)",
          style = MaterialTheme.typography.labelLarge,
        )
        val clients = settingsViewModel.installedClients.value
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = prefs.preferredTorrentClientPackage == null,
            onClick = { settingsViewModel.setPreferredClient(null) },
            label = { Text("Ask / System Default") },
          )
          clients.forEach { client ->
            FilterChip(
              selected = prefs.preferredTorrentClientPackage == client.packageName,
              onClick = { settingsViewModel.setPreferredClient(client) },
              label = { Text(client.label) },
            )
          }
        }

        Spacer(Modifier.height(8.dp))
        Text("Default Magnet Action", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MagnetAction.entries.forEach { action ->
            FilterChip(
              selected = prefs.defaultMagnetAction == action,
              onClick = { settingsViewModel.setDefaultMagnetAction(action) },
              label = { Text(action.label) },
            )
          }
        }

        Spacer(Modifier.height(8.dp))
        Text("Default .torrent File Handling", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TorrentFileAction.entries.forEach { action ->
            FilterChip(
              selected = prefs.defaultTorrentFileAction == action,
              onClick = { settingsViewModel.setDefaultTorrentFileAction(action) },
              label = { Text(action.label) },
            )
          }
        }
      }
    }

    // 5. NOTIFICATIONS
    item {
      SettingsGroupHeader("Notifications")
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SettingsToggleRow(
          title = "Enable Notifications",
          subtitle = "Master switch for TorrentClaw alert channels",
          checked = prefs.notificationsEnabled,
          onCheckedChange = settingsViewModel::setNotificationsEnabled,
        )
        SettingsToggleRow(
          title = "New Release Alerts",
          subtitle = "Notify when followed titles match quality rules",
          checked = prefs.newReleaseNotifications,
          onCheckedChange = settingsViewModel::setNewReleaseNotifications,
        )
        SettingsToggleRow(
          title = "TrueSpec Scan Updates",
          subtitle = "Notify when requested swarm verification completes",
          checked = prefs.trueSpecNotifications,
          onCheckedChange = settingsViewModel::setTrueSpecNotifications,
        )
      }
    }

    // 6. STORAGE
    item {
      SettingsGroupHeader("Storage")
      Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cached files & posters: $cacheSizeLabel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = settingsViewModel::clearImageCache, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Image Cache")
          }
          OutlinedButton(onClick = settingsViewModel::clearTempFiles, modifier = Modifier.weight(1f)) {
            Text("Temp & HTTP")
          }
        }
        TextButton(onClick = settingsViewModel::clearLocalData) {
          Text("Reset All Local Library & History Data", color = MaterialTheme.colorScheme.error)
        }
      }
    }

    // 7. ABOUT
    item {
      SettingsGroupHeader("About")
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("TorrentClaw Android v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
        Text("Official API: https://torrentclaw.com/api/v1/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { TorrentIntentHelper.openBrowserUrl(context, "https://torrentclaw.com") }) {
            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Website")
          }
          OutlinedButton(onClick = { TorrentIntentHelper.openBrowserUrl(context, "https://torrentclaw.com/api/docs") }) {
            Text("API Docs")
          }
          OutlinedButton(onClick = { TorrentIntentHelper.openBrowserUrl(context, "https://torrentclaw.com/privacy") }) {
            Text("Privacy Policy")
          }
          OutlinedButton(onClick = { showLicenses = true }) {
            Text("Open Source")
          }
        }
      }
    }
  }

  connectProvider?.let { provider ->
    var tokenInput by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { connectProvider = null },
      title = { Text("Connect ${provider.displayName}") },
      text = {
        Column {
          Text(
            "Enter your official ${provider.displayName} API token. It is verified directly with ${provider.displayName} and encrypted in Android Keystore.",
            style = MaterialTheme.typography.bodySmall,
          )
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("API Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            settingsViewModel.connectDebrid(provider, tokenInput)
            connectProvider = null
          },
          enabled = tokenInput.isNotBlank(),
        ) {
          Text("Verify & Connect")
        }
      },
      dismissButton = {
        TextButton(onClick = { connectProvider = null }) { Text("Cancel") }
      },
    )
  }

  if (showLicenses) {
    AlertDialog(
      onDismissRequest = { showLicenses = false },
      title = { Text("Open-Source Licenses") },
      text = {
        Text(
          "Built with Jetpack Compose, Material 3, AndroidX Room, DataStore, Paging 3, Retrofit, OkHttp, Moshi, Kotlinx Serialization, and Coil.",
          style = MaterialTheme.typography.bodySmall,
        )
      },
      confirmButton = {
        TextButton(onClick = { showLicenses = false }) { Text("Close") }
      },
    )
  }
}

@Composable
private fun SettingsGroupHeader(title: String) {
  Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(Modifier.height(12.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
