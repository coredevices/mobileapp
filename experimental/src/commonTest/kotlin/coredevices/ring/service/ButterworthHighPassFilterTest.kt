package coredevices.ring.service

import coredevices.ring.service.recordings.ButterworthHighPassFilter
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class ButterworthHighPassFilterTest {

    private companion object {
        const val SAMPLE_RATE = 16000
        const val CUTOFF_HZ = 30.0
        const val AMPLITUDE = 10000.0
        const val TOTAL_SAMPLES = SAMPLE_RATE * 2 // 2 seconds
        const val SETTLE_SAMPLES = 8000 // skip startup transient before measuring
    }

    /** Steady-state gain of the filter at [freqHz] in dB, measured on a pure sine tone. */
    private fun measureGainDb(freqHz: Double, order: Int): Double {
        val input = ShortArray(TOTAL_SAMPLES) { n ->
            (AMPLITUDE * sin(2.0 * PI * freqHz * n / SAMPLE_RATE)).toInt().toShort()
        }
        val output = input.copyOf()
        ButterworthHighPassFilter(SAMPLE_RATE, CUTOFF_HZ, order).process(output)

        fun rms(a: ShortArray): Double {
            var sum = 0.0
            for (i in SETTLE_SAMPLES until a.size) {
                val s = a[i].toDouble()
                sum += s * s
            }
            return sqrt(sum / (a.size - SETTLE_SAMPLES))
        }
        return 20.0 * log10(rms(output) / rms(input))
    }

    @Test
    fun stopbandStronglyAttenuatedAtOrder4() {
        // Modelled response is ~ -38 dB at 10 Hz; assert a comfortable >= 30 dB cut.
        val gain = measureGainDb(freqHz = 10.0, order = 4)
        assertTrue(gain <= -30.0, "10 Hz tone should be attenuated by >= 30 dB at order 4, but was $gain dB")
    }

    @Test
    fun passbandPreservedAtOrder4() {
        // Voice band should pass essentially untouched.
        val gain = measureGainDb(freqHz = 300.0, order = 4)
        assertTrue(gain in -0.5..0.5, "300 Hz tone should pass within +/- 0.5 dB, but was $gain dB")
    }

    @Test
    fun cornerIsAboutMinus3dB() {
        // Butterworth -3 dB corner sits at the cutoff regardless of order.
        val gain = measureGainDb(freqHz = CUTOFF_HZ, order = 4)
        assertTrue(gain in -4.0..-2.0, "30 Hz corner should be ~ -3 dB, but was $gain dB")
    }

    @Test
    fun higherOrderRollsOffSteeper() {
        val order2 = measureGainDb(freqHz = 10.0, order = 2)
        val order4 = measureGainDb(freqHz = 10.0, order = 4)
        assertTrue(
            order4 < order2 - 10.0,
            "order 4 should attenuate 10 Hz much more than order 2 (order2=$order2 dB, order4=$order4 dB)"
        )
    }
}
