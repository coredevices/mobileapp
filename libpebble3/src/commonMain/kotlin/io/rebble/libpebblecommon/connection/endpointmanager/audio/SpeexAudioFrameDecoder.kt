package io.rebble.libpebblecommon.connection.endpointmanager.audio

import coredevices.speex.SpeexCodec
import coredevices.speex.SpeexDecodeResult
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer

@OptIn(ExperimentalUnsignedTypes::class)
class SpeexAudioFrameDecoder : AudioFrameDecoder {
    override fun open(encoderInfo: VoiceEncoderInfo): AudioPcmDecoder? {
        val speex = encoderInfo as? VoiceEncoderInfo.Speex ?: return null
        val codec = SpeexCodec(
            sampleRate = speex.sampleRate,
            bitRate = speex.bitRate,
            frameSize = speex.frameSize,
        )
        val pcm = ByteArray(speex.frameSize * Short.SIZE_BYTES)
        return object : AudioPcmDecoder {
            override fun decode(frame: UByteArray): ByteArray? {
                val result = codec.decodeFrame(frame.asByteArray(), pcm, hasHeaderByte = true)
                return if (result == SpeexDecodeResult.Success) pcm.copyOf() else null
            }

            override fun close() {
                codec.close()
            }
        }
    }
}

/** Decodes a whole recording; throws if the codec is unsupported or any frame fails. */
@OptIn(ExperimentalUnsignedTypes::class)
suspend fun AudioFrameDecoder.decodeAll(
    encoderInfo: VoiceEncoderInfo,
    frames: Flow<UByteArray>,
): Buffer {
    val decoder = open(encoderInfo)
        ?: error("Unsupported voice encoding: ${encoderInfo::class.simpleName}")
    val output = Buffer()
    try {
        withContext(Dispatchers.IO) {
            frames.collect { frame ->
                output.write(decoder.decode(frame) ?: error("Failed to decode audio frame"))
            }
        }
    } finally {
        decoder.close()
    }
    return output
}
