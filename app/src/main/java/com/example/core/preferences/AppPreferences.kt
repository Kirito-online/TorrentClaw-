package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FavoriteMediaEntry
import com.example.data.model.MediaItem
import com.example.data.model.isSameFavoriteMedia
import com.example.data.model.toFavoriteEntry
import com.example.data.model.toMediaItem
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "torrentclaw_prefs")

data class UserPreferencesState(
  val themeMode: ThemeMode = ThemeMode.DARK,
  val accentPalette: AccentPalette = AccentPalette.ELECTRIC_CYAN,
  val preferredTorrentClientPackage: String? = null,
  val preferredTorrentClientLabel: String? = null,
  val preferredResolutions: Set<String> = setOf("2160p", "1080p"),
  val preferredCodecs: Set<String> = setOf("HEVC", "AV1"),
  val preferTrueSpecVerified: Boolean = true,
  val recentSearches: List<String> = emptyList(),
  val torrentClawApiKey: String? = null,
  val favoriteItems: List<MediaItem> = emptyList(),
) {
  fun isFavorite(item: MediaItem): Boolean = favoriteItems.any { isSameFavoriteMedia(it, item) }
}

class AppPreferences(private val context: Context) {

  private val json = Json { ignoreUnknownKeys = true }

  val preferencesFlow: Flow<UserPreferencesState> =
    context.dataStore.data.map { prefs ->
      val recent =
        prefs[Keys.RECENT_SEARCHES]?.let { raw ->
          runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()

      val favorites =
        prefs[Keys.FAVORITES]?.let { raw ->
          runCatching { json.decodeFromString<List<FavoriteMediaEntry>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.savedAtMs }
            .map { it.toMediaItem() }
        } ?: emptyList()

      UserPreferencesState(
        themeMode =
          prefs[Keys.THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } }
            ?: ThemeMode.DARK,
        accentPalette =
          prefs[Keys.ACCENT_PALETTE]?.let { name -> AccentPalette.entries.find { it.name == name } }
            ?: AccentPalette.ELECTRIC_CYAN,
        preferredTorrentClientPackage = prefs[Keys.TORRENT_CLIENT_PKG],
        preferredTorrentClientLabel = prefs[Keys.TORRENT_CLIENT_LABEL],
        preferredResolutions = prefs[Keys.PREFERRED_RESOLUTIONS] ?: setOf("2160p", "1080p"),
        preferredCodecs = prefs[Keys.PREFERRED_CODECS] ?: setOf("HEVC", "AV1"),
        preferTrueSpecVerified = prefs[Keys.PREFER_TRUESPEC] ?: true,
        recentSearches = recent,
        torrentClawApiKey = prefs[Keys.TORRENTCLAW_API_KEY]?.takeIf { it.isNotBlank() },
        favoriteItems = favorites,
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

  suspend fun addRecentSearch(query: String) {
    val clean = query.trim()
    if (clean.isBlank()) return
    context.dataStore.edit { prefs ->
      val current =
        prefs[Keys.RECENT_SEARCHES]?.let { raw ->
          runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
      val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(12)
      prefs[Keys.RECENT_SEARCHES] = json.encodeToString(updated)
    }
  }

  suspend fun removeRecentSearch(query: String) {
    context.dataStore.edit { prefs ->
      val current =
        prefs[Keys.RECENT_SEARCHES]?.let { raw ->
          runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
      prefs[Keys.RECENT_SEARCHES] = json.encodeToString(current.filterNot { it.equals(query, true) })
    }
  }

  suspend fun clearRecentSearches() {
    context.dataStore.edit { it.remove(Keys.RECENT_SEARCHES) }
  }

  suspend fun setTorrentClawApiKey(apiKey: String?) {
    val clean = apiKey?.trim()?.takeIf { it.isNotBlank() }
    context.dataStore.edit { prefs ->
      if (clean == null) prefs.remove(Keys.TORRENTCLAW_API_KEY)
      else prefs[Keys.TORRENTCLAW_API_KEY] = clean
    }
  }

  suspend fun toggleFavorite(item: MediaItem): Boolean {
    var nowFavorited = false
    context.dataStore.edit { prefs ->
      val current =
        prefs[Keys.FAVORITES]?.let { raw ->
          runCatching { json.decodeFromString<List<FavoriteMediaEntry>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
      val existingIndex = current.indexOfFirst { isSameFavoriteMedia(it.toMediaItem(), item) }
      val updated =
        if (existingIndex >= 0) {
          nowFavorited = false
          current.filterIndexed { idx, _ -> idx != existingIndex }
        } else {
          nowFavorited = true
          listOf(item.toFavoriteEntry()) + current
        }
      prefs[Keys.FAVORITES] = json.encodeToString(updated)
    }
    return nowFavorited
  }

  suspend fun clearFavorites() {
    context.dataStore.edit { it.remove(Keys.FAVORITES) }
  }

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
    val TORRENT_CLIENT_PKG = stringPreferencesKey("torrent_client_pkg")
    val TORRENT_CLIENT_LABEL = stringPreferencesKey("torrent_client_label")
    val PREFERRED_RESOLUTIONS = stringSetPreferencesKey("preferred_resolutions")
    val PREFERRED_CODECS = stringSetPreferencesKey("preferred_codecs")
    val PREFER_TRUESPEC = booleanPreferencesKey("prefer_truespec")
    val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    val TORRENTCLAW_API_KEY = stringPreferencesKey("torrentclaw_api_key")
    val FAVORITES = stringPreferencesKey("favorite_media_entries")
  }
}
