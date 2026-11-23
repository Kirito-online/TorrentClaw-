package com.example

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.core.preferences.AppPreferences
import com.example.core.security.KeystoreCredentialVault
import com.example.data.local.TorrentClawDatabase
import com.example.data.remote.DebridService
import com.example.data.remote.NetworkModule
import com.example.data.repository.AlertRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.DebridRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.LibraryRepository
import com.example.data.repository.MediaRepository
import com.example.util.NotificationHelper

class AppContainer(context: Context) {
  val appContext: Context = context.applicationContext
  val vault = KeystoreCredentialVault(appContext)
  val preferences = AppPreferences(appContext)
  val database = TorrentClawDatabase.getInstance(appContext)
  val networkModule = NetworkModule(appContext, vault)
  val debridService = DebridService()
  val authRepository = AuthRepository(vault, preferences, networkModule, debridService)
  val mediaRepository =
    MediaRepository(
      appContext,
      networkModule,
      database.cachedDiscoveryDao(),
      authRepository,
      debridService,
    )
  val libraryRepository =
    LibraryRepository(database.savedMediaDao(), database.savedTorrentDao())
  val historyRepository =
    HistoryRepository(database.searchHistoryDao(), database.downloadHistoryDao())
  val alertRepository =
    AlertRepository(appContext, database.contentAlertDao(), mediaRepository, authRepository)
  val debridRepository = DebridRepository(authRepository, debridService)
}

class TorrentClawApp : Application(), ImageLoaderFactory {

  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    NotificationHelper.ensureChannels(this)
  }

  override fun newImageLoader(): ImageLoader {
    return ImageLoader.Builder(this)
      .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.22).build() }
      .diskCache {
        DiskCache.Builder()
          .directory(cacheDir.resolve("coil_poster_cache"))
          .maxSizeBytes(120L * 1024L * 1024L)
          .build()
      }
      .crossfade(true)
      .build()
  }
}
