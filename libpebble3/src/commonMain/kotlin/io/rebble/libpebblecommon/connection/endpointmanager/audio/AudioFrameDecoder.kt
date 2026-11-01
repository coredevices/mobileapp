package io.rebble.libpebblecommon.connection.endpointmanager.audio

import io.rebble.libpebblecommon.voice.VoiceEncoderInfo

/** Decodes codec frames to 16-bit little-endian mono PCM. */
interface AudioFrameDecoder {
    /** Returns null if the codec is not supported. */
    fun open(encoderInfo: VoiceEncoderInfo): AudioPcmDecoder?
}

interface AudioPcmDecoder {
    /** Returns PCM for the frame, or null if it could not be decoded. */
    fun decode(frame: UByteArray): ByteArray?
    fun close()
}
