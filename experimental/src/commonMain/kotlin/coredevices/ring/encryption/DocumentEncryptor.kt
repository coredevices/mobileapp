package coredevices.ring.encryption

import coredevices.indexai.data.entity.AssistantSessionDocument
import coredevices.indexai.data.entity.EncryptedEnvelope
import coredevices.indexai.data.entity.RecordingDocument
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class KeyFingerprintMismatchException(
    val expected: String,
    val actual: String,
) : Exception("Encryption key fingerprint mismatch (expected=$expected, actual=$actual)")

/**
 * High-level encrypt/decrypt for [RecordingDocument] and audio files.
 *
 * Encrypts sensitive fields (transcriptions, assistant session) into an [EncryptedEnvelope],
 * leaving metadata fields (timestamp, updated, fileName, status) in cleartext for queries/sync.
 */
class DocumentEncryptor(
    private val encryptionKeyManager: EncryptionKeyManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Encrypt sensitive fields of a [RecordingDocument].
     * Returns a new document with nulled sensitive fields and the [EncryptedEnvelope] populated.
     */
    fun encryptDocument(doc: RecordingDocument, keyBase64: String): RecordingDocument {
        val sensitiveData = SensitiveFields(
            transcriptions = doc.entries.map { it.transcription },
            assistantSession = doc.assistantSession,
        )
        val plaintext = json.encodeToString(SensitiveFields.serializer(), sensitiveData).encodeToByteArray()
        val encrypted = AesGcmCrypto.encrypt(plaintext, keyBase64) // IV(16) || ciphertext || tag
        val encodedBlob = kotlin.io.encoding.Base64.encode(encrypted)

        val envelope = EncryptedEnvelope(
            iv = kotlin.io.encoding.Base64.encode(encrypted.copyOfRange(0, 16)),
            ciphertext = encodedBlob,
            keyFingerprint = AesGcmCrypto.keyFingerprint(keyBase64),
        )

        // Null out sensitive fields, keep structural metadata
        val clearedEntries = doc.entries.map { entry ->
            entry.copy(transcription = null)
        }

        return doc.copy(
            entries = clearedEntries,
            assistantSession = null,
            encrypted = envelope,
        )
    }

    /**
     * Decrypt a [RecordingDocument] that has an [EncryptedEnvelope].
     * Returns the document with sensitive fields restored.
     * Throws [TamperedException] if the data has been tampered with.
     */
    fun decryptDocument(doc: RecordingDocument, keyBase64: String): RecordingDocument {
        val envelope = doc.encrypted ?: return doc

        val keyFingerprint = AesGcmCrypto.keyFingerprint(keyBase64)
        if (keyFingerprint != envelope.keyFingerprint) {
            throw KeyFingerprintMismatchException(
                expected = envelope.keyFingerprint,
                actual = keyFingerprint,
            )
        }

        val encryptedBytes = kotlin.io.encoding.Base64.decode(envelope.ciphertext)
        val plaintext = AesGcmCrypto.decrypt(encryptedBytes, keyBase64)
        val sensitiveData = json.decodeFromString(SensitiveFields.serializer(), plaintext.decodeToString())

        // Restore sensitive fields
        val restoredEntries = doc.entries.mapIndexed { index, entry ->
            val transcription = sensitiveData.transcriptions.getOrNull(index)
            entry.copy(transcription = transcription)
        }

        return doc.copy(
            entries = restoredEntries,
            assistantSession = sensitiveData.assistantSession,
            encrypted = null, // Clear envelope after decryption
        )
    }

    /**
     * Encrypt audio bytes. Returns IV(16) || ciphertext || tag.
     */
    fun encryptAudio(bytes: ByteArray, keyBase64: String): ByteArray {
        return AesGcmCrypto.encrypt(bytes, keyBase64)
    }

    /**
     * Decrypt audio bytes. Input must be IV(16) || ciphertext || tag.
     * Throws [TamperedException] if the data has been tampered with.
     */
    fun decryptAudio(encryptedBytes: ByteArray, keyBase64: String): ByteArray {
        return AesGcmCrypto.decrypt(encryptedBytes, keyBase64)
    }

    /**
     * Get the encryption key for the current signed-in user's email. Returns null if
     * no key is stored for this account.
     */
    suspend fun getKey(): String? {
        val email = Firebase.auth.currentUser?.emailOrNull
        return encryptionKeyManager.getLocalKey(email)
    }
}

@Serializable
private data class SensitiveFields(
    val transcriptions: List<String?>,
    @SerialName("assistant_session")
    val assistantSession: AssistantSessionDocument?,
)
