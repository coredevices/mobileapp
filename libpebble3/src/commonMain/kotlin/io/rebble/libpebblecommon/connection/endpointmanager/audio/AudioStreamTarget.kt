package io.rebble.libpebblecommon.connection.endpointmanager.audio

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * A companion app that can receive its watchapp's microphone stream. Implemented alongside
 * [io.rebble.libpebblecommon.connection.CompanionApp] by companions that support it.
 */
interface AudioStreamTarget {
    val uuid: Uuid

    /** [onReady] fires once the stream is accepted; returns false if it never was or the companion stopped first. */
    suspend fun streamAudio(
        sampleRate: Int,
        pcm: Flow<ByteArray>,
        onReady: suspend () -> Unit,
    ): Boolean
}
