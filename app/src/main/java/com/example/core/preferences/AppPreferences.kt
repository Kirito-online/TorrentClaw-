package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SortOption
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "torrentclaw_prefs")

enum class InterfaceDensity(val label: String) {
  COMFORTABLE("Comfortable"),
  COMPACT("Compact"),
}

enum class PosterSize(val label: String, val widthDp: Int) {
  SMALL("Small", 104),
  MEDIUM("Medium", 128),
  LARGE("Large", 156),
}

enum class ListingLayout(val label: String) {
  COMPACT("Compact"),
  EXPANDED("Expanded"),
}

enum class MagnetAction(val label: String) {
  ASK("Choose client each time"),
  OPEN_CLIENT("Open preferred client"),
  COPY("Copy magnet link"),
  SEND_DEBRID("Send to Debrid (if connected)"),
}

enum class TorrentFileAction(val label: String) {
  DOWNLOAD_AND_OPEN("Download & open in client"),
  SAVE_ONLY("Download to app cache"),
  SHARE("Download & share .torrent"),
}

data class UserPreferencesState(
  val hasCompletedOnboarding: Boolean = false,
  val themeMode: ThemeMode = ThemeMode.DARK,
  val accentPalette: AccentPalette = AccentPalette.ELECTRIC_CYAN,
  val density: InterfaceDensity = InterfaceDensity.COMFORTABLE,
  val posterSize: PosterSize = PosterSize.MEDIUM,
  val defaultListingLayout: ListingLayout = ListingLayout.COMPACT,
  val defaultSort: SortOption = SortOption.QUALITY_SCORE,
  val preferredResolutions: Set<String> = setOf("2160p", "1080p"),
  val preferredCodecs: Set<String> = setOf("HEVC", "AV1"),
  val preferTrueSpecVerified: Boolean = true,
  val autocompleteEnabled: Boolean = true,
  val saveSearchHistory: Boolean = true,
  val preferredTorrentClientPackage: String? = null,
  val preferredTorrentClientLabel: String? = null,
  val defaultMagnetAction: MagnetAction = MagnetAction.ASK,
  val defaultTorrentFileAction: TorrentFileAction = TorrentFileAction.DOWNLOAD_AND_OPEN,
  val notificationsEnabled: Boolean = true,
  val newReleaseNotifications: Boolean = true,
  val trueSpecNotifications: Boolean = true,
  val alertDefaultQuality: String = "1080p",
  val alertMinQualityScore: Int = 70,
  val preferredRegion: String = "US",
)

class AppPreferences(private val context: Context) {

