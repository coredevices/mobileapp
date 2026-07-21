package coredevices.ring.service.recordings

import co.touchlab.kermit.Logger
import coredevices.ring.database.Preferences
import coredevices.ring.storage.RecordingStorage
import coredevices.util.dictation.PebbleDictationSink
import kotlin.uuid.Uuid

class PebbleWatchappDictationSink(
    private val recordingStorage: RecordingStorage,
    private val recordingProcessingQueue: RecordingProcessingQueue,
    private val preferences: Preferences,
) : PebbleDictationSink {

    companion object {
        private val logger = Logger.withTag("PebbleWatchappDictationSink")
    }

    override suspend fun canIngest(appUuid: Uuid): Boolean =
        appUuid in preferences.indexFeedWatchappUuids.value

    override suspend fun ingest(appUuid: Uuid, pcm: ByteArray, sampleRateHz: Int) {
        val fileId = "pebble-watch-${Uuid.random()}"
        logger.i {
            "Ingesting ${pcm.size} bytes of dictation audio from watchapp $appUuid as " +
                "$fileId (sampleRate=$sampleRateHz)"
        }
        recordingStorage.openRecordingSink(fileId, sampleRateHz, "audio/raw").use { it.write(pcm) }
        recordingProcessingQueue.queueLocalAudioProcessing(fileId)
    }
}
