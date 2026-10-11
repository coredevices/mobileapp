package io.rebble.libpebblecommon.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.uuid.Uuid

/** AES-GCM with a Keystore key under [keyAlias]; ciphertexts live in the [prefsName] prefs file. */
class KeystoreSecureStorage(
    context: Context,
    private val keyAlias: String,
    private val prefsName: String,
) : SecureStorage {
    private val context = context.applicationContext

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        val entry = keyStore.getEntry(keyAlias, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
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
        // IV length (1 byte) + IV + ciphertext, base64-encoded
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

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit { putString(key, encrypt(value)) }
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val encrypted = prefs.getString(key, null) ?: return@withContext null
        try {
            decrypt(encrypted)
        } catch (_: Exception) {
            // Undecryptable (e.g. the key was deleted): drop the entry.
            prefs.edit { remove(key) }
            null
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        prefs.edit { remove(key) }
    }

    override suspend fun destroy() = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) { clear() }
        context.deleteSharedPreferences(prefsName)
        synchronized(keyLock) { keyStore.deleteEntry(keyAlias) }
    }

    companion object {
        private val keyLock = Any()
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}

internal actual fun pluginSecureStorage(appContext: AppContext, pluginUuid: Uuid): SecureStorage =
    "plugin_secrets_$pluginUuid".let { KeystoreSecureStorage(appContext.context, keyAlias = it, prefsName = it) }
