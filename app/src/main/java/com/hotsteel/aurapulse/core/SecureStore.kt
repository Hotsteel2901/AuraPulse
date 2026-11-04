package com.hotsteel.aurapulse.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps a secret with an AES/GCM key that never leaves the Android Keystore.
 * The ciphertext is useless when copied to another device, and no plaintext
 * token is ever written to SharedPreferences.
 */
class SecureStore(context: Context, private val alias: String) {
  private val prefs = context.applicationContext.getSharedPreferences("aurapulse_secure", Context.MODE_PRIVATE)

  private fun key(): SecretKey {
    val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return generator.generateKey()
  }

  fun put(value: String?) {
    if (value.isNullOrBlank()) {
      prefs.edit().remove(alias).apply()
      return
    }
    runCatching {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, key())
      val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
      prefs.edit().putString(alias, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }
  }

  fun get(): String? {
    val stored = prefs.getString(alias, null) ?: return null
    return runCatching {
      val payload = Base64.decode(stored, Base64.NO_WRAP)
      val iv = payload.copyOfRange(0, IV_BYTES)
      val body = payload.copyOfRange(IV_BYTES, payload.size)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
      String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()
  }

  fun clear() {
    prefs.edit().remove(alias).apply()
    runCatching {
      val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
      if (store.containsAlias(alias)) store.deleteEntry(alias)
    }
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
  }
}
