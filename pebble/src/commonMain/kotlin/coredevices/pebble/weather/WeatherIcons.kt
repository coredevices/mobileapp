package coredevices.pebble.weather

import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.imaging.ImageEncoder

/**
 * The weather glyphs, drawn here rather than named for the watch to draw: a plugin that says
 * "sun" is asking every consumer to own a sun, and only the ones that happen to know the word.
 *
 * Two of each: the `image` is a filled disc in the condition's own colour with the glyph knocked
 * out of it in white — a shape that reads on a light or a dark watchface without the plugin
 * knowing which it is drawing onto — and the `icon` is the glyph alone, black on a transparent
 * ground, for a consumer that wants a small mark rather than a picture. Its palette has two
 * entries, so a consumer drawing onto a dark background can flip the colour itself.
 */
internal object WeatherIcons {
    // Straight out of the watch's own 64-colour space (each channel 0/85/170/255), so the
    // encoder places them exactly instead of dithering a speckle across the disc.
    private const val TRANSPARENT = 0
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val ORANGE = 0xFFFFAA00.toInt()
    private const val BLUE = 0xFF0055FF.toInt()
    private const val PALE_BLUE = 0xFF00AAFF.toInt()
    private const val GREY = 0xFFAAAAAA.toInt()

    private const val BLACK = 0xFF000000.toInt()

    /** Drawn at twice the size and averaged down, so the curves don't come out as staircases. */
    private const val SUPERSAMPLE = 2

    private val cache = mutableMapOf<String, EncodedImage>()

    /** The colour disc. */
    fun image(name: String, size: Int): EncodedImage =
        cache.getOrPut("image:$name@$size") { encode(name, size, mono = false) }

    /** The glyph alone, in black. */
    fun icon(name: String, size: Int): EncodedImage =
        cache.getOrPut("icon:$name@$size") { encode(name, size, mono = true) }

    private fun encode(name: String, size: Int, mono: Boolean): EncodedImage {
        val big = size * SUPERSAMPLE
        val canvas = IntArray(big * big) { TRANSPARENT }
        draw(canvas, big, name, if (mono) BLACK else WHITE, mono)
        return ImageEncoder.encode(downsample(canvas, big, size), size, size)
    }

    private fun draw(canvas: IntArray, size: Int, name: String, glyph: Int, mono: Boolean) {
        val centre = size / 2f
        if (!mono) circle(canvas, size, centre, centre, size / 2f, discColour(name))
        // The monochrome glyph has no disc to sit in, so it fills more of the square.
        val scale = if (mono) 1.25f else 1f
        when (name) {
            WeatherPlugin.ICON_SUN ->
                sun(canvas, size, centre, centre, size * 0.16f * scale, glyph)
            WeatherPlugin.ICON_PARTLY_CLOUDY -> {
                sun(canvas, size, size * 0.62f, size * 0.32f, size * 0.12f * scale, glyph)
                cloud(canvas, size, size * 0.06f, glyph, scale)
            }
            WeatherPlugin.ICON_CLOUDY -> cloud(canvas, size, 0f, glyph, scale)
            WeatherPlugin.ICON_LIGHT_RAIN -> streaks(canvas, size, 3, false, glyph, scale)
            WeatherPlugin.ICON_HEAVY_RAIN -> streaks(canvas, size, 4, false, glyph, scale)
            WeatherPlugin.ICON_LIGHT_SNOW -> streaks(canvas, size, 3, true, glyph, scale)
            WeatherPlugin.ICON_HEAVY_SNOW -> streaks(canvas, size, 4, true, glyph, scale)
            WeatherPlugin.ICON_RAIN_AND_SNOW -> {
                streaks(canvas, size, 2, false, glyph, scale)
                streaks(canvas, size, 1, true, glyph, scale)
            }
            // No glyph for this condition: the disc alone says "some weather", and with no disc
            // to fall back on the monochrome icon draws its outline.
            else -> if (mono) {
                circle(canvas, size, centre, centre, size * 0.44f, glyph)
                circle(canvas, size, centre, centre, size * 0.36f, TRANSPARENT)
            }
        }
    }

    private fun discColour(name: String) = when (name) {
        WeatherPlugin.ICON_SUN, WeatherPlugin.ICON_PARTLY_CLOUDY -> ORANGE
        WeatherPlugin.ICON_LIGHT_SNOW, WeatherPlugin.ICON_HEAVY_SNOW -> PALE_BLUE
        WeatherPlugin.ICON_LIGHT_RAIN, WeatherPlugin.ICON_HEAVY_RAIN,
        WeatherPlugin.ICON_RAIN_AND_SNOW -> BLUE
        else -> GREY
    }

    private fun sun(canvas: IntArray, size: Int, cx: Float, cy: Float, radius: Float, glyph: Int) {
        circle(canvas, size, cx, cy, radius, glyph)
        val thickness = maxOf(1f, size * 0.03f)
        for (i in 0 until 8) {
            val angle = i * 2f * PI / 8f
            val from = radius * 1.6f
            val to = radius * 2.2f
            line(
                canvas, size,
                cx + cos(angle) * from, cy + sin(angle) * from,
                cx + cos(angle) * to, cy + sin(angle) * to,
                thickness, glyph,
            )
        }
    }

    /** Three overlapping puffs over a rounded base, lifted by [lift] to sit under a sun. */
    private fun cloud(canvas: IntArray, size: Int, lift: Float, glyph: Int, scale: Float) {
        val base = size * 0.62f - lift
        val puff = size * 0.17f * scale
        circle(canvas, size, size * 0.38f, base - puff * 0.75f, puff, glyph)
        circle(canvas, size, size * 0.54f, base - puff * 1.15f, puff * 0.82f, glyph)
        circle(canvas, size, size * 0.66f, base - puff * 0.55f, puff * 0.66f, glyph)
        val half = puff * 0.42f
        val left = size * 0.30f - (scale - 1f) * size * 0.06f
        val right = size * 0.70f + (scale - 1f) * size * 0.06f
        circle(canvas, size, left, base - half, half, glyph)
        circle(canvas, size, right, base - half, half, glyph)
        rect(canvas, size, left, base - half * 2f, right - left, half * 2f, glyph)
    }

    private fun streaks(
        canvas: IntArray,
        size: Int,
        count: Int,
        flakes: Boolean,
        glyph: Int,
        scale: Float,
    ) {
        cloud(canvas, size, size * -0.06f, glyph, scale)
        val thickness = maxOf(1f, size * 0.035f * scale)
        val top = size * 0.72f
        val first = 0.5f - (count - 1) * 0.06f
        for (i in 0 until count) {
            val x = size * (first + i * 0.12f)
            if (flakes) {
                circle(canvas, size, x, top + size * 0.06f, size * 0.04f * scale, glyph)
            } else {
                line(canvas, size, x, top, x - size * 0.05f, top + size * 0.12f, thickness, glyph)
            }
        }
    }

    // ------------------------------------------------------------------ raster

    private fun circle(canvas: IntArray, size: Int, cx: Float, cy: Float, r: Float, colour: Int) {
        val squared = r * r
        for (y in maxOf(0, (cy - r).toInt())..minOf(size - 1, (cy + r).toInt() + 1)) {
            for (x in maxOf(0, (cx - r).toInt())..minOf(size - 1, (cx + r).toInt() + 1)) {
                val dx = x + 0.5f - cx
                val dy = y + 0.5f - cy
                if (dx * dx + dy * dy <= squared) canvas[y * size + x] = colour
            }
        }
    }

    private fun rect(
        canvas: IntArray,
        size: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        colour: Int,
    ) {
        for (y in maxOf(0, top.toInt())..minOf(size - 1, (top + height).toInt())) {
            for (x in maxOf(0, left.toInt())..minOf(size - 1, (left + width).toInt())) {
                canvas[y * size + x] = colour
            }
        }
    }

    private fun line(
        canvas: IntArray,
        size: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        thickness: Float,
        colour: Int,
    ) {
        val steps = maxOf(abs(x1 - x0), abs(y1 - y0)).toInt() * 2 + 1
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            circle(canvas, size, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, thickness / 2f, colour)
        }
    }

    /** Box filter, with the alpha averaged too so the edge fades rather than jumps. */
    private fun downsample(canvas: IntArray, from: Int, to: Int): IntArray {
        val out = IntArray(to * to)
        val block = from / to
        for (y in 0 until to) {
            for (x in 0 until to) {
                var a = 0; var r = 0; var g = 0; var b = 0
                for (dy in 0 until block) {
                    for (dx in 0 until block) {
                        val pixel = canvas[(y * block + dy) * from + (x * block + dx)]
                        val alpha = (pixel ushr 24) and 0xFF
                        a += alpha
                        r += ((pixel shr 16) and 0xFF) * alpha
                        g += ((pixel shr 8) and 0xFF) * alpha
                        b += (pixel and 0xFF) * alpha
                    }
                }
                val count = block * block
                out[y * to + x] = if (a == 0) {
                    TRANSPARENT
                } else {
                    ((a / count) shl 24) or ((r / a) shl 16) or ((g / a) shl 8) or (b / a)
                }
            }
        }
        return out
    }
}

private const val PI = 3.14159265f

private fun cos(angle: Float) = kotlin.math.cos(angle.toDouble()).toFloat()
private fun sin(angle: Float) = kotlin.math.sin(angle.toDouble()).toFloat()
private fun abs(value: Float) = kotlin.math.abs(value)
