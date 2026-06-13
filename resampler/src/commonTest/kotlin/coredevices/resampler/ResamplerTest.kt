package coredevices.resampler

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ResamplerTest {
    companion object {
        private val random = Random(1234)
    }

    @Test
    fun testResamplerTiming() {
        val sampleRateIn = 10000
        val sampleRateOut = 16000
        val lengthInSeconds = 10
        val input = ShortArray(sampleRateIn * lengthInSeconds) {
            random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val results = (0 until 10).map {
            val (resampler, instantiationTime) = measureTimedValue {
                Resampler(sampleRateIn, sampleRateOut)
            }
            val inputCopy = input.copyOf()
            instantiationTime.inWholeMicroseconds + measureTime {
                resampler.process(inputCopy)
            }.inWholeMicroseconds
        }

        val min = results.min()
        val max = results.max()
        val avg = results.average()
        val avgMs = avg / 1_000

        println("Resampling ${input.size} samples took: min = $min µs, max = $max µs, avg = $avg µs")
        assertTrue(avgMs < 50, "Average resampling time should be under 50 ms, but was $avgMs ms")
    }
}