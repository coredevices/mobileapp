package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Decorator that uploads recording data to a user-configured webhook endpoint.
 *
 * RecordingOnly payloads send at operation start (the audio is already on disk).
 * Transcript-bearing payloads send once the inner operation persists the transcript,
 * concurrently with agent processing. Operations with no transcript hook send after
 * the inner operation completes.
 */
class IndexWebhookUploadRecordingOperation(
    private val enqueue: suspend (IndexWebhookDeliveryEntity) -> Unit,
    private val webhookPreferences: IndexWebhookPreferences,
    private val decorated: RecordingOperation,
    private val fileId: String?,
    private val recordingId: Long,
    private val gesture: RingGesture,
): RecordingOperation, KoinComponent {

    companion object {
        private val logger = Logger.withTag("IndexWebhookUploadRecordingOperation")
    }

    private val recordingBackgroundScope: RecordingBackgroundScope by inject()
    private var transcriptDelivered = false

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        // One mode snapshot drives the whole delivery, so a mid-operation settings
        // change can't split the payload across incompatible modes.
        val payloadMode = webhookPreferences.configFor(gesture).payloadMode
        val decoratedWillSend = when {
            // Audio is already on disk and no transcript is in the payload, so send now.
            fileId != null && payloadMode == IndexWebhookPayloadMode.RecordingOnly -> {
                launchSend(payloadMode, transcription = null)
                true
            }
            // Send from the transcript hook, carrying the exact persisted transcript.
            decorated is TranscribingRecordingOperation -> {
                decorated.onTranscriptionPersisted = { transcription ->
                    transcriptDelivered = true
                    launchSend(payloadMode, transcription)
                }
                true
            }
            else -> false
        }
        try {
            decorated.run(handle)
        } catch (e: Exception) {
            // A transcription that failed for good (e.g. no speech detected) must still deliver the audio.
            if (e !is CancellationException && !transcriptDelivered &&
                fileId != null && payloadMode == IndexWebhookPayloadMode.Both
            ) {
                launchSend(payloadMode, transcription = null)
            }
            throw e
        }
        // Only operations that never sent above (e.g. webhook-only) fall back to a send
        // here — otherwise this could race the hook's send and win with a null transcript.
        // A webhook failure must never fail the recording.
        if (!decoratedWillSend) {
            try {
                sendWebhook(payloadMode, transcription = null)
            } catch (e: Exception) {
                logger.e(e) { "Webhook send failed" }
            }
        }
    }

    /** Launched so queueing the delivery doesn't delay the inner operation. */
    private fun launchSend(payloadMode: IndexWebhookPayloadMode, transcription: String?) {
        recordingBackgroundScope.launch {
            try {
                sendWebhook(payloadMode, transcription)
            } catch (e: Exception) {
                logger.e(e) { "Webhook send failed" }
            }
        }
    }

    private suspend fun sendWebhook(payloadMode: IndexWebhookPayloadMode, transcription: String?) {
        if (fileId == null && payloadMode == IndexWebhookPayloadMode.RecordingOnly) return
        enqueue(
            IndexWebhookDeliveryEntity(
                deliveryId = fileId ?: "text-$recordingId",
                gesture = gesture,
                recordingId = recordingId,
                fileId = fileId.takeIf { payloadMode != IndexWebhookPayloadMode.TranscriptionOnly },
                transcription = transcription.takeIf { payloadMode != IndexWebhookPayloadMode.RecordingOnly },
            )
        )
    }
}
