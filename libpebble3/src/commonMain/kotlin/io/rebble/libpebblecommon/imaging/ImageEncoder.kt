package io.rebble.libpebblecommon.imaging

/**
 * Encodes an ARGB image as the watch's 4-bpp palettized [EncodedImage]: choose a 16-colour palette
 * by median cut over the watch's 64 screen colors, Floyd–Steinberg dither to that palette, and
 * pack two 4-bit indices per byte (even x = high nibble, matching the firmware).
 *
 * Transparency is binary, and costs a palette slot when present. The watch only skips these
 * pixels when it draws with `GCompOpSet`; under the default `GCompOpAssign` they come out black.
 *
 * Both the palette and the per-pixel match are decided against what the panel actually shows (see
 * ScreenColors.kt), not the nominal 0/85/170/255 the GColor8 bits suggest. The bytes on the wire
 * are still GColor8 codes.
 */
object ImageEncoder {
    private const val MAX_COLORS = 16

    /** At or above this, a source pixel keeps its colour; below it, it becomes transparent. */
    private const val OPAQUE_ALPHA = 128

    /** GColor8 with all bits clear: alpha 0, which the firmware draws as nothing. */
    private const val TRANSPARENT = 0

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    // Screen color index (0..63) -> the GColor8 byte sent to the watch, always fully opaque.
    private fun gcolor8(code: Int): Int = 0xC0 or code

    private class Entry(val code: Int, val count: Int)

