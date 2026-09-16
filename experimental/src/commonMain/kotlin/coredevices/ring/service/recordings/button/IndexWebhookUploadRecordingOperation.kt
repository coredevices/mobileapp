package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val webhookApi: IndexWebhookApi,
    private val webhookPreferences: IndexWebhookPreferences,
    private val recordingStorage: RecordingStorage,
    private val decorated: RecordingOperation,
    private val fileId: String?,
    private val recordingId: Long,
    private val gesture: RingGesture,
): RecordingOperation, KoinComponent {

    companion object {
        private val logger = Logger.withTag("IndexWebhookUploadRecordingOperation")
        private val sentRecordingIds = mutableSetOf<String>()
        private val sentRecordingIdsLock = Mutex()
    }

    private val localRecordingDao: LocalRecordingDao by inject()
    private val recordingBackgroundScope: RecordingBackgroundScope by inject()

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
                decorated.onTranscriptionPersisted = { transcription -> launchSend(payloadMode, transcription) }
                true
            }
            else -> false
        }
        decorated.run(handle)
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

    /** Launched so reading the audio payload doesn't delay the inner operation. */
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
        val sendKey = fileId ?: "text-$recordingId"
        if (fileId == null && payloadMode == IndexWebhookPayloadMode.RecordingOnly) return

        if (sentRecordingIdsLock.withLock { sendKey in sentRecordingIds }) return

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

        // Claimed only after the payload reads succeeded, so a failed attempt leaves the
        // key unclaimed for a later queue retry to pick up.
        if (!sentRecordingIdsLock.withLock { sentRecordingIds.add(sendKey) }) {
            logger.d { "Webhook already sent for recording $sendKey, skipping" }
            return
        }
        webhookApi.uploadIfEnabled(samples, sampleRate, sendKey, transcriptionToSend, recordedAt, gesture)
    }
}
