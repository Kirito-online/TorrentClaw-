package com.example

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.core.preferences.AppPreferences
import com.example.core.security.KeystoreCredentialVault
import com.example.core.security.PersistentCookieJar
import com.example.data.web.OffscreenDomExtractor
import com.example.data.web.WebsiteHttpClient
import com.example.data.web.WebsitePageRepository
import com.example.util.NotificationHelper

class AppContainer(context: Context) {
  val appContext: Context = context.applicationContext
  val vault = KeystoreCredentialVault(appContext)
  val preferences = AppPreferences(appContext)
  val cookieJar = PersistentCookieJar(vault)
  val httpClient = WebsiteHttpClient(cookieJar)
  val domExtractor = OffscreenDomExtractor(appContext, cookieJar)
  val websitePageRepository = WebsitePageRepository(httpClient, domExtractor)
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