  val preferencesFlow: Flow<UserPreferencesState> =
    context.dataStore.data.map { prefs ->
      UserPreferencesState(
        hasCompletedOnboarding = prefs[Keys.ONBOARDING_DONE] ?: false,
        themeMode =
          prefs[Keys.THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } }
            ?: ThemeMode.DARK,
        accentPalette =
          prefs[Keys.ACCENT_PALETTE]?.let { name -> AccentPalette.entries.find { it.name == name } }
            ?: AccentPalette.ELECTRIC_CYAN,
        density =
          prefs[Keys.DENSITY]?.let { name -> InterfaceDensity.entries.find { it.name == name } }
            ?: InterfaceDensity.COMFORTABLE,
        posterSize =
          prefs[Keys.POSTER_SIZE]?.let { name -> PosterSize.entries.find { it.name == name } }
            ?: PosterSize.MEDIUM,
        defaultListingLayout =
          prefs[Keys.LISTING_LAYOUT]?.let { name -> ListingLayout.entries.find { it.name == name } }
            ?: ListingLayout.COMPACT,
        defaultSort =
          prefs[Keys.DEFAULT_SORT]?.let { name -> SortOption.entries.find { it.name == name } }
            ?: SortOption.QUALITY_SCORE,
        preferredResolutions = prefs[Keys.PREFERRED_RESOLUTIONS] ?: setOf("2160p", "1080p"),
        preferredCodecs = prefs[Keys.PREFERRED_CODECS] ?: setOf("HEVC", "AV1"),
        preferTrueSpecVerified = prefs[Keys.PREFER_TRUESPEC] ?: true,
        autocompleteEnabled = prefs[Keys.AUTOCOMPLETE_ENABLED] ?: true,
        saveSearchHistory = prefs[Keys.SAVE_SEARCH_HISTORY] ?: true,
        preferredTorrentClientPackage = prefs[Keys.TORRENT_CLIENT_PKG],
        preferredTorrentClientLabel = prefs[Keys.TORRENT_CLIENT_LABEL],
        defaultMagnetAction =
          prefs[Keys.MAGNET_ACTION]?.let { name -> MagnetAction.entries.find { it.name == name } }
            ?: MagnetAction.ASK,
        defaultTorrentFileAction =
          prefs[Keys.TORRENT_FILE_ACTION]?.let { name ->
            TorrentFileAction.entries.find { it.name == name }
          } ?: TorrentFileAction.DOWNLOAD_AND_OPEN,
        notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
        newReleaseNotifications = prefs[Keys.NEW_RELEASE_NOTIFICATIONS] ?: true,
        trueSpecNotifications = prefs[Keys.TRUESPEC_NOTIFICATIONS] ?: true,
        alertDefaultQuality = prefs[Keys.ALERT_DEFAULT_QUALITY] ?: "1080p",
        alertMinQualityScore = prefs[Keys.ALERT_MIN_SCORE]?.toIntOrNull() ?: 70,
        preferredRegion = prefs[Keys.PREFERRED_REGION] ?: "US",
      )
    }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    context.dataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setAccentPalette(palette: AccentPalette) {
    context.dataStore.edit { it[Keys.ACCENT_PALETTE] = palette.name }
  }

  suspend fun setDensity(density: InterfaceDensity) {
    context.dataStore.edit { it[Keys.DENSITY] = density.name }
  }

  suspend fun setPosterSize(size: PosterSize) {
    context.dataStore.edit { it[Keys.POSTER_SIZE] = size.name }
  }

  suspend fun setDefaultListingLayout(layout: ListingLayout) {
    context.dataStore.edit { it[Keys.LISTING_LAYOUT] = layout.name }
  }

  suspend fun setDefaultSort(sort: SortOption) {
    context.dataStore.edit { it[Keys.DEFAULT_SORT] = sort.name }
  }

  suspend fun setPreferredResolutions(resolutions: Set<String>) {
    context.dataStore.edit { it[Keys.PREFERRED_RESOLUTIONS] = resolutions }
  }

  suspend fun setPreferredCodecs(codecs: Set<String>) {
    context.dataStore.edit { it[Keys.PREFERRED_CODECS] = codecs }
  }

  suspend fun setPreferTrueSpecVerified(enabled: Boolean) {
    context.dataStore.edit { it[Keys.PREFER_TRUESPEC] = enabled }
  }

  suspend fun setAutocompleteEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTOCOMPLETE_ENABLED] = enabled }
  }

  suspend fun setSaveSearchHistory(enabled: Boolean) {
    context.dataStore.edit { it[Keys.SAVE_SEARCH_HISTORY] = enabled }
  }

  suspend fun setPreferredTorrentClient(packageName: String?, label: String?) {
    context.dataStore.edit { prefs ->
      if (packageName == null) {
        prefs.remove(Keys.TORRENT_CLIENT_PKG)
        prefs.remove(Keys.TORRENT_CLIENT_LABEL)
      } else {
        prefs[Keys.TORRENT_CLIENT_PKG] = packageName
        prefs[Keys.TORRENT_CLIENT_LABEL] = label ?: packageName
      }
    }
  }

  suspend fun setDefaultMagnetAction(action: MagnetAction) {
    context.dataStore.edit { it[Keys.MAGNET_ACTION] = action.name }
  }

  suspend fun setDefaultTorrentFileAction(action: TorrentFileAction) {
    context.dataStore.edit { it[Keys.TORRENT_FILE_ACTION] = action.name }
  }

  suspend fun setNotificationsEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
  }

  suspend fun setNewReleaseNotifications(enabled: Boolean) {
    context.dataStore.edit { it[Keys.NEW_RELEASE_NOTIFICATIONS] = enabled }
  }

  suspend fun setTrueSpecNotifications(enabled: Boolean) {
    context.dataStore.edit { it[Keys.TRUESPEC_NOTIFICATIONS] = enabled }
  }

  suspend fun setAlertDefaultQuality(quality: String) {
    context.dataStore.edit { it[Keys.ALERT_DEFAULT_QUALITY] = quality }
  }

  suspend fun setAlertMinQualityScore(score: Int) {
    context.dataStore.edit { it[Keys.ALERT_MIN_SCORE] = score.toString() }
  }

  suspend fun setPreferredRegion(region: String) {
    context.dataStore.edit { it[Keys.PREFERRED_REGION] = region }
  }

  private object Keys {
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
    val DENSITY = stringPreferencesKey("density")
    val POSTER_SIZE = stringPreferencesKey("poster_size")
    val LISTING_LAYOUT = stringPreferencesKey("listing_layout")
    val DEFAULT_SORT = stringPreferencesKey("default_sort")
    val PREFERRED_RESOLUTIONS = stringSetPreferencesKey("preferred_resolutions")
    val PREFERRED_CODECS = stringSetPreferencesKey("preferred_codecs")
    val PREFER_TRUESPEC = booleanPreferencesKey("prefer_truespec")
    val AUTOCOMPLETE_ENABLED = booleanPreferencesKey("autocomplete_enabled")
    val SAVE_SEARCH_HISTORY = booleanPreferencesKey("save_search_history")
    val TORRENT_CLIENT_PKG = stringPreferencesKey("torrent_client_pkg")
    val TORRENT_CLIENT_LABEL = stringPreferencesKey("torrent_client_label")
    val MAGNET_ACTION = stringPreferencesKey("magnet_action")
    val TORRENT_FILE_ACTION = stringPreferencesKey("torrent_file_action")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val NEW_RELEASE_NOTIFICATIONS = booleanPreferencesKey("new_release_notifications")
    val TRUESPEC_NOTIFICATIONS = booleanPreferencesKey("truespec_notifications")
    val ALERT_DEFAULT_QUALITY = stringPreferencesKey("alert_default_quality")
    val ALERT_MIN_SCORE = stringPreferencesKey("alert_min_score")
    val PREFERRED_REGION = stringPreferencesKey("preferred_region")
  }
}
