package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.ring.external.indexwebhook.IndexWebhookDelivery
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.CancellationException
import kotlinx.io.buffered
import kotlinx.io.readShortLe
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

/**
 * Decorator that uploads recording data to a user-configured webhook endpoint.
 *
 * RecordingOnly payloads send at operation start (the audio is already on disk).
 * Transcript-bearing payloads send once the inner operation persists the transcript,
 * concurrently with agent processing. Operations with no transcript hook send after
 * the inner operation completes.
 */
class IndexWebhookUploadRecordingOperation(
    private val enqueue: suspend (IndexWebhookDelivery) -> Unit,
    private val webhookPreferences: IndexWebhookPreferences,
    private val encodeM4a: suspend (ShortArray, Int) -> ByteArray,
    private val recordingStorage: RecordingStorage,
    private val decorated: RecordingOperation,
    private val fileId: String?,
    private val recordingId: Long,
    private val gesture: RingGesture,
): RecordingOperation, KoinComponent {

    companion object {
        private val logger = Logger.withTag("IndexWebhookUploadRecordingOperation")
    }

    private val localRecordingDao: LocalRecordingDao by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        // One mode snapshot drives the whole delivery, so a mid-operation settings
        // change can't split the payload across incompatible modes.
        val payloadMode = webhookPreferences.configFor(gesture).payloadMode
        val decoratedWillSend = when {
            // Audio is already on disk and no transcript is in the payload, so send now.
            fileId != null && payloadMode == IndexWebhookPayloadMode.RecordingOnly -> {
                enqueueWebhook(payloadMode, transcription = null)
                true
            }
            // Send from the transcript hook, carrying the exact persisted transcript.
            decorated is TranscribingRecordingOperation -> {
                decorated.onTranscriptionPersisted = { transcription -> enqueueWebhook(payloadMode, transcription) }
                true
            }
            else -> false
        }
        decorated.run(handle)
        // Only operations that never sent above (e.g. webhook-only) fall back to a send
        // here — otherwise this could race the hook's send and win with a null transcript.
        if (!decoratedWillSend) enqueueWebhook(payloadMode, transcription = null)
    }

    private suspend fun enqueueWebhook(payloadMode: IndexWebhookPayloadMode, transcription: String?) {
        try {
            prepareDelivery(payloadMode, transcription)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Webhook preparation failed" }
        }
    }

    private suspend fun prepareDelivery(payloadMode: IndexWebhookPayloadMode, transcription: String?) {
        val sendKey = fileId ?: "text-$recordingId"
        val config = webhookPreferences.configFor(gesture)
        val url = config.url
        if (!config.isActive || url == null) return
        if (fileId == null && payloadMode == IndexWebhookPayloadMode.RecordingOnly) return
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

        val transcriptionToSend = if (payloadMode != IndexWebhookPayloadMode.RecordingOnly) {
            transcription
        } else null

        val recordedAt = localRecordingDao.getRecording(recordingId)?.localTimestamp
            ?: Clock.System.now()

        val audioData = samples?.let { encodeM4a(it, sampleRate) }
        enqueue(
            IndexWebhookDelivery(
                deliveryId = sendKey,
                gesture = gesture,
                url = url,
                headers = config.headers,
                audioData = audioData,
                filename = audioData?.let { "$sendKey.m4a" },
                transcription = transcriptionToSend,
                recordedAt = recordedAt,
            )
        )
        logger.d { "Queued webhook delivery for recording $sendKey" }
    }
}
