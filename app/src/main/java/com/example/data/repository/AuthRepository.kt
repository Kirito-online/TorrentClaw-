package com.example.data.repository

import android.net.Uri
import com.example.core.preferences.AppPreferences
import com.example.core.security.KeystoreCredentialVault
import com.example.data.model.AccountTier
import com.example.data.model.DebridAccount
import com.example.data.model.DebridProvider
import com.example.data.model.UserAccount
import com.example.data.remote.DebridService
import com.example.data.remote.NetworkModule
import com.example.data.remote.RateLimitSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StoredAccountMeta(
  val username: String,
  val email: String? = null,
  val tierName: String = AccountTier.FREE.name,
  val stremioConfigured: Boolean = false,
  val torznabEnabled: Boolean = false,
)

@Serializable
private data class StoredDebridMeta(
  val username: String? = null,
  val planLabel: String? = null,
  val expiresAt: String? = null,
)

class AuthRepository(
  private val vault: KeystoreCredentialVault,
  private val appPreferences: AppPreferences,
  private val networkModule: NetworkModule,
  private val debridService: DebridService,
) {
  private val json = Json { ignoreUnknownKeys = true }

  private val _baseAccount = MutableStateFlow(restoreSavedAccount())
  private val _debridAccounts = MutableStateFlow(restoreDebridAccounts())
  val debridAccounts: StateFlow<List<DebridAccount>> = _debridAccounts.asStateFlow()

  val accountFlow: Flow<UserAccount> =
    combine(_baseAccount, networkModule.rateLimitState) { account, limits: RateLimitSnapshot ->
      account.copy(
        rateLimitTotal = limits.limit ?: account.tier.requestsPerMinute,
        rateLimitRemaining = limits.remaining,
      )
    }

  val currentAccount: UserAccount
    get() = _baseAccount.value

  private fun restoreSavedAccount(): UserAccount {
    val apiKey = vault.getSecret(KeystoreCredentialVault.KEY_API_KEY)
    val metaRaw = vault.getMetadata(KeystoreCredentialVault.KEY_ACCOUNT_JSON)
    if (apiKey.isNullOrBlank()) {
      return UserAccount(
        isSignedIn = false,
        username = "Guest",
        tier = AccountTier.ANONYMOUS,
        subscriptionStatus = "Anonymous (${AccountTier.ANONYMOUS.requestsPerMinute} req/min)",
      )
    }
    val meta = metaRaw?.let { runCatching { json.decodeFromString<StoredAccountMeta>(it) }.getOrNull() }
    val tier =
      meta?.tierName?.let { name -> AccountTier.entries.find { it.name == name } } ?: AccountTier.FREE
    return UserAccount(
      isSignedIn = true,
      username = meta?.username ?: "TorrentClaw User",
      email = meta?.email,
      tier = tier,
      apiKeyMasked = maskSecret(apiKey),
      subscriptionStatus = "${tier.label} (${tier.requestsPerMinute} req/min)",
      stremioAddonConfigured = meta?.stremioConfigured ?: false,
      torznabEnabled = meta?.torznabEnabled ?: (tier == AccountTier.PRO),
    )
  }

  private fun restoreDebridAccounts(): List<DebridAccount> {
    return DebridProvider.entries.map { provider ->
      val token = vault.getSecret(KeystoreCredentialVault.KEY_DEBRID_PREFIX + provider.name)
      val metaRaw = vault.getMetadata(KeystoreCredentialVault.KEY_DEBRID_META_PREFIX + provider.name)
      val meta = metaRaw?.let { runCatching { json.decodeFromString<StoredDebridMeta>(it) }.getOrNull() }
      DebridAccount(
        provider = provider,
        isConnected = !token.isNullOrBlank(),
        username = meta?.username,
        planLabel = meta?.planLabel,
        expiresAt = meta?.expiresAt,
      )
    }
  }

  suspend fun signInWithApiKey(
    rawApiKey: String,
    accountLabel: String? = null,
    email: String? = null,
  ): Result<UserAccount> =
    withContext(Dispatchers.IO) {
      runCatching {
        val key = rawApiKey.trim()
        require(key.length >= 8) { "Please enter a valid TorrentClaw API key." }

        // Temporarily store to verify against official live endpoints
        val previousKey = vault.getSecret(KeystoreCredentialVault.KEY_API_KEY)
        vault.putSecret(KeystoreCredentialVault.KEY_API_KEY, key)

        val statsResp = runCatching { networkModule.torrentClawApi.getStats() }.getOrNull()
        if (statsResp != null && (statsResp.code() == 401 || statsResp.code() == 403)) {
          vault.putSecret(KeystoreCredentialVault.KEY_API_KEY, previousKey)
          throw IllegalStateException("TorrentClaw rejected this API key (HTTP ${statsResp.code()}).")
        }

        // Check Pro access via official /search-analytics endpoint (Pro tier only)
        val analyticsResp =
          runCatching { networkModule.torrentClawApi.getSearchAnalytics("7d") }.getOrNull()
        val rateHeaderLimit = statsResp?.headers()?.get("X-RateLimit-Limit")?.toIntOrNull()
        val detectedTier =
          when {
            analyticsResp?.isSuccessful == true || (rateHeaderLimit != null && rateHeaderLimit >= 500) ->
              AccountTier.PRO
            else -> AccountTier.FREE
          }

        val resolvedUsername =
          accountLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotEmpty() }
            ?: "TorrentClaw Member"

        val meta =
          StoredAccountMeta(
            username = resolvedUsername,
            email = email?.trim()?.takeIf { it.isNotEmpty() },
            tierName = detectedTier.name,
            stremioConfigured = true,
            torznabEnabled = detectedTier == AccountTier.PRO,
          )
        vault.putMetadata(KeystoreCredentialVault.KEY_ACCOUNT_JSON, json.encodeToString(meta))
        val updated = restoreSavedAccount()
        _baseAccount.value = updated
        appPreferences.setOnboardingCompleted(true)
        updated
      }
    }

  suspend fun handleAuthDeepLink(uri: Uri): Result<UserAccount>? {
    val key = uri.getQueryParameter("key") ?: uri.getQueryParameter("api_key") ?: return null
    val username = uri.getQueryParameter("username") ?: uri.getQueryParameter("name")
    val email = uri.getQueryParameter("email")
    return signInWithApiKey(key, username, email)
  }

  suspend fun continueWithoutAccount() {
    vault.putSecret(KeystoreCredentialVault.KEY_API_KEY, null)
    vault.putSecret(KeystoreCredentialVault.KEY_SESSION_TOKEN, null)
    vault.putMetadata(KeystoreCredentialVault.KEY_ACCOUNT_JSON, null)
    _baseAccount.value = restoreSavedAccount()
    appPreferences.setOnboardingCompleted(true)
  }

  suspend fun logout() {
    vault.putSecret(KeystoreCredentialVault.KEY_API_KEY, null)
    vault.putSecret(KeystoreCredentialVault.KEY_SESSION_TOKEN, null)
    vault.putMetadata(KeystoreCredentialVault.KEY_ACCOUNT_JSON, null)
    _baseAccount.value = restoreSavedAccount()
  }

  suspend fun connectDebridProvider(provider: DebridProvider, token: String): Result<DebridAccount> {
    val result = debridService.verifyProviderToken(provider, token)
    result.onSuccess { verified ->
      vault.putSecret(KeystoreCredentialVault.KEY_DEBRID_PREFIX + provider.name, token.trim())
      val meta =
        StoredDebridMeta(
          username = verified.username,
          planLabel = verified.planLabel,
          expiresAt = verified.expiresAt,
        )
      vault.putMetadata(
        KeystoreCredentialVault.KEY_DEBRID_META_PREFIX + provider.name,
        json.encodeToString(meta),
      )
      _debridAccounts.value = restoreDebridAccounts()
    }
    return result
  }

  fun disconnectDebridProvider(provider: DebridProvider) {
    vault.putSecret(KeystoreCredentialVault.KEY_DEBRID_PREFIX + provider.name, null)
    vault.putMetadata(KeystoreCredentialVault.KEY_DEBRID_META_PREFIX + provider.name, null)
    _debridAccounts.value = restoreDebridAccounts()
  }

  fun getDebridToken(provider: DebridProvider): String? {
    return vault.getSecret(KeystoreCredentialVault.KEY_DEBRID_PREFIX + provider.name)
  }

  private fun maskSecret(secret: String): String {
    if (secret.length <= 8) return "••••••••"
    return "${secret.take(4)}••••${secret.takeLast(4)}"
  }
}
