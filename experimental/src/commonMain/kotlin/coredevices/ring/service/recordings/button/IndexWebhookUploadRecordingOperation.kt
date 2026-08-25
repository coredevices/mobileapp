package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.audio.M4aEncoder
import coredevices.ring.external.indexwebhook.IndexWebhookDelivery
import coredevices.ring.external.indexwebhook.IndexWebhookDeliveryQueue
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
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
    private val webhookQueue: IndexWebhookDeliveryQueue,
    private val webhookPreferences: IndexWebhookPreferences,
    private val m4aEncoder: M4aEncoder,
    private val recordingStorage: RecordingStorage,
    private val decorated: RecordingOperation,
    private val fileId: String?,
    private val recordingId: Long,
    private val gesture: RingGesture,
): RecordingOperation, KoinComponent {

    companion object {
        private val logger = Logger.withTag("IndexWebhookUploadRecordingOperation")
    }

    private val recordingEntryDao: RecordingEntryDao by inject()
    private val localRecordingDao: LocalRecordingDao by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        // Run the inner operation first (transcription + agent processing)
        decorated.run(handle)

        val sendKey = fileId ?: "text-$recordingId"
        val config = webhookPreferences.configFor(gesture)
        val url = config.url
        if (!config.isActive || url == null) return
        val payloadMode = config.payloadMode
        // Typed input has no audio, so a recording-only webhook has nothing to deliver.
        if (fileId == null && payloadMode == IndexWebhookPayloadMode.RecordingOnly) return

        // Read audio samples if needed
        val samples: ShortArray?
        val sampleRate: Int
        if (fileId != null && payloadMode != IndexWebhookPayloadMode.TranscriptionOnly) {
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

        val audioData = samples?.let { m4aEncoder.encode(it, sampleRate) }
        webhookQueue.enqueue(
            IndexWebhookDelivery(
                deliveryId = sendKey,
                gesture = gesture,
                url = url,
                headers = config.headers,
                audioData = audioData,
                filename = audioData?.let { "$sendKey.m4a" },
                transcription = transcription,
                recordedAt = recordedAt,
            )
        )
        logger.d { "Queued webhook delivery for recording $sendKey" }
    }
}
