package coredevices.resampler

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

class Resampler(private val sampleRateIn: Int, private val sampleRateOut: Int) {
    companion object {
        // Half the number of sinc lobes on each side of the center sample.
        // 16 taps (32 total kernel width) is a good balance for speech.
        private const val SINC_HALF_TAPS = 16
    }

    // Cutoff relative to the lower of the two rates to prevent aliasing
    private val cutoff: Double = minOf(sampleRateIn, sampleRateOut).toDouble() / sampleRateIn

    // Kaiser window approximation (beta=6 gives ~-60 dB sidelobe, good for speech)
    private fun kaiserWindow(n: Double, halfWidth: Int): Double {
        val alpha = n / halfWidth
        if (abs(alpha) >= 1.0) return 0.0
        // I0 approximation: Kaiser beta=6
        val beta = 6.0
        return bessel0(beta * kotlin.math.sqrt(1.0 - alpha * alpha)) / bessel0(beta)
    }

    // Modified Bessel function of the first kind, order 0 (series approximation)
    private fun bessel0(x: Double): Double {
        var sum = 1.0
        var term = 1.0
        val halfX = x / 2.0
        for (k in 1..20) {
            term *= (halfX / k)
            sum += term * term
        }
        return sum
    }

    private fun sincKernel(x: Double): Double {
        if (abs(x) < 1e-10) return 1.0
        val piX = PI * x
        return sin(piX) / piX
    }

    fun process(input: ShortArray): ShortArray {
        val ratio = sampleRateOut.toDouble() / sampleRateIn
        val outputLength = ceil(input.size * ratio).toInt()
        val output = ShortArray(outputLength)

        for (i in 0 until outputLength) {
            val inputPos = i / ratio
            val center = floor(inputPos).toInt()
            val frac = inputPos - center

            var sample = 0.0
            for (j in -SINC_HALF_TAPS + 1..SINC_HALF_TAPS) {
                val idx = center + j
                val inputSample = when {
                    idx < 0 -> input[0].toDouble()
                    idx >= input.size -> input[input.size - 1].toDouble()
                    else -> input[idx].toDouble()
                }
                val x = (j - frac) * cutoff
                val w = kaiserWindow(j - frac, SINC_HALF_TAPS)
                sample += inputSample * sincKernel(x) * cutoff * w
            }

            output[i] = sample.toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return output
    }
}