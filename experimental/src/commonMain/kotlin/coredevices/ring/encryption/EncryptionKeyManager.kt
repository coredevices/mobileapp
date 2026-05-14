package coredevices.ring.encryption

import PlatformUiContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class KeyResult(
    val keyBase64: String,
    val fingerprint: String,
)

@Serializable
data class StoredKeyEntry(
    val email: String,
    @SerialName("key_base64")
    val keyBase64: String,
    val fingerprint: String,
)

expect class EncryptionKeyManager {
    /** Generate a 32-byte AES-256 key. */
    fun generateKey(): KeyResult

    /**
     * Save key to local secure storage that persists across app uninstalls.
     * Stores as an array of [StoredKeyEntry] keyed by email.
     */
    suspend fun saveKeyLocally(key: String, email: String)

    /** Read key for the given email from local secure storage. */
    suspend fun getLocalKey(email: String? = null): String?

    /** Get all stored key entries (for showing which accounts have keys). */
    suspend fun getStoredKeyEntries(): List<StoredKeyEntry>

    /** Save key to cloud keychain (Google Password Manager / iCloud Keychain). */
    suspend fun saveToCloudKeychain(uiContext: PlatformUiContext, key: String)

    /** Read key from cloud keychain. */
    suspend fun readFromCloudKeychain(uiContext: PlatformUiContext): String?
}
