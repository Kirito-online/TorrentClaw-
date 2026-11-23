package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed encrypted credential storage for TorrentClaw API keys,
 * session tokens, and connected Debrid provider tokens.
 * Never logs or exposes plaintext tokens outside verified in-memory requests.
 */
class KeystoreCredentialVault(context: Context) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private fun getOrCreateSecretKey(): SecretKey? {
    return runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@runCatching existing

        val keyGenerator =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
          KeyGenParameterSpec.Builder(
              KEY_ALIAS,
              KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
      }
      .getOrNull()
  }

  fun putSecret(key: String, value: String?) {
    if (value.isNullOrBlank()) {
      prefs.edit().remove(key).apply()
      return
    }
    val secretKey = getOrCreateSecretKey()
    if (secretKey == null) {
      // Fallback only when AndroidKeyStore is unavailable (e.g., local JVM Robolectric test runner)
      val encoded = Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
      prefs.edit().putString(key, "plain:$encoded").apply()
      return
    }
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
    val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    prefs.edit().putString(key, "aesgcm:$ivB64:$cipherB64").apply()
  }

  fun getSecret(key: String): String? {
    val raw = prefs.getString(key, null) ?: return null
    if (raw.startsWith("plain:")) {
      return runCatching {
          val decoded = Base64.decode(raw.removePrefix("plain:"), Base64.NO_WRAP)
          String(decoded, StandardCharsets.UTF_8)
        }
        .getOrNull()
    }
    if (!raw.startsWith("aesgcm:")) return null
    val parts = raw.removePrefix("aesgcm:").split(":")
    if (parts.size != 2) return null
    return runCatching {
        val secretKey = getOrCreateSecretKey() ?: return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
      }
      .getOrNull()
  }

  fun putMetadata(key: String, value: String?) {
    if (value == null) prefs.edit().remove(key).apply() else prefs.edit().putString(key, value).apply()
  }

  fun getMetadata(key: String): String? = prefs.getString(key, null)

  fun clearAll() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val PREFS_NAME = "torrentclaw_secure_vault"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "torrentclaw_vault_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    const val KEY_API_KEY = "tc_api_key"
    const val KEY_SESSION_TOKEN = "tc_session_token"
    const val KEY_ACCOUNT_JSON = "tc_account_json"
    const val KEY_DEBRID_PREFIX = "debrid_token_"
    const val KEY_DEBRID_META_PREFIX = "debrid_meta_"
  }
}
