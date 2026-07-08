package coredevices.ring.service

import coredevices.resampler.Resampler
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.service.recordings.RecordingPreprocessor
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.files.Path
import kotlinx.io.writeShortLe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class RecordingPreprocessorTest {
    companion object {
        private val random = Random(1234)
    }

    @Test
    fun testComputeGainTiming() {
        val sampleRate = 16000
        val lengthInSeconds = 10
        val frameSize = 320 // 20 ms frames at 16 kHz
        val input = ShortArray(sampleRate * lengthInSeconds) {
            random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val results = (0 until 10).map {
            val inputCopy = input.copyOf()
            measureTime {
                RecordingPreprocessor.computeGain(inputCopy, frameSize)
            }.inWholeMicroseconds
        }

        val min = results.min()
        val max = results.max()
        val avg = results.average()
        val avgMs = avg / 1_000

        println("Gain compute on ${input.size} samples took: min = $min µs, max = $max µs, avg = $avg µs")
        assertTrue(avgMs < 30, "Average gain compute time should be under 50 ms, but was $avgMs ms")
    }

    @Test
    fun testPreprocessorTiming() {
        val sampleRate = 16000
        val lengthInSeconds = 10
        val input = ShortArray(sampleRate * lengthInSeconds) {
            random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val recordingStorage = object : RecordingStorage {
            override fun clearCacheDirectory() {}

            override suspend fun deleteAllCachedMetadata() {}

            override suspend fun deleteFromFirebaseStorage(id: String) {}

            override fun deleteRecording(id: String) {}

            override fun deleteRecordingFromCache(id: String) {}

            override suspend fun exportRecording(id: String, useOriginalAudio: Boolean): Path {
                TODO()
            }

            override fun getCacheDirectory(): Path {
                TODO()
            }

            override suspend fun openCachedRecordingSource(
                idNoSuffix: String,
                useOriginalAudio: Boolean
            ): Pair<Source, RecordingStorage.RecordingSourceInfo>? {
                TODO()
            }

            override suspend fun openOriginalRecordingSink(
                id: String,
                sampleRate: Int,
                mimeType: String
            ): Sink {
                TODO()
            }

            override suspend fun openRecordingSink(
                id: String,
                sampleRate: Int,
                mimeType: String
            ): Sink {
                return Buffer()
            }

            override suspend fun openRecordingSource(
                idNoSuffix: String,
                useOriginalAudio: Boolean
            ): Pair<Source, RecordingStorage.RecordingSourceInfo> {
                return Pair(
                    Buffer().apply {
                        // Write input audio as little-endian PCM
                        for (s in input) {
                            writeShortLe(s)
                        }
                    },
                    RecordingStorage.RecordingSourceInfo(
                        id = "",
                        size = input.size * 2L, // 2 bytes per sample
                        cachedMetadata = CachedRecordingMetadata(
                            id = "",
                            sampleRate = sampleRate,
                            mimeType = "audio/pcm"
                        ),
                    )
                )
            }

            override suspend fun persistRecording(id: String) {}

            override fun recordingExists(id: String): Boolean {
                TODO()
            }

            override suspend fun uploadRecordingPcm(
                id: String,
                sampleRate: Int,
                pcmBytes: ByteArray,
                encryptionKey: String?
            ) {}
        }

        val preprocessor = RecordingPreprocessor(recordingStorage)
        val time = measureTime {
            runBlocking {
                preprocessor.preprocess("test-file-id")
            }
        }

        println("Preprocessing took ${time.inWholeMilliseconds} ms")
        assertTrue(time.inWholeMilliseconds < 500, "Preprocessing should be under 500ms, but was ${time.inWholeMilliseconds} ms")
    }
}