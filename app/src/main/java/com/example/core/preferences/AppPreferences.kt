package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "torrentclaw_prefs")

data class UserPreferencesState(
  val themeMode: ThemeMode = ThemeMode.DARK,
  val accentPalette: AccentPalette = AccentPalette.ELECTRIC_CYAN,
  val preferredTorrentClientPackage: String? = null,
  val preferredTorrentClientLabel: String? = null,
)

class AppPreferences(private val context: Context) {

  val preferencesFlow: Flow<UserPreferencesState> =
    context.dataStore.data.map { prefs ->
      UserPreferencesState(
        themeMode =
          prefs[Keys.THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } }
            ?: ThemeMode.DARK,
        accentPalette =
          prefs[Keys.ACCENT_PALETTE]?.let { name -> AccentPalette.entries.find { it.name == name } }
            ?: AccentPalette.ELECTRIC_CYAN,
        preferredTorrentClientPackage = prefs[Keys.TORRENT_CLIENT_PKG],
        preferredTorrentClientLabel = prefs[Keys.TORRENT_CLIENT_LABEL],
      )
    }

  suspend fun setPreferredTorrentClient(packageName: String?, label: String?) {
    context.dataStore.edit { prefs ->
      if (packageName.isNullOrBlank()) {
        prefs.remove(Keys.TORRENT_CLIENT_PKG)
        prefs.remove(Keys.TORRENT_CLIENT_LABEL)
      } else {
        prefs[Keys.TORRENT_CLIENT_PKG] = packageName
        prefs[Keys.TORRENT_CLIENT_LABEL] = label ?: packageName
      }
    }
  }

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
    val TORRENT_CLIENT_PKG = stringPreferencesKey("torrent_client_pkg")
    val TORRENT_CLIENT_LABEL = stringPreferencesKey("torrent_client_label")
  }
}
