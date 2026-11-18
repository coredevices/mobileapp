package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.RecordingEntryErrorType
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.ring.external.indexwebhook.IndexWebhookDeliveryOutcome
import coredevices.ring.external.indexwebhook.IndexWebhookDeliveryQueue
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.util.trace.RingTraceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

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
        // Only operations that never sent above (e.g. typed text) fall back to a send
        // here — otherwise this could race the hook's send and win with a null transcript.
        if (!decoratedWillSend) launchSend(payloadMode, transcription = null)
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

/** Webhook-only route: keeps the recording in the feed with the delivery outcome. No agent runs. */
class WebhookOnlyRecordingOperation(
    private val deliveryQueue: IndexWebhookDeliveryQueue,
    private val webhookConfigured: Boolean,
    recordingId: Long,
    transferId: Long?,
    fileId: String,
    trace: RingTraceSession,
) : TranscribedRecordingOperation(recordingId, transferId, fileId, trace) {

    /** Latest attempt the wrapping decorator's delivery has made; its deliveryId is the fileId. */
    private val latestOutcome = MutableStateFlow<IndexWebhookDeliveryOutcome?>(null)

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        if (!webhookConfigured) {
            // Nothing will be sent, so don't spend a transcription (or its retries) on it.
            markFailed(createRecordingEntry(handle), "Webhook is not set up")
            return
        }
        coroutineScope {
            // Subscribed before the decorator can queue the delivery, so a fast send isn't missed.
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                deliveryQueue.outcomes.filter { it.deliveryId == fileId }.collect { latestOutcome.value = it }
            }
            try {
                super.run(handle)
            } finally {
                watcher.cancel()
            }
        }
    }

    override suspend fun processTranscription(scope: CoroutineScope, entryId: Long, text: String) {
        // The queue keeps retrying past this wait; the webhook's Recent runs show the final result.
        val outcome = withTimeoutOrNull(DELIVERY_TIMEOUT) {
            latestOutcome.first { it != null && !it.willRetry }
        } ?: latestOutcome.value
        val error = webhookDeliveryError(outcome)
        recordingProcessor.recordDirectResult(
            recordingId = recordingId,
            recordingEntryId = entryId,
            text = text,
            toolCallId = Uuid.random().toString(),
            result = if (error == null) {
                ToolCallResult("Sent to webhook", SemanticResult.ListItemCreation(text, destination = "webhook"))
            } else {
                ToolCallResult(error, SemanticResult.GenericFailure(userErrorMessage = error))
            },
        )
        if (error == null) {
            recordingEntryDao.updateRecordingEntryStatus(entryId, status = RecordingEntryStatus.completed)
        } else {
            markFailed(entryId, outcome?.result?.let { "$error: ${it.detail}" } ?: error)
        }
    }

    private suspend fun markFailed(entryId: Long, error: String) {
        recordingEntryDao.updateRecordingEntryStatus(
            entryId,
            status = RecordingEntryStatus.agent_error,
            error = error,
            errorType = RecordingEntryErrorType.agent_failed,
        )
    }

    private companion object {
        // Covers audio encoding, the webhook client's 2 minute request timeout and a retry or two.
        val DELIVERY_TIMEOUT = 3.minutes
    }
}

/**
 * The feed error for a webhook-only delivery, or null when it counts as sent. [outcome] is the last
 * attempt seen: null when none finished in time, with a null result when the webhook was off.
 */
internal fun webhookDeliveryError(outcome: IndexWebhookDeliveryOutcome?): String? {
    val result = outcome?.result
    return when {
        outcome == null -> "Webhook did not respond"
        result == null || result.ok -> null
        result.status == "FAILED" -> "Couldn't reach the webhook"
        else -> "Webhook failed (${result.status})"
    }
}
