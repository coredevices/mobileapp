package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookGesture
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.readShortLe
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

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
    private val configGesture: IndexWebhookGesture,
    private val observedGesture: IndexWebhookGesture?,
    private val recordingStorage: RecordingStorage,
    private val decorated: RecordingOperation,
    private val fileId: String,
    private val recordingId: Long,
): RecordingOperation, KoinComponent {

    companion object {
        private val logger = Logger.withTag("IndexWebhookUploadRecordingOperation")
        private val sentRecordingIds = mutableSetOf<String>()
        private val sentRecordingIdsLock = Mutex()
    }

    private val recordingEntryDao: RecordingEntryDao by inject()
    private val localRecordingDao: LocalRecordingDao by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        // Run the inner operation first (transcription + agent processing)
        decorated.run(handle)

        if (!sentRecordingIdsLock.withLock { sentRecordingIds.add(fileId) }) {
            logger.d { "Webhook already sent for recording $fileId, skipping" }
            return
        }

        // Re-read after the inner operation: the user may have unlinked the webhook while it ran.
        val config = webhookPreferences.config(configGesture).value
        if (!config.isConfigured) {
            logger.d { "Webhook no longer configured for $configGesture, skipping upload for $fileId" }
            return
        }
        val payloadMode = config.payloadMode

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

        val recordedAt = localRecordingDao.getRecording(recordingId)?.localTimestamp
            ?: Clock.System.now()

        webhookApi.upload(config, samples, sampleRate, fileId, transcription, recordedAt, observedGesture)
    }
}
