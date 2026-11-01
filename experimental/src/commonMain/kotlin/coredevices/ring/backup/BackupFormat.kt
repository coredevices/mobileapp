package coredevices.ring.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupManifest(
    val version: Int,
    val userId: String,
    val email: String,
    val exportedAt: String,
    val recordingCount: Int,
)

@Serializable
data class BackupAudioMeta(
    val sampleRate: Int = 16000,
    val mimeType: String = "audio/raw",
)

/**
 * Layout of a backup zip, shared by export and import.
 */
object BackupFormat {
    const val VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val ORIGINAL_SUFFIX = "-original"
    private const val RECORDINGS_DIR = "recordings"

    val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun documentEntry(firestoreId: String) = "$RECORDINGS_DIR/$firestoreId/document.json"
    fun audioEntry(firestoreId: String, variant: String) = "$RECORDINGS_DIR/$firestoreId/$variant.raw"
    fun audioMetaEntry(firestoreId: String, variant: String) = "$RECORDINGS_DIR/$firestoreId/$variant.meta.json"

    fun recordingIdFromEntry(entryName: String): String? {
        val parts = entryName.split("/")
        return if (parts.size >= 3 && parts[0] == RECORDINGS_DIR) parts[1] else null
    }

    fun decodeAudioMeta(bytes: ByteArray): BackupAudioMeta =
        runCatching { json.decodeFromString(BackupAudioMeta.serializer(), bytes.decodeToString()) }
            .getOrDefault(BackupAudioMeta())
}
