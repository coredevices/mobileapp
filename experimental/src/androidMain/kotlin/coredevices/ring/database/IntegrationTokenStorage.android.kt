package coredevices.ring.database

import coredevices.util.integrations.IntegrationTokenStorage
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

actual class IntegrationTokenStorageImpl(
    context: Context
) : IntegrationTokenStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val keyLock = Any()

    fun clearKey() = synchronized(keyLock) {
        keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        val entry = keyStore.getEntry(KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        // Store IV length (1 byte) + IV + ciphertext, all base64-encoded
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        iv.copyInto(combined, 1)
        ciphertext.copyInto(combined, 1 + iv.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size >= 1 + GCM_IV_LENGTH + GCM_TAG_LENGTH / 8) { "Ciphertext too short" }
        val ivLength = combined[0].toInt() and 0xFF
        require(ivLength > 0 && 1 + ivLength < combined.size) { "Invalid IV length" }
        val iv = combined.copyOfRange(1, 1 + ivLength)
        val ciphertext = combined.copyOfRange(1 + ivLength, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext))
    }

    actual override suspend fun saveToken(key: String, token: String) {
        prefs.edit { putString(key, encrypt(token)) }
    }

    actual override suspend fun getToken(key: String): String? {
        val encrypted = prefs.getString(key, null) ?: return null
        return try {
            decrypt(encrypted)
        } catch (_: Exception) {
            // If decryption fails (e.g. key was rotated), remove the corrupt entry
            prefs.edit { remove(key) }
            null
        }
    }

    actual override suspend fun deleteToken(key: String) {
        prefs.edit { remove(key) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "integration_token_key"
        private const val PREFS_NAME = "integration_tokens"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}