package coredevices.ring.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

actual class M4aEncoder {
    companion object {
        private val logger = Logger.withTag("M4aEncoder")
        private const val AAC_MIME_TYPE = "audio/mp4a-latm"
        private const val BIT_RATE = 128000 // 128 kbps
        private const val CHANNEL_COUNT = 1 // Mono
        private const val TIMEOUT_US = 10000L
    }

    actual suspend fun encode(samples: ShortArray, sampleRate: Int): ByteArray = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("temporary_audio", ".m4a")
        try {
            logger.d { "Encoding ${samples.size} samples at ${sampleRate}Hz to M4A" }

            // Convert ShortArray to ByteArray (PCM 16-bit little-endian)
            val pcmData = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                pcmData[i * 2] = (samples[i].toInt() and 0xFF).toByte()
                pcmData[i * 2 + 1] = (samples[i].toInt() shr 8 and 0xFF).toByte()
            }

            // Configure AAC encoder
            val format = MediaFormat.createAudioFormat(AAC_MIME_TYPE, sampleRate, CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            }

            val codec = MediaCodec.createEncoderByType(AAC_MIME_TYPE)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1
            var muxerStarted = false

            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputOffset = 0
            var inputDone = false
            var outputDone = false
            var presentationTimeUs = 0L
            val bytesPerSample = 2 // 16-bit PCM
            val samplesPerSecond = sampleRate

            try {
                while (!outputDone) {
                    // Feed input
                    if (!inputDone) {
                        val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                            inputBuffer.clear()

                            val remaining = pcmData.size - inputOffset
                            if (remaining > 0) {
                                val size = minOf(remaining, inputBuffer.capacity())
                                inputBuffer.put(pcmData, inputOffset, size)
                                val samplesInBuffer = size / bytesPerSample

                                // Check if this is the last chunk
                                val isLastChunk = (inputOffset + size) >= pcmData.size
                                val flags = if (isLastChunk) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0

                                codec.queueInputBuffer(inputBufferIndex, 0, size, presentationTimeUs, flags)
                                presentationTimeUs += (samplesInBuffer * 1_000_000L) / samplesPerSecond
                                inputOffset += size

                                if (isLastChunk) {
                                    inputDone = true
                                }
                            } else {
                                // No more data, send end of stream
                                codec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                        }
                    }

                    // Get output
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = codec.outputFormat
                            audioTrackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputBufferIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                            if (muxerStarted && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            } finally {
                codec.stop()
                codec.release()
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            }

            val result = tempFile.readBytes()
            logger.d { "Encoded to ${result.size} bytes M4A" }
            result
        } finally {
            tempFile.delete()
        }
    }
}
