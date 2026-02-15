package coredevices.pebble.services

import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.flow.Flow

class NullTranscriptionProvider: TranscriptionProvider {
    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean
    ): TranscriptionResult {
        audioFrames.collect {  }
        return TranscriptionResult.Disabled
    }

    override suspend fun canServeSession(): Boolean = false
}