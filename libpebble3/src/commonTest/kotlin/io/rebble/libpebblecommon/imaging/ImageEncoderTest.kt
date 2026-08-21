package io.rebble.libpebblecommon.imaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ImageEncoderTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun gradient(w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        argb((x * 255) / w, (y * 255) / h, 128)
    }

    private fun verify(width: Int, height: Int) {
        val art = ImageEncoder.encode(gradient(width, height), width, height)
        assertEquals(width, art.width)
        assertEquals(height, art.height)
        // 4-bpp, rows padded to ceil(width/2) bytes.
        val stride = (width + 1) / 2
        assertEquals(stride * height, art.pixels.size)
        val paletteSize = art.palette.size
        assertTrue(paletteSize in 1..16, "palette 1..16")
        for (b in art.pixels) {
            assertTrue(((b.toInt() shr 4) and 0xF) < paletteSize, "hi index in palette")
            assertTrue((b.toInt() and 0xF) < paletteSize, "lo index in palette")
        }
        // Every palette entry must be an opaque GColor8 (alpha bits = 0b11).
        for (c in art.palette) {
            assertEquals(0x3, (c.toInt() shr 6) and 0x3)
        }
    }

    @Test fun encodesGetafix() = verify(260, 260)   // round getafix, full width
    @Test fun encodesObelix() = verify(166, 166)    // rectangular obelix
    @Test fun oddWidthPacksCorrectly() = verify(199, 100)

    @Test
    fun solidColourIsSinglePaletteEntry() {
        val art = ImageEncoder.encode(IntArray(4 * 2) { argb(0, 0, 0) }, 4, 2)
        assertEquals(1, art.palette.size)
        for (b in art.pixels) assertEquals(0, b.toInt())  // every index is 0
    }

    @Test
    fun evenPixelIsHighNibble() {
        // 2x1: x=0 black, x=1 white. Black is GColor8 0xC0, white 0xFF.
        val art = ImageEncoder.encode(intArrayOf(argb(0, 0, 0), argb(255, 255, 255)), 2, 1)
        assertEquals(1, art.pixels.size)
        val hi = (art.pixels[0].toInt() shr 4) and 0xF
        val lo = art.pixels[0].toInt() and 0xF
        assertEquals(0xC0.toUByte(), art.palette[hi], "x=0 (black) is the high nibble")
        assertEquals(0xFF.toUByte(), art.palette[lo], "x=1 (white) is the low nibble")
    }
}
