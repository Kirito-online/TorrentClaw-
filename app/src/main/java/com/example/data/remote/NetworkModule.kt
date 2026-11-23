package com.example.data.remote

import android.content.Context
import com.example.core.security.KeystoreCredentialVault
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit

data class RateLimitSnapshot(
  val limit: Int? = null,
  val remaining: Int? = null,
  val retryAfterSeconds: Int? = null,
)

class NetworkModule(context: Context, private val vault: KeystoreCredentialVault) {

  private val _rateLimitState = MutableStateFlow(RateLimitSnapshot())
  val rateLimitState: StateFlow<RateLimitSnapshot> = _rateLimitState.asStateFlow()

  private val cacheDir = File(context.cacheDir, "http_cache")
  private val httpCache = Cache(cacheDir, 25L * 1024L * 1024L)

  private val authAndRateLimitInterceptor = Interceptor { chain ->
    val original = chain.request()
    val builder =
      original
        .newBuilder()
        .header("Accept", "application/json")
        .header("User-Agent", "TorrentClaw-Android/1.0")

    val apiKey = vault.getSecret(KeystoreCredentialVault.KEY_API_KEY)
    val sessionToken = vault.getSecret(KeystoreCredentialVault.KEY_SESSION_TOKEN)
    if (!apiKey.isNullOrBlank()) {
      builder.header("X-API-Key", apiKey)
      builder.header("Authorization", "Bearer $apiKey")
    } else if (!sessionToken.isNullOrBlank()) {
      builder.header("Authorization", "Bearer $sessionToken")
    }

    var response: Response = chain.proceed(builder.build())
    // Exponential backoff retry for transient 5xx or 429 on idempotent GET calls
    if (original.method == "GET" && (response.code == 429 || response.code in 500..599)) {
      val retryAfterHeader = response.header("Retry-After")?.toIntOrNull()
      if (response.code == 429) {
        _rateLimitState.value =
          _rateLimitState.value.copy(
            remaining = 0,
            retryAfterSeconds = retryAfterHeader ?: 30,
          )
      }
      if (response.code in 500..599) {
        response.close()
        Thread.sleep(600L)
        response = chain.proceed(builder.build())
      }
    }

    val limit = response.header("X-RateLimit-Limit")?.toIntOrNull()
    val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
    if (limit != null || remaining != null) {
      _rateLimitState.value =
        RateLimitSnapshot(
          limit = limit ?: _rateLimitState.value.limit,
          remaining = remaining ?: _rateLimitState.value.remaining,
          retryAfterSeconds = if ((remaining ?: 1) > 0) null else _rateLimitState.value.retryAfterSeconds,
        )
    }

    response
  }

  val okHttpClient: OkHttpClient =
    OkHttpClient.Builder()
      .cache(httpCache)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .addInterceptor(authAndRateLimitInterceptor)
      .build()

  val torrentClawApi: TorrentClawApi =
    Retrofit.Builder()
      .baseUrl("https://torrentclaw.com/api/v1/")
      .client(okHttpClient)
      .build()
      .create(TorrentClawApi::class.java)
}
