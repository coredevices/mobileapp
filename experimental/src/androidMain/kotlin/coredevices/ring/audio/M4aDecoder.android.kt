package coredevices.ring.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder

actual class M4aDecoder {
    companion object {
        private val logger = Logger.withTag("M4aDecoder")
        private const val TIMEOUT_US = 10000L
    }

    actual suspend fun decode(m4aBytes: ByteArray): DecodedAudio = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("decode_audio", ".m4a")
        try {
            tempFile.writeBytes(m4aBytes)
            logger.d { "Decoding ${m4aBytes.size} bytes M4A" }

            val extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)

            // Find the first audio track
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            require(audioTrackIndex >= 0 && format != null) { "No audio track found in M4A" }
            extractor.selectTrack(audioTrackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val outputSamples = ArrayList<Short>(m4aBytes.size * 4)
            var inputDone = false
            var outputDone = false

            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, sampleSize,
                                    extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                        if (bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val numShorts = shortBuffer.remaining()
                            val frameSamples = ShortArray(numShorts)
                            shortBuffer.get(frameSamples)
                            if (channelCount == 1) {
                                for (s in frameSamples) outputSamples.add(s)
                            } else {
                                // Downmix interleaved channels to mono by averaging
                                var i = 0
                                while (i + channelCount <= frameSamples.size) {
                                    var sum = 0
                                    for (c in 0 until channelCount) sum += frameSamples[i + c].toInt()
                                    outputSamples.add((sum / channelCount).toShort())
                                    i += channelCount
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            } finally {
                codec.stop()
                codec.release()
                extractor.release()
            }

            val samples = ShortArray(outputSamples.size) { outputSamples[it] }
            logger.d { "Decoded to ${samples.size} samples at ${sampleRate}Hz" }
            DecodedAudio(samples, sampleRate)
        } finally {
            tempFile.delete()
        }
    }
}