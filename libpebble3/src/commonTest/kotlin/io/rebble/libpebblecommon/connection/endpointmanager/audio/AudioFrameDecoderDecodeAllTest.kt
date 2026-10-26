package io.rebble.libpebblecommon.connection.endpointmanager.audio

import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class AudioFrameDecoderDecodeAllTest {
    private val speex = VoiceEncoderInfo.Speex(
        sampleRate = 16000,
        version = "1.2",
        bitRate = 16000,
        bitstreamVersion = 4,
        frameSize = 320,
    )

    private class FakeDecoder(
        private val failOn: UByte? = null,
        private val supported: Boolean = true,
    ) : AudioFrameDecoder {
        var closed = false
        override fun open(encoderInfo: VoiceEncoderInfo): AudioPcmDecoder? {
            if (!supported) return null
            return object : AudioPcmDecoder {
                override fun decode(frame: UByteArray): ByteArray? =
                    if (frame.firstOrNull() == failOn) null else frame.asByteArray()

                override fun close() {
                    closed = true
                }
            }
        }
    }

    @Test
    fun concatenatesDecodedFramesAndCloses() = runTest {
        val decoder = FakeDecoder()
        val pcm = decoder.decodeAll(speex, flowOf(ubyteArrayOf(1u, 2u), ubyteArrayOf(3u)))
        assertContentEquals(byteArrayOf(1, 2, 3), pcm.readByteArray())
        assertTrue(decoder.closed)
    }

    @Test
    fun failsOnUndecodableFrameAndCloses() = runTest {
        val decoder = FakeDecoder(failOn = 9u)
        val error = assertFailsWith<IllegalStateException> {
            decoder.decodeAll(speex, flowOf(ubyteArrayOf(1u), ubyteArrayOf(9u)))
        }
        assertEquals("Failed to decode audio frame", error.message)
        assertTrue(decoder.closed)
    }

    @Test
    fun failsOnUnsupportedEncoding() = runTest {
        assertFailsWith<IllegalStateException> {
            FakeDecoder(supported = false).decodeAll(speex, flowOf())
        }
    }
}
