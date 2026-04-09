package coredevices.ring.service.recordings

import coredevices.util.KrispAudioProcessor
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.readShortLe
import kotlinx.io.writeShortLe
import kotlin.math.sqrt

class RecordingPreprocessor(
    private val recordingStorage: RecordingStorage
) {
    companion object {
        // Target RMS level (~22% of Short.MAX_VALUE), representative of normal speech
        private const val TARGET_RMS = 7000.0
        // Maximum gain to avoid amplifying noise into fake speech
        private const val MAX_GAIN = 17f
        // Frames with RMS below this are considered silence and excluded from measurement
        private const val NOISE_FLOOR_RMS = 100.0
    }

    suspend fun preprocess(fileId: String) {
        withContext(Dispatchers.IO) {
            val audioProcessor = KrispAudioProcessor()
            audioProcessor.init()
            audioProcessor.use {
                val (fileSource, info) = recordingStorage.openRecordingSource(fileId)
                val source = Buffer()
                fileSource.transferTo(source)

                // Pass 1: Read all samples and compute uniform gain
                val allSamples = readAllSamples(source)
                val gain = computeGain(allSamples, audioProcessor.samplesPerFrame)

                // Pass 2: Apply uniform gain and process through Krisp
                recordingStorage.openRecordingSink(fileId, info.cachedMetadata.sampleRate, info.cachedMetadata.mimeType).use { sink ->
                    val frameSize = audioProcessor.samplesPerFrame
                    val buffer = ShortArray(frameSize)
                    val out = ShortArray(frameSize)
                    var offset = 0
                    while (offset < allSamples.size) {
                        val end = minOf(frameSize, allSamples.size - offset)
                        for (i in 0 until end) {
                            buffer[i] = (allSamples[offset + i] * gain)
                                .toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                        }
                        // Zero-fill remainder if last frame is partial
                        for (i in end until frameSize) {
                            buffer[i] = 0
                        }
                        audioProcessor.process(buffer, out)
                        for (i in 0 until end) {
                            sink.writeShortLe(out[i])
                        }
                        offset += end
                    }
                }
            }
        }
    }

    private fun readAllSamples(source: Buffer): ShortArray {
        val samples = mutableListOf<Short>()
        while (true) {
            try {
                samples.add(source.readShortLe())
            } catch (_: EOFException) {
                break
            }
        }
        return samples.toShortArray()
    }

    private fun computeGain(samples: ShortArray, frameSize: Int): Float {
        // Compute RMS over voiced frames only (frames above the noise floor)
        var voicedSumOfSquares = 0.0
        var voicedSampleCount = 0L

        var offset = 0
        while (offset < samples.size) {
            val end = minOf(frameSize, samples.size - offset)

            // Compute frame RMS
            var frameSum = 0.0
            for (i in 0 until end) {
                val s = samples[offset + i].toDouble()
                frameSum += s * s
            }
            val frameRms = sqrt(frameSum / end)

            // Only include voiced frames in the overall measurement
            if (frameRms > NOISE_FLOOR_RMS) {
                voicedSumOfSquares += frameSum
                voicedSampleCount += end
            }
            offset += end
        }

        if (voicedSampleCount == 0L) return 1f // silence, don't amplify

        val voicedRms = sqrt(voicedSumOfSquares / voicedSampleCount)
        if (voicedRms < 1.0) return 1f

        val gain = (TARGET_RMS / voicedRms).toFloat().coerceAtMost(MAX_GAIN)
        // Don't attenuate — only boost
        return gain.coerceAtLeast(1f)
    }
}