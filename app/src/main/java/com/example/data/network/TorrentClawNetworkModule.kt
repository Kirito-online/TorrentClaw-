package com.example.data.network

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Provides configured OkHttpClients, Retrofit instances, and Coil ImageLoader
 * for TorrentClaw API calls and cover artwork loading.
 */
object TorrentClawNetworkModule {

  const val PRIMARY_BASE_URL = "https://torrentclaw.com/api/v1/"
  const val MIRROR_BASE_URL = "https://torrentclaw.to/api/v1/"

  private const val API_USER_AGENT = "TorrentClaw-Android/1.0"
  private const val IMAGE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

  fun createApiOkHttpClient(apiKeyProvider: () -> String? = { null }): OkHttpClient {
    val apiHeadersInterceptor = Interceptor { chain ->
      val original = chain.request()
      val builder = original.newBuilder()
      if (original.header("Accept") == null) {
        builder.header("Accept", "application/json")
      }
      if (original.header("User-Agent") == null) {
        builder.header("User-Agent", API_USER_AGENT)
      }
      val key = apiKeyProvider()?.trim()?.takeIf { it.isNotBlank() }
      if (key != null && original.header("Authorization") == null) {
        builder.header("Authorization", "Bearer $key")
      }
      chain.proceed(builder.build())
    }

    return OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .callTimeout(25, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .retryOnConnectionFailure(true)
      .addInterceptor(apiHeadersInterceptor)
      .build()
  }

  /**
   * Builds a dedicated OkHttpClient for Coil image loading, sharing the underlying
   * connection pool and dispatcher while sending image-compatible Accept headers.
   */
  fun createImageOkHttpClient(apiClient: OkHttpClient): OkHttpClient {
    val imageHeadersInterceptor = Interceptor { chain ->
      val request =
        chain
          .request()
          .newBuilder()
          .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*;q=0.8")
          .header("User-Agent", IMAGE_USER_AGENT)
          .build()
      chain.proceed(request)
    }

    val builder = apiClient.newBuilder()
    builder.interceptors().clear()
    return builder
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .retryOnConnectionFailure(true)
      .addInterceptor(imageHeadersInterceptor)
      .build()
  }

  fun createRetrofit(baseUrl: String, okHttpClient: OkHttpClient): Retrofit {
    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .build()
  }

  fun createApiService(
    baseUrl: String = PRIMARY_BASE_URL,
    okHttpClient: OkHttpClient = createApiOkHttpClient(),
  ): TorrentClawApiService {
    return createRetrofit(baseUrl, okHttpClient).create(TorrentClawApiService::class.java)
  }

  fun createImageLoader(context: Context, imageOkHttpClient: OkHttpClient): ImageLoader {
    return ImageLoader.Builder(context)
      .okHttpClient { imageOkHttpClient }
      .respectCacheHeaders(false)
      .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.22).build() }
      .diskCache {
        DiskCache.Builder()
          .directory(context.cacheDir.resolve("coil_poster_cache"))
          .maxSizeBytes(120L * 1024L * 1024L)
          .build()
      }
      .crossfade(true)
      .build()
  }
}
