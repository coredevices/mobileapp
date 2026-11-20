package io.rebble.libpebblecommon.voice


/** Decodes codec frames to 16-bit little-endian mono PCM. */
public interface AudioFrameDecoder {
    /** Returns null if the codec is not supported. */
    public fun open(encoderInfo: VoiceEncoderInfo): AudioPcmDecoder?
}

public interface AudioPcmDecoder {
    /** Returns PCM for the frame, or null if it could not be decoded. */
    public fun decode(frame: UByteArray): ByteArray?
    public fun close()
}
