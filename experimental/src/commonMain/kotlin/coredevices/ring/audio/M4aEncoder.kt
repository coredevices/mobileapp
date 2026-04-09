package coredevices.ring.audio

/**
 * Platform-specific M4A (AAC) audio encoder.
 * Encodes raw PCM audio samples to M4A format for upload to external services.
 */
expect class M4aEncoder() {
    /**
     * Encode PCM audio samples to M4A format.
     * @param samples PCM audio samples (16-bit signed, mono)
     * @param sampleRate Sample rate of the input audio in Hz
     * @return M4A encoded audio bytes
     */
    suspend fun encode(samples: ShortArray, sampleRate: Int): ByteArray
}
