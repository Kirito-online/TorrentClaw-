package com.example.data.remote

import com.example.data.model.DebridAccount
import com.example.data.model.DebridProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Official REST client for user-configured Debrid providers (Real-Debrid, TorBox, AllDebrid, Premiumize).
 * Never exposes or logs tokens.
 */
class DebridService {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .build()

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  suspend fun verifyProviderToken(
    provider: DebridProvider,
    token: String,
  ): Result<DebridAccount> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanToken = token.trim()
        require(cleanToken.isNotBlank()) { "API token cannot be empty." }
        val request =
          when (provider) {
            DebridProvider.REAL_DEBRID ->
              Request.Builder()
                .url("${provider.baseUrl}user")
                .header("Authorization", "Bearer $cleanToken")
                .get()
                .build()
            DebridProvider.TORBOX ->
              Request.Builder()
                .url("${provider.baseUrl}user/me")
                .header("Authorization", "Bearer $cleanToken")
                .get()
                .build()
            DebridProvider.ALLDEBRID ->
              Request.Builder()
                .url("${provider.baseUrl}user?agent=TorrentClawAndroid&apikey=$cleanToken")
                .get()
                .build()
            DebridProvider.PREMIUMIZE ->
              Request.Builder()
                .url("${provider.baseUrl}account/info?apikey=$cleanToken")
                .get()
                .build()
          }
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            throw IllegalStateException("${provider.displayName} rejected token (HTTP ${response.code}).")
          }
          val body = response.body?.string().orEmpty()
          val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
          val data = (root?.get("data") as? JsonObject)?.let { it["user"] as? JsonObject ?: it } ?: root
          val username =
            (data?.get("username") as? JsonPrimitive)?.contentOrNull
              ?: (data?.get("email") as? JsonPrimitive)?.contentOrNull
              ?: (data?.get("customer_id") as? JsonPrimitive)?.contentOrNull
              ?: "Connected"
          val expiration =
            (data?.get("expiration") as? JsonPrimitive)?.contentOrNull
              ?: (data?.get("premium_until") as? JsonPrimitive)?.contentOrNull
              ?: (data?.get("premium_expires_at") as? JsonPrimitive)?.contentOrNull
          val plan =
            (data?.get("type") as? JsonPrimitive)?.contentOrNull
              ?: if ((data?.get("is_subscribed") as? JsonPrimitive)?.booleanOrNull == true) "Premium" else "Active"
          DebridAccount(
            provider = provider,
            isConnected = true,
            username = username,
            planLabel = plan,
            expiresAt = expiration,
          )
        }
      }
    }

  suspend fun checkInstantCache(
    provider: DebridProvider,
    token: String,
    infoHash: String,
  ): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        val hash = infoHash.lowercase()
        val request =
          when (provider) {
            DebridProvider.REAL_DEBRID ->
              Request.Builder()
                .url("${provider.baseUrl}torrents/instantAvailability/$hash")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            DebridProvider.TORBOX ->
              Request.Builder()
                .url("${provider.baseUrl}torrents/checkcached?hash=$hash&format=object")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            DebridProvider.ALLDEBRID ->
              Request.Builder()
                .url("${provider.baseUrl}magnet/instant?agent=TorrentClawAndroid&apikey=$token&magnets[]=$hash")
                .get()
                .build()
            DebridProvider.PREMIUMIZE ->
              Request.Builder()
                .url("${provider.baseUrl}cache/check?apikey=$token&items[]=$hash")
                .get()
                .build()
          }
        client.newCall(request).execute().use { resp ->
          if (!resp.isSuccessful) return@runCatching false
          val text = resp.body?.string().orEmpty()
          text.contains(hash, ignoreCase = true) && !text.contains("\"cached\":false")
        }
      }.getOrDefault(false)
    }

  suspend fun sendMagnetToDebrid(
    provider: DebridProvider,
    token: String,
    magnetUri: String,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val request =
          when (provider) {
            DebridProvider.REAL_DEBRID ->
              Request.Builder()
                .url("${provider.baseUrl}torrents/addMagnet")
                .header("Authorization", "Bearer $token")
                .post(FormBody.Builder().add("magnet", magnetUri).build())
                .build()
            DebridProvider.TORBOX ->
              Request.Builder()
                .url("${provider.baseUrl}torrents/createtorrent")
                .header("Authorization", "Bearer $token")
                .post(FormBody.Builder().add("magnet", magnetUri).build())
                .build()
            DebridProvider.ALLDEBRID ->
              Request.Builder()
                .url("${provider.baseUrl}magnet/upload?agent=TorrentClawAndroid&apikey=$token")
                .post(FormBody.Builder().add("magnets[]", magnetUri).build())
                .build()
            DebridProvider.PREMIUMIZE ->
              Request.Builder()
                .url("${provider.baseUrl}transfer/create?apikey=$token")
                .post(FormBody.Builder().add("src", magnetUri).build())
                .build()
          }
        client.newCall(request).execute().use { resp ->
          if (!resp.isSuccessful) {
            throw IllegalStateException("${provider.displayName} returned HTTP ${resp.code}.")
          }
          "Sent to ${provider.displayName} queue"
        }
      }
    }
}
