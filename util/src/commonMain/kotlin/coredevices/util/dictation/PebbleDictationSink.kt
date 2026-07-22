package coredevices.util.dictation

import kotlin.uuid.Uuid

interface PebbleDictationSink {
    suspend fun canIngest(appUuid: Uuid): Boolean
    suspend fun ingest(appUuid: Uuid, pcm: ByteArray, sampleRateHz: Int)
}
