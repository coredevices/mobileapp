package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.ring.external.indexwebhook.IndexWebhookDelivery
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Decorator that uploads recording data to a user-configured webhook endpoint.
 *
 * RecordingOnly payloads begin preparation at operation start (the audio is already on disk).
 * Transcript-bearing payloads begin preparation once the inner operation persists the transcript,
 * concurrently with agent processing. Operations with no transcript hook send after
 * the inner operation completes.
 */
class IndexWebhookUploadRecordingOperation(
    private val enqueue: suspend (IndexWebhookDelivery) -> Unit,
    private val webhookPreferences: IndexWebhookPreferences,
    private val decorated: RecordingOperation,
    private val fileId: String?,
    private val recordingId: Long,
    private val gesture: RingGesture,
    private val backgroundScope: RecordingBackgroundScope,
): RecordingOperation {

    companion object {
        private val logger = Logger.withTag("IndexWebhookUploadRecordingOperation")
    }

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        var enqueueJob: Job? = null
        fun enqueueInBackground(payloadMode: IndexWebhookPayloadMode, transcription: String?) {
            enqueueJob = backgroundScope.launch {
                enqueueWebhook(payloadMode, transcription)
            }
        }

        // One mode snapshot drives the whole delivery, so a mid-operation settings
        // change can't split the payload across incompatible modes.
        val payloadMode = webhookPreferences.configFor(gesture).payloadMode
        val decoratedWillSend = when {
            // Audio is already on disk and no transcript is in the payload, so prepare it now.
            fileId != null && payloadMode == IndexWebhookPayloadMode.RecordingOnly -> {
                enqueueInBackground(payloadMode, transcription = null)
                true
            }
            // Send from the transcript hook, carrying the exact persisted transcript.
            decorated is TranscribingRecordingOperation -> {
                decorated.onTranscriptionPersisted = { transcription ->
                    enqueueInBackground(payloadMode, transcription)
                }
                true
            }
            else -> false
        }
        try {
            decorated.run(handle)
            // Only operations that never sent above (e.g. webhook-only) fall back to a send
            // here — otherwise this could race the hook's send and win with a null transcript.
            if (!decoratedWillSend) enqueueWebhook(payloadMode, transcription = null)
        } finally {
            enqueueJob?.join()
        }
    }

    private suspend fun enqueueWebhook(payloadMode: IndexWebhookPayloadMode, transcription: String?) {
        try {
            val sendKey = fileId ?: "text-$recordingId"
            val config = webhookPreferences.configFor(gesture)
            val url = config.url
            if (!config.isActive || url == null) return
            if (fileId == null && payloadMode == IndexWebhookPayloadMode.RecordingOnly) return
            enqueue(
                IndexWebhookDelivery(
                    deliveryId = sendKey,
                    gesture = gesture,
                    url = url,
                    headers = config.headers,
                    fileId = fileId?.takeUnless { payloadMode == IndexWebhookPayloadMode.TranscriptionOnly },
                    transcription = transcription?.takeUnless { payloadMode == IndexWebhookPayloadMode.RecordingOnly },
                    recordingId = recordingId,
                )
            )
            logger.d { "Queued webhook delivery for recording $sendKey" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Webhook preparation failed" }
        }
    }
}
