package coredevices.ring.encryption

import PlatformUiContext
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual class EncryptionKeyManager(
    private val context: Context
) {
    private val logger = Logger.withTag("EncryptionKeyManager")
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    actual fun generateKey(): KeyResult {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        val keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        val fingerprint = sha256Hex(keyBytes)
        return KeyResult(
            keyBase64 = keyBase64,
            fingerprint = fingerprint,
        )
    }

    actual suspend fun saveKeyLocally(key: String, email: String) {
        val fingerprint = AesGcmCrypto.keyFingerprint(key)
        val entry = StoredKeyEntry(email = email, keyBase64 = key, fingerprint = fingerprint)

        // Load existing entries, replace or add for this email
        val entries = loadEntries().toMutableList()
        entries.removeAll { it.email == email }
        entries.add(entry)
        saveEntries(entries)

        logger.i { "Key saved locally for $email (${entries.size} total entries)" }
    }

    actual suspend fun getLocalKey(email: String?): String? {
        val entries = loadEntries()
        if (entries.isEmpty()) return null
        return if (email != null) {
            entries.find { it.email == email }?.keyBase64
        } else {
            // Return first/only key if no email specified
            entries.firstOrNull()?.keyBase64
        }
    }

    actual suspend fun getStoredKeyEntries(): List<StoredKeyEntry> {
        return loadEntries()
    }

    actual suspend fun saveToCloudKeychain(uiContext: PlatformUiContext, key: String) {
        val activity = uiContext.activity
        val credentialManager = CredentialManager.create(activity)
        val request = CreatePasswordRequest(
            id = CREDENTIAL_ID,
            password = key,
        )
        try {
            credentialManager.createCredential(activity, request)
            logger.i { "Key saved to Google Password Manager" }
        } catch (e: CreateCredentialCancellationException) {
            throw Exception("Save cancelled. You can save the key later from the Backup menu.")
        } catch (e: CreateCredentialException) {
            val msg = when {
                e.message?.contains("provider", ignoreCase = true) == true ->
                    "Google Password Manager is not set up. Go to Settings > Passwords & Accounts to configure it."
                else -> "Could not save to Password Manager: ${e.message}"
            }
            throw Exception(msg)
        }
    }

    actual suspend fun readFromCloudKeychain(uiContext: PlatformUiContext): String? {
        val activity = uiContext.activity
        val credentialManager = CredentialManager.create(activity)
        val request = GetCredentialRequest(listOf(GetPasswordOption()))
        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is PasswordCredential && credential.id == CREDENTIAL_ID) {
                logger.i { "Key read from Google Password Manager" }
                credential.password
            } else {
                logger.w { "No matching credential found in Password Manager" }
                throw Exception("No encryption key found in Google Password Manager. Generate a new key first.")
            }
        } catch (e: NoCredentialException) {
            throw Exception("No encryption key found in Google Password Manager. Generate a new key first.")
        } catch (e: GetCredentialCancellationException) {
            throw Exception("Cancelled by user.")
        } catch (e: GetCredentialException) {
            val msg = when {
                e.message?.contains("provider", ignoreCase = true) == true ->
                    "Google Password Manager is not set up. Go to Settings > Passwords & Accounts to configure it."
                else -> "Could not read from Password Manager: ${e.message}"
            }
            throw Exception(msg)
        }
    }

    // --- Encrypted local storage ---

    private fun loadEntries(): List<StoredKeyEntry> {
        val encrypted = prefs.getString(LOCAL_KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val decrypted = decrypt(encrypted)
            json.decodeFromString<List<StoredKeyEntry>>(decrypted)
        } catch (e: Exception) {
            logger.e(e) { "Failed to load key entries" }
            emptyList()
        }
    }

    private fun saveEntries(entries: List<StoredKeyEntry>) {
        val jsonStr = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(StoredKeyEntry.serializer()), entries)
        prefs.edit { putString(LOCAL_KEY_ENTRIES, encrypt(jsonStr)) }
    }

    private fun getOrCreateKey(): SecretKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) return entry.secretKey
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        iv.copyInto(combined, 1)
        ciphertext.copyInto(combined, 1 + iv.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLength = combined[0].toInt() and 0xFF
        val iv = combined.copyOfRange(1, 1 + ivLength)
        val ciphertext = combined.copyOfRange(1 + ivLength, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext))
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "encryption_private_key"
        private const val PREFS_NAME = "encryption_keys"
        private const val LOCAL_KEY_ENTRIES = "local_key_entries"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val CREDENTIAL_ID = "Pebble Index Private Key"
    }
}
