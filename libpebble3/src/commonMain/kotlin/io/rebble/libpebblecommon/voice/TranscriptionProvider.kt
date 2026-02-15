package io.rebble.libpebblecommon.voice

import kotlinx.coroutines.flow.Flow

interface TranscriptionProvider {
    suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean
    ): TranscriptionResult
    suspend fun canServeSession(): Boolean
}