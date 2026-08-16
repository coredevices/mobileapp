package coredevices.ring.service.recordings

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Butterworth high-pass as a cascade of second-order sections.
 *
 * `order` must be a positive even number (each biquad = 2 poles = 12 dB/octave).
 * The -3 dB corner stays at [cutoffHz] regardless of order; higher orders just
 * roll off the stopband more steeply.
 */
class ButterworthHighPassFilter(sampleRate: Int, cutoffHz: Double, order: Int = 4) {

    private val sections: List<Biquad> = run {
        require(order >= 2 && order % 2 == 0) { "order must be a positive even number" }
        require(cutoffHz > 0.0 && cutoffHz < sampleRate / 2.0) { "cutoffHz must be in (0, sampleRate/2)" }
        List(order / 2) { i ->
            // Pole Q for section i of an `order`-pole Butterworth filter
            val theta = PI * (2.0 * (i + 1) - 1.0) / (2.0 * order)
            Biquad.highPass(sampleRate, cutoffHz, q = 1.0 / (2.0 * cos(theta)))
        }
    }

    fun process(buffer: ShortArray, offset: Int = 0, count: Int = buffer.size - offset) {
        for (section in sections) section.process(buffer, offset, count)
    }

    fun reset() = sections.forEach { it.reset() }
}

/** Single second-order section, Direct Form I. */
private class Biquad(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(buffer: ShortArray, offset: Int, count: Int) {
        val end = offset + count
        var i = offset
        while (i < end) {
            val x0 = buffer[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0 // keep unclamped output in state so clipping doesn't corrupt the recursion
            buffer[i] = y0.toInt().coerceIn(-32768, 32767).toShort()
            i++
        }
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    companion object {
        // RBJ audio-EQ cookbook high-pass, coefficients normalized by a0
        fun highPass(sampleRate: Int, cutoffHz: Double, q: Double): Biquad {
            val w0 = 2.0 * PI * cutoffHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = ((1.0 + cosW0) / 2.0) / a0,
                b1 = (-(1.0 + cosW0)) / a0,
                b2 = ((1.0 + cosW0) / 2.0) / a0,
                a1 = (-2.0 * cosW0) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }
    }
}