    /** Encodes an ARGB8888 pixel array (row-major, [width] * [height]). */
    fun encode(argb: IntArray, width: Int, height: Int): EncodedImage {
        val hasTransparency = argb.any { alphaOf(it) < OPAQUE_ALPHA }
        val colors = medianCutPalette(argb, if (hasTransparency) MAX_COLORS - 1 else MAX_COLORS)
        val palR = IntArray(colors.size) { SCREEN_R[colors[it]] }
        val palG = IntArray(colors.size) { SCREEN_G[colors[it]] }
        val palB = IntArray(colors.size) { SCREEN_B[colors[it]] }
        // Past the colours, so `nearest` can never land an opaque black pixel on it.
        val transparentIdx = colors.size

        val stride = (width + 1) / 2
        val pixels = UByteArray(stride * height)
        var curErr = FloatArray(width * 3)
        var nextErr = FloatArray(width * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = argb[y * width + x]
                if (hasTransparency && alphaOf(p) < OPAQUE_ALPHA) {
                    writeIndex(pixels, stride, x, y, transparentIdx)
                    // Nothing is drawn here, so there is no error to carry into the neighbours.
                    continue
                }
                // Clamp pixel+error into gamut before matching, and diffuse the residual from the
                // clamped value; otherwise error compounds at saturated edges (dither worms).
                val r = (((p shr 16) and 0xFF) + curErr[x * 3].toInt()).coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) + curErr[x * 3 + 1].toInt()).coerceIn(0, 255)
                val b = ((p and 0xFF) + curErr[x * 3 + 2].toInt()).coerceIn(0, 255)
                val idx = nearest(r, g, b, palR, palG, palB)
                writeIndex(pixels, stride, x, y, idx)
                val er = (r - palR[idx]).toFloat()
                val eg = (g - palG[idx]).toFloat()
                val eb = (b - palB[idx]).toFloat()
                if (x + 1 < width) diffuse(curErr, x + 1, er, eg, eb, 7f / 16f)
                if (y + 1 < height) {
                    if (x > 0) diffuse(nextErr, x - 1, er, eg, eb, 3f / 16f)
                    diffuse(nextErr, x, er, eg, eb, 5f / 16f)
                    if (x + 1 < width) diffuse(nextErr, x + 1, er, eg, eb, 1f / 16f)
                }
            }
            val tmp = curErr; curErr = nextErr; nextErr = tmp
            nextErr.fill(0f)
        }
        val paletteBytes = UByteArray(if (hasTransparency) colors.size + 1 else colors.size) {
            if (it == transparentIdx) TRANSPARENT.toUByte() else gcolor8(colors[it]).toUByte()
        }
        return EncodedImage(width, height, paletteBytes, pixels)
    }

    /** Even x is the high nibble, matching the firmware's packing. */
    private fun writeIndex(pixels: UByteArray, stride: Int, x: Int, y: Int, idx: Int) {
        val bi = y * stride + (x shr 1)
        pixels[bi] = if (x and 1 == 0) {
            ((pixels[bi].toInt() and 0x0F) or (idx shl 4)).toUByte()
        } else {
            ((pixels[bi].toInt() and 0xF0) or idx).toUByte()
        }
    }

    // Median cut over the screen colors the image's opaque pixels land on. Returns up to
    // [maxColors] distinct screen color indices. Box choice and split point are weighted by pixel
    // count, so a large flat region doesn't lose a palette slot to a handful of stray pixels.
    private fun medianCutPalette(argb: IntArray, maxColors: Int): List<Int> {
        val counts = HashMap<Int, Int>()
        for (p in argb) {
            if (alphaOf(p) < OPAQUE_ALPHA) continue
            val code = nearestScreenColor((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            counts[code] = (counts[code] ?: 0) + 1
        }
        val initial = counts.entries.map { Entry(it.key, it.value) }.toMutableList()
        val boxes = mutableListOf(initial)
        while (boxes.size < maxColors) {
            val bi = boxes.indices.filter { boxes[it].size > 1 }
                .maxByOrNull { spread(boxes[it]).toLong() * population(boxes[it]) } ?: break
            val box = boxes[bi]
            val axis = longestAxis(box)
            box.sortBy { channel(it.code, axis) }
            val mid = weightedMedian(box)
            boxes[bi] = box.subList(0, mid).toMutableList()
            boxes.add(box.subList(mid, box.size).toMutableList())
        }
        return boxes.filter { it.isNotEmpty() }.map { box ->
            // Screen colors can't be averaged into a new entry the way nominal 2-bit components
            // could, so take the one nearest the box's count-weighted centroid.
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var totalCount = 0L
            for (entry in box) {
                sumR += SCREEN_R[entry.code].toLong() * entry.count
                sumG += SCREEN_G[entry.code].toLong() * entry.count
                sumB += SCREEN_B[entry.code].toLong() * entry.count
                totalCount += entry.count
            }
            nearestScreenColor(
                round(sumR, totalCount),
                round(sumG, totalCount),
                round(sumB, totalCount),
            )
        }.distinct().ifEmpty { listOf(nearestScreenColor(0, 0, 0)) }
    }

    private fun round(sum: Long, count: Long): Int = ((sum * 2 + count) / (count * 2)).toInt()

    private fun channel(code: Int, axis: Int): Int = when (axis) {
        0 -> SCREEN_R[code]
        1 -> SCREEN_G[code]
        else -> SCREEN_B[code]
    }

    private fun population(box: List<Entry>): Long = box.sumOf { it.count.toLong() }

    // Split index that puts half the box's pixels either side, keeping both halves non-empty.
    private fun weightedMedian(box: List<Entry>): Int {
        val half = population(box) / 2
        var acc = 0L
        for (i in box.indices) {
            acc += box[i].count
            if (acc > half) return (i + 1).coerceIn(1, box.size - 1)
        }
        return box.size - 1
    }

    // Side lengths of the box's bounding cuboid, one per RGB channel, in screen colors.
    private fun extents(box: List<Entry>): IntArray {
        val lowest = intArrayOf(255, 255, 255)
        val highest = intArrayOf(0, 0, 0)
        for (entry in box) {
            for (axis in 0..2) {
                val value = channel(entry.code, axis)
                if (value < lowest[axis]) lowest[axis] = value
                if (value > highest[axis]) highest[axis] = value
            }
        }
        return IntArray(3) { highest[it] - lowest[it] }
    }

    private fun spread(box: List<Entry>): Int = extents(box).max()

    private fun longestAxis(box: List<Entry>): Int {
        val extent = extents(box)
        return when {
            extent[0] >= extent[1] && extent[0] >= extent[2] -> 0
            extent[1] >= extent[2] -> 1
            else -> 2
        }
    }

    private fun diffuse(err: FloatArray, x: Int, r: Float, g: Float, b: Float, w: Float) {
        err[x * 3] += r * w
        err[x * 3 + 1] += g * w
        err[x * 3 + 2] += b * w
    }

    private fun nearest(r: Int, g: Int, b: Int, palR: IntArray, palG: IntArray, palB: IntArray): Int {
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (j in palR.indices) {
            val dr = r - palR[j]; val dg = g - palG[j]; val db = b - palB[j]
            val d = dr * dr + dg * dg + db * db
            if (d < bestDist) { bestDist = d; best = j }
        }
        return best
    }
}
