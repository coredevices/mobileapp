package coredevices.ring.service.recordings

import co.touchlab.kermit.Logger
import coredevices.ring.database.Preferences
import coredevices.ring.storage.RecordingStorage
import coredevices.util.dictation.PebbleDictationSink
import kotlin.uuid.Uuid

/**
 * [PebbleDictationSink] that feeds dictation audio from one specific Pebble watchapp into the
 * same ring feed ingestion pipeline phone-microphone recordings use (`RecordingProcessingQueue`),
 * bypassing normal ASR entirely.
 *
 * Stage 1 (MVP): the target watchapp is a hardcoded UUID constant, matching the
 * `pebble-index-note` demo watchapp (see `pebble-index-note/package.json`). See
 * `specs/pebble-dictation-audio-index-feed-intercept.md` for the planned evolution to a
 * user-editable preference and then a picker, neither of which requires touching this class's
 * ingestion logic.
 */
class PebbleWatchappDictationSink(
    private val recordingStorage: RecordingStorage,
    private val recordingProcessingQueue: RecordingProcessingQueue,
    private val preferences: Preferences,
) : PebbleDictationSink {

    companion object {
        private val logger = Logger.withTag("PebbleWatchappDictationSink")

        /** Seed/default value only now: UUID of the production pebble-index-note watchapp. */
        val TARGET_WATCHAPP_UUID: Uuid = Uuid.parse("049e694d-7d8f-4615-92aa-b77163a174c8")
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
