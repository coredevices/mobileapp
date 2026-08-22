package coredevices.ring.external.indexlocal

import co.touchlab.kermit.Logger
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.button.RecordingOperation
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.readShortLe
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

/**
 * Decorator that delivers recording data to a local Android app (Notesnook)
 * after the inner operation (transcription + agent) completes.
 *
 * Mirrors [coredevices.ring.service.recordings.button.IndexWebhookUploadRecordingOperation]
 * but uses an explicit intent instead of HTTP.
 */
class IndexLocalCaptureRecordingOperation(
    private val localCaptureApi: IndexLocalCaptureApi,
    private val recordingStorage: RecordingStorage,
    private val decorated: RecordingOperation,
    private val fileId: String?,
    private val recordingId: Long,
    private val gesture: RingGesture,
) : RecordingOperation, KoinComponent {

    companion object {
        private val logger = Logger.withTag("IndexLocalCaptureRecordingOperation")
        private val sentRecordingIds = mutableSetOf<String>()
        private val sentRecordingIdsLock = Mutex()
    }

    private val recordingEntryDao: RecordingEntryDao by inject()
    private val localRecordingDao: LocalRecordingDao by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        localCaptureApi.beginRecordingCapture()
        try {
            decorated.run(handle)

            val rec = localRecordingDao.getRecording(recordingId)
            val sendKey = rec?.firestoreId ?: fileId ?: "text-$recordingId"
            val payloadMode = IndexWebhookPayloadMode.Both
            if (!sentRecordingIdsLock.withLock { sentRecordingIds.add(sendKey) }) {
                logger.d { "Local capture already sent for recording $sendKey, skipping" }
                return
            }

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

            val transcription: String? = if (payloadMode != IndexWebhookPayloadMode.RecordingOnly) {
                recordingEntryDao.getMostRecentEntryForRecording(recordingId)?.transcription
            } else null

            val recordedAt = rec?.localTimestamp ?: Clock.System.now()

            localCaptureApi.deliverIfEnabled(samples, sampleRate, sendKey, transcription, recordedAt, gesture)
        } finally {
            localCaptureApi.endRecordingCapture()
        }
    }
}
