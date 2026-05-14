package coredevices.ring.service.recordings.button

import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import kotlinx.io.buffered
import kotlinx.io.readShortLe
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Decorator that uploads recording data to a user-configured webhook endpoint
 * after the inner operation (transcription + agent processing) completes.
 *
 * Based on payload mode, sends audio, transcription text, or both.
 * Uses the same PCM→M4A encoding pipeline as the original Vermillion integration.
 */
class IndexWebhookUploadRecordingOperation(
    private val webhookApi: IndexWebhookApi,
    private val webhookPreferences: IndexWebhookPreferences,
    private val recordingStorage: RecordingStorage,
    private val decorated: RecordingOperation,
    private val fileId: String,
    private val recordingId: Long
): RecordingOperation, KoinComponent {

    private val recordingEntryDao: RecordingEntryDao by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        // Run the inner operation first (transcription + agent processing)
        decorated.run(handle)

        val payloadMode = webhookPreferences.payloadMode.value

        // Read audio samples if needed
        val samples: ShortArray?
        val sampleRate: Int
        if (payloadMode != IndexWebhookPayloadMode.TranscriptionOnly) {
            val (source, meta) = recordingStorage.openRecordingSource(fileId)
            samples = ShortArray((meta.size / 2).toInt())
            source.buffered().use {
                for (i in samples.indices) {
                    samples[i] = it.readShortLe()
                }
            }
            sampleRate = meta.cachedMetadata.sampleRate
        } else {
            samples = null
            sampleRate = 16000
        }

        // Read transcription if needed
        val transcription: String? = if (payloadMode != IndexWebhookPayloadMode.RecordingOnly) {
            recordingEntryDao.getMostRecentEntryForRecording(recordingId)?.transcription
        } else null

        webhookApi.uploadIfEnabled(samples, sampleRate, fileId, transcription)
    }
}
