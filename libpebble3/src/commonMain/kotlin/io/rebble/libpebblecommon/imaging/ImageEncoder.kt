package io.rebble.libpebblecommon.imaging

/**
 * Encodes an ARGB image as the watch's 4-bpp palettized [EncodedImage]: choose a 16-colour palette
 * by median cut over the watch's 64-colour (GColor8) space, Floyd–Steinberg dither to that palette,
 * and pack two 4-bit indices per byte (even x = high nibble, matching the firmware).
 */
object ImageEncoder {
    private const val MAX_COLORS = 16

    // 8-bit channel (0..255) -> 2-bit GColor8 channel (0..3), rounded to nearest of 0/85/170/255.
    private fun quant2(v: Int): Int = (v.coerceIn(0, 255) * 3 + 127) / 255
    private fun expand2(c: Int): Int = c * 85
    private fun gcolor8(r: Int, g: Int, b: Int): Int = (0x3 shl 6) or (r shl 4) or (g shl 2) or b

    private class Color(val r: Int, val g: Int, val b: Int, val count: Int)

    /** Encodes an ARGB8888 pixel array (row-major, [width] * [height]). */
    fun encode(argb: IntArray, width: Int, height: Int): EncodedImage {
        val palette = medianCutPalette(argb)
        val palR = IntArray(palette.size) { expand2((palette[it] shr 4) and 0x3) }
        val palG = IntArray(palette.size) { expand2((palette[it] shr 2) and 0x3) }
        val palB = IntArray(palette.size) { expand2(palette[it] and 0x3) }

        val stride = (width + 1) / 2
        val pixels = UByteArray(stride * height)
        var curErr = FloatArray(width * 3)
        var nextErr = FloatArray(width * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = argb[y * width + x]
                // Clamp pixel+error into gamut before matching, and diffuse the residual from the
                // clamped value; otherwise error compounds at saturated edges (dither worms).
                val r = (((p shr 16) and 0xFF) + curErr[x * 3].toInt()).coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) + curErr[x * 3 + 1].toInt()).coerceIn(0, 255)
                val b = ((p and 0xFF) + curErr[x * 3 + 2].toInt()).coerceIn(0, 255)
                val idx = nearest(r, g, b, palR, palG, palB)
                val bi = y * stride + (x shr 1)
                pixels[bi] = if (x and 1 == 0) {
                    ((pixels[bi].toInt() and 0x0F) or (idx shl 4)).toUByte()
                } else {
                    ((pixels[bi].toInt() and 0xF0) or idx).toUByte()
                }
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
        val paletteBytes = UByteArray(palette.size) { palette[it].toUByte() }
        return EncodedImage(width, height, paletteBytes, pixels)
    }

    // Median cut over the GColor8-reduced histogram. Returns up to 16 distinct GColor8 palette
    // bytes. Box choice and split point are weighted by pixel count, so a large flat region doesn't
    // lose a palette slot to a handful of stray pixels.
    private fun medianCutPalette(argb: IntArray): List<Int> {
        val counts = HashMap<Int, Int>()
        for (p in argb) {
            val key = (quant2((p shr 16) and 0xFF) shl 4) or
                (quant2((p shr 8) and 0xFF) shl 2) or quant2(p and 0xFF)
            counts[key] = (counts[key] ?: 0) + 1
        }
        val initial = counts.entries.map {
            Color((it.key shr 4) and 0x3, (it.key shr 2) and 0x3, it.key and 0x3, it.value)
        }.toMutableList()
        val boxes = mutableListOf(initial)
        while (boxes.size < MAX_COLORS) {
            val bi = boxes.indices.filter { boxes[it].size > 1 }
                .maxByOrNull { spread(boxes[it]) * population(boxes[it]) } ?: break
            val box = boxes[bi]
            val axis = longestAxis(box)
            box.sortBy { when (axis) { 0 -> it.r; 1 -> it.g; else -> it.b } }
            val mid = weightedMedian(box)
            boxes[bi] = box.subList(0, mid).toMutableList()
            boxes.add(box.subList(mid, box.size).toMutableList())
        }
        return boxes.filter { it.isNotEmpty() }.map { box ->
            var sr = 0L; var sg = 0L; var sb = 0L; var sn = 0L
            for (c in box) { sr += c.r.toLong() * c.count; sg += c.g.toLong() * c.count; sb += c.b.toLong() * c.count; sn += c.count }
            gcolor8(round(sr, sn), round(sg, sn), round(sb, sn))
        }.distinct().ifEmpty { listOf(gcolor8(0, 0, 0)) }
    }

    private fun round(sum: Long, count: Long): Int = ((sum * 2 + count) / (count * 2)).toInt()

    private fun population(box: List<Color>): Long = box.sumOf { it.count.toLong() }

    // Split index that puts half the box's pixels either side, keeping both halves non-empty.
    private fun weightedMedian(box: List<Color>): Int {
        val half = population(box) / 2
        var acc = 0L
        for (i in box.indices) {
            acc += box[i].count
            if (acc > half) return (i + 1).coerceIn(1, box.size - 1)
        }
        return box.size - 1
    }

    private fun spread(box: List<Color>): Int {
        var rl = 3; var rh = 0; var gl = 3; var gh = 0; var bl = 3; var bh = 0
        for (c in box) {
            if (c.r < rl) rl = c.r; if (c.r > rh) rh = c.r
            if (c.g < gl) gl = c.g; if (c.g > gh) gh = c.g
            if (c.b < bl) bl = c.b; if (c.b > bh) bh = c.b
        }
        return maxOf(rh - rl, gh - gl, bh - bl)
    }

    private fun longestAxis(box: List<Color>): Int {
        var rl = 3; var rh = 0; var gl = 3; var gh = 0; var bl = 3; var bh = 0
        for (c in box) {
            if (c.r < rl) rl = c.r; if (c.r > rh) rh = c.r
            if (c.g < gl) gl = c.g; if (c.g > gh) gh = c.g
            if (c.b < bl) bl = c.b; if (c.b > bh) bh = c.b
        }
        val dr = rh - rl; val dg = gh - gl; val db = bh - bl
        return if (dr >= dg && dr >= db) 0 else if (dg >= db) 1 else 2
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
