package coredevices.ring.audio

/**
 * Decoded audio result from [M4aDecoder.decode].
 */
data class DecodedAudio(val samples: ShortArray, val sampleRate: Int)

/**
 * Platform-specific M4A (AAC) audio decoder.
 * Decodes M4A audio bytes back to raw PCM samples for processing.
 */
expect class M4aDecoder() {
    /**
     * Decode M4A audio bytes to PCM samples.
     * @param m4aBytes M4A (AAC) encoded audio bytes
     * @return Decoded PCM audio samples (16-bit signed, mono) with sample rate
     */
    suspend fun decode(m4aBytes: ByteArray): DecodedAudio
}