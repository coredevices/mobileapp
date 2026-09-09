package io.rebble.libpebblecommon.imaging

/**
 * What the watch's 64 GColor8 codes actually look like on screen.
 *
 * The encoder used to assume each 2-bit channel lands on 0/85/170/255 in sRGB. A reflective panel
 * does not behave that way: its channel steps are uneven and its primaries bleed into one another,
 * so matching against the nominal values picks codes the screen never shows and washes every image
 * out.
 *
 * Measured on a Pebble Time 2 in sunlight, indexed by the low six bits of the GColor8 code
 * (0xC0..0xFF), packed 0xRRGGBB.
 */
private val MEASURED_PT2 = intArrayOf(
    0x1E191F, 0x1B2D43, 0x0F3C5B, 0x00476D,
    0x384F3D, 0x345856, 0x2E5E68, 0x266577,
    0x4A6D4F, 0x467263, 0x407973, 0x3C7D81,
    0x55825B, 0x51866C, 0x4C897A, 0x498C85,

    0x4D2A30, 0x4D374C, 0x494261, 0x464B71,
    0x5B5444, 0x595B5A, 0x56636C, 0x52677A,
    0x666F54, 0x637366, 0x607976, 0x5C7D82,
    0x6C825E, 0x69856E, 0x68887B, 0x648B86,

    0x69343C, 0x683E52, 0x644765, 0x615074,
    0x71574A, 0x6D5D5E, 0x6D636E, 0x69687A,
    0x7A7057, 0x777568, 0x737977, 0x727D83,
    0x7D8061, 0x7B846F, 0x77877C, 0x758987,

    0x7B3A42, 0x7A4358, 0x784B68, 0x755374,
    0x815A50, 0x7E6060, 0x7C656F, 0x7B6A7C,
    0x856F59, 0x86756B, 0x847A79, 0x817F84,
    0x8B8265, 0x8A8572, 0x86887D, 0x848A88,
)

private fun channel(packed: Int, shift: Int) = (packed shr shift) and 0xFF

// The table is indexed by (r shl 4) or (g shl 2) or b, so the panel's own black and white are the
// first and last entries. In sunlight the screen spans about a fifth of sRGB's range, and a viewer
// adapts to that, so the palette is chosen against colors normalized to those endpoints: what
// should steer the choice is the shape of the correction, not the dimming.
private fun normalize(shift: Int): IntArray {
    val black = channel(MEASURED_PT2.first(), shift)
    val white = channel(MEASURED_PT2.last(), shift)
    return IntArray(MEASURED_PT2.size) {
        (((channel(MEASURED_PT2[it], shift) - black) * 255) / (white - black)).coerceIn(0, 255)
    }
}

/** Screen colors normalized to the panel's black and white, one array per channel. */
internal val SCREEN_R = normalize(16)
internal val SCREEN_G = normalize(8)
internal val SCREEN_B = normalize(0)

/** Index of the screen color closest to the given sRGB value. */
internal fun nearestScreenColor(r: Int, g: Int, b: Int): Int {
    var best = 0
    var bestDistanceSq = Int.MAX_VALUE
    for (i in SCREEN_R.indices) {
        val dr = r - SCREEN_R[i]
        val dg = g - SCREEN_G[i]
        val db = b - SCREEN_B[i]
        val distanceSq = dr * dr + dg * dg + db * db
        if (distanceSq < bestDistanceSq) {
            bestDistanceSq = distanceSq
            best = i
        }
    }
    return best
}
