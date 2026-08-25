package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.external.indexwebhook.sendsBeforeProcessing
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
 * Based on payload mode, sends audio, transcription text, or both.
 * Uses the same PCM→M4A encoding pipeline as the original Vermillion integration.
 *
 * **When it fires depends on the payload mode.** A mode that carries the transcription has to wait
 * for the inner operation, because that is what writes the transcription it will read. A
 * [IndexWebhookPayloadMode.RecordingOnly] webhook never reads it: the audio is already fully
 * written on disk before this operation starts, so waiting buys the endpoint nothing and costs it
 * the whole transcription + agent turnaround. So that mode sends straight away, in parallel with
 * the inner operation, and — because it no longer sits after it — still delivers the audio when
 * transcription fails outright.
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

    private val recordingEntryDao: RecordingEntryDao by inject()
    private val localRecordingDao: LocalRecordingDao by inject()
    private val backgroundScope: RecordingBackgroundScope by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        // Read the mode once. Settings are live, so re-reading it after the inner operation could
        // pick a different branch from the one that built the payload.
        val payloadMode = webhookPreferences.configFor(gesture).payloadMode
        val sendEarly = payloadMode.sendsBeforeProcessing(hasAudio = fileId != null)

        if (sendEarly) {
            // The long-lived background scope, not the queue's: a slow upload must not hold the
            // processing slot, and the queue cancelling this task must not cancel the delivery.
            backgroundScope.launch {
                try {
                    send(payloadMode)
                } catch (e: Exception) {
                    // The webhook is an add-on. It may never be the reason a recording fails.
                    logger.e(e) { "Error sending early webhook for recording $fileId" }
                }
            }
        }

        decorated.run(handle)

        // Anything carrying the transcription has to wait for the inner operation to write it.
        if (!sendEarly) send(payloadMode)
    }

    private suspend fun send(payloadMode: IndexWebhookPayloadMode) {
        val sendKey = fileId ?: "text-$recordingId"
        // Typed input has no audio, so a recording-only webhook has nothing to deliver.
        if (fileId == null && payloadMode == IndexWebhookPayloadMode.RecordingOnly) return

        if (!sentRecordingIdsLock.withLock { sentRecordingIds.add(sendKey) }) {
            logger.d { "Webhook already sent for recording $sendKey, skipping" }
            return
        }

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

        webhookApi.uploadIfEnabled(samples, sampleRate, sendKey, transcription, recordedAt, gesture)
    }
}
