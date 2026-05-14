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
        private const val TAPS_PER_PHASE = SINC_HALF_TAPS * 2
        // Number of quantized fractional positions for the polyphase filter bank
        private const val NUM_PHASES = 256
    }

    // Cutoff relative to the lower of the two rates to prevent aliasing
    private val cutoff: Double = minOf(sampleRateIn, sampleRateOut).toDouble() / sampleRateIn

    // Precomputed polyphase filter bank: for each quantized fractional offset,
    // store the 32 kernel weights (sinc * kaiser window * cutoff).
    // This eliminates all sin/sqrt/bessel calls from the per-sample loop.
    private val filterBank = Array(NUM_PHASES) { phase ->
        val frac = phase.toDouble() / NUM_PHASES
        DoubleArray(TAPS_PER_PHASE) { t ->
            val j = t - SINC_HALF_TAPS + 1
            val x = (j - frac) * cutoff
            sincKernel(x) * cutoff * kaiserWindow(j - frac, SINC_HALF_TAPS)
        }
    }

    // Kaiser window approximation (beta=6 gives ~-60 dB sidelobe, good for speech)
    private fun kaiserWindow(n: Double, halfWidth: Int): Double {
        val alpha = n / halfWidth
        if (abs(alpha) >= 1.0) return 0.0
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
        val lastIdx = input.size - 1

        for (i in 0 until outputLength) {
            val inputPos = i / ratio
            val center = floor(inputPos).toInt()
            val frac = inputPos - center

            val phaseIdx = (frac * NUM_PHASES).toInt().coerceIn(0, NUM_PHASES - 1)
            val kernel = filterBank[phaseIdx]

            var sample = 0.0
            for (t in 0 until TAPS_PER_PHASE) {
                val idx = (center + t - SINC_HALF_TAPS + 1).coerceIn(0, lastIdx)
                sample += input[idx].toDouble() * kernel[t]
            }

            output[i] = sample.toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return output
    }
}