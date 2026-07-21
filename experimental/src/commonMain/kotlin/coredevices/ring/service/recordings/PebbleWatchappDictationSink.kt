package coredevices.ring.service.recordings

import co.touchlab.kermit.Logger
import coredevices.resampler.Resampler
import coredevices.ring.database.Preferences
import coredevices.ring.storage.RecordingStorage
import coredevices.util.dictation.PebbleDictationSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class PebbleWatchappDictationSink(
    private val recordingStorage: RecordingStorage,
    private val recordingProcessingQueue: RecordingProcessingQueue,
    private val preferences: Preferences,
) : PebbleDictationSink {

    companion object {
        private val logger = Logger.withTag("PebbleWatchappDictationSink")
        private const val TARGET_SAMPLE_RATE = 16000
    }

    override suspend fun canIngest(appUuid: Uuid): Boolean =
        appUuid in preferences.indexFeedWatchappUuids.value

    override suspend fun ingest(appUuid: Uuid, pcm: ByteArray, sampleRateHz: Int) {
        val fileId = "pebble-watch-${Uuid.random()}"
        val resampledPcm = withContext(Dispatchers.Default) {
            if (sampleRateHz == TARGET_SAMPLE_RATE) {
                pcm
            } else {
                Resampler(sampleRateHz, TARGET_SAMPLE_RATE).process(pcm.toShortArrayLe()).toByteArrayLe()
            }
        }
        logger.i {
            "Ingesting ${resampledPcm.size} bytes of dictation audio from watchapp $appUuid as " +
                "$fileId (sampleRate=$sampleRateHz -> $TARGET_SAMPLE_RATE)"
        }
        recordingStorage.openRecordingSink(fileId, TARGET_SAMPLE_RATE, "audio/raw").use { it.write(resampledPcm) }
        recordingProcessingQueue.queueLocalAudioProcessing(fileId)
    }
}

private fun ByteArray.toShortArrayLe(): ShortArray {
    val samples = ShortArray(size / 2)
    for (i in samples.indices) {
        val lo = this[i * 2].toInt() and 0xFF
        val hi = this[i * 2 + 1].toInt()
        samples[i] = ((hi shl 8) or lo).toShort()
    }
    return samples
}

private fun ShortArray.toByteArrayLe(): ByteArray {
    val bytes = ByteArray(size * 2)
    for (i in indices) {
        val s = this[i].toInt()
        bytes[i * 2] = s.toByte()
        bytes[i * 2 + 1] = (s shr 8).toByte()
    }
    return bytes
}
