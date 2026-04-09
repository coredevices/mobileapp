package coredevices.ring.service.recordings.button

import coredevices.ring.external.vermillion.VermillionApi
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.buffered
import kotlinx.io.readShortLe

class VermillionUploadRecordingOperation(
    private val vermillionApi: VermillionApi,
    private val recordingStorage: RecordingStorage,
    private val decorated: RecordingOperation,
    private val fileId: String
): RecordingOperation {
    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        decorated.run(handle)
        val (source, meta) = recordingStorage.openRecordingSource(fileId)
        val samples = ShortArray((meta.size / 2).toInt())
        source.buffered().use {
            for (i in samples.indices) {
                samples[i] = it.readShortLe()
            }
        }
        vermillionApi.uploadIfEnabled(samples, meta.cachedMetadata.sampleRate, fileId)
    }
}