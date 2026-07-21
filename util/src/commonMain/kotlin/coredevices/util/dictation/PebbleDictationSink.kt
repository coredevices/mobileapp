package coredevices.util.dictation

import kotlin.uuid.Uuid

/**
 * Sink for dictation audio originating from one specific Pebble watchapp, injected into a
 * `TranscriptionProvider` implementation so it can divert that app's sessions away from normal
 * ASR and into another ingestion pipeline instead. Lives in `:util` - the one module both the
 * caller (`:pebble`) and the implementation (`:experimental`) already depend on - so neither
 * module needs a new dependency edge to the other.
 */
interface PebbleDictationSink {
    suspend fun canIngest(appUuid: Uuid): Boolean
    suspend fun ingest(appUuid: Uuid, pcm: ByteArray, sampleRateHz: Int)
}
