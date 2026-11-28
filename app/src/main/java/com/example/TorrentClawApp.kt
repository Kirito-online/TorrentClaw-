package com.example

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.core.preferences.AppPreferences
import com.example.data.network.TorrentClawNetworkModule
import com.example.data.network.TorrentClawNetworkService
import com.example.data.repository.TorrentClawRepository
import com.example.util.NotificationHelper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
  val appContext: Context = context.applicationContext
  val preferences = AppPreferences(appContext)
  @Volatile var torrentClawApiKey: String? = null

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  init {
    scope.launch {
      preferences.preferencesFlow.collect { state ->
        torrentClawApiKey = state.torrentClawApiKey
      }
    }
  }

  val apiOkHttpClient = TorrentClawNetworkModule.createApiOkHttpClient { torrentClawApiKey }
  val imageOkHttpClient = TorrentClawNetworkModule.createImageOkHttpClient(apiOkHttpClient)
  val networkService = TorrentClawNetworkService(apiOkHttpClient)
  val repository = TorrentClawRepository(appContext, networkService)
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
    return TorrentClawNetworkModule.createImageLoader(
      context = this,
      imageOkHttpClient = container.imageOkHttpClient,
    )
  }
}
