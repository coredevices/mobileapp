package coredevices.ring.audio

import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M4aRoundTripTest {

    private fun sinePcm(sampleRate: Int, seconds: Int, freq: Double = 440.0): ShortArray {
        val n = sampleRate * seconds
        return ShortArray(n) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
    }

    @Test
    fun encodeThenDecodeReturnsAudio() = runTest {
        val sampleRate = 16000
        val pcm = sinePcm(sampleRate, seconds = 1)

        val m4a = M4aEncoder().encode(pcm, sampleRate)
        assertTrue(m4a.isNotEmpty(), "encoder produced empty M4A")

        val decoded = M4aDecoder().decode(m4a)

        assertEquals(sampleRate, decoded.sampleRate)
        // Regression guard for the 0-sample decode bug: AAC adds priming/padding,
        // so just require the decoded audio to be non-trivial and close to the input length.
        assertTrue(
            decoded.samples.size > sampleRate / 2,
            "decoded ${decoded.samples.size} samples, expected ~$sampleRate"
        )
    }
}
