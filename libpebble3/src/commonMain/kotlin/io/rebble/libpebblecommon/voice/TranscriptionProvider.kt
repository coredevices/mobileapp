package io.rebble.libpebblecommon.voice

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.seconds

public interface TranscriptionProvider {
    public suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean
    ): TranscriptionResult
    public suspend fun canServeSession(): Boolean
}

/**
 * The maximum amount of time the Pebble firmware will wait for a transcription result before timing
 * out and cancelling the session.
 */
public val PEBBLE_FW_TRANSCRIPTION_TIMEOUT: Duration = 15.seconds