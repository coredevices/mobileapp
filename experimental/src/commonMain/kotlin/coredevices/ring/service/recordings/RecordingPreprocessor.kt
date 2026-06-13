package coredevices.ring.service.recordings

import co.touchlab.kermit.Logger
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.readShortLe
import kotlinx.io.writeShortLe
import kotlin.math.sqrt
import kotlin.time.TimeSource
import kotlin.time.measureTime

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
        private val logger = Logger.withTag("RecordingPreprocessor")

        internal fun computeGain(samples: ShortArray, frameSize: Int): Float {
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

    suspend fun preprocess(fileId: String) = withContext(Dispatchers.IO) {
        // Read original (no processing) audio
        val (fileSource, info) = recordingStorage.openRecordingSource(fileId, useOriginalAudio = true)

        val allSamples = fileSource.use {
            readAllSamples(fileSource, info.size)
        }
        val frameDurationMs = 20
        // Compute whole-file gain based on average RMS of voiced frames, then apply uniformly (preserving relative dynamics for AI)
        val start = TimeSource.Monotonic.markNow()
        val gain = withContext(Dispatchers.Default) {
            computeGain(allSamples, (info.cachedMetadata.sampleRate * frameDurationMs) / 1000)
        }
        logger.d { "Computed gain of $gain for file $fileId in ${start.elapsedNow().inWholeMilliseconds}ms" }
        // Write out processed audio to 'normal' file
        recordingStorage.openRecordingSink(fileId, info.cachedMetadata.sampleRate, info.cachedMetadata.mimeType).use { sink ->
            val took = TimeSource.Monotonic.measureTime {
                for (s in allSamples) {
                    val amplified = (s * gain)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    sink.writeShortLe(amplified)
                }
            }
            logger.d { "Applied gain to file $fileId in ${took.inWholeMilliseconds}ms" }
        }
    }

    private fun readAllSamples(source: Source, size: Long): ShortArray {
        val count = (size / 2).toInt()
        return ShortArray(count) { source.readShortLe() }
    }
}