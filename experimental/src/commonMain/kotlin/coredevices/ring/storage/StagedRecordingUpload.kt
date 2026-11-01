package coredevices.ring.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A recording encoded (and encrypted) on disk, waiting for [RecordingUploadSyncHandler] to upload it. */
@Serializable
data class StagedRecordingUpload(
    val id: String,
    val sampleRate: Int,
    val keyFingerprint: String? = null,
) {
    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(payload: String): StagedRecordingUpload = json.decodeFromString(payload)
    }
}
