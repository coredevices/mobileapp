package io.rebble.libpebblecommon.util

/**
 * Convert android bitmap into common multiplatform bitmap.
 *
 * Only supported for [android.graphics.Bitmap.Config.ARGB_8888] formats
 */
internal actual class Bitmap(private val androidBitmap: android.graphics.Bitmap) {
    init {
        if (androidBitmap.config != android.graphics.Bitmap.Config.ARGB_8888) {
            throw IllegalArgumentException("Only ARGB_8888 bitmaps are supported")
        }
    }

    actual val width: Int
        get() = androidBitmap.width
    actual val height: Int
        get() = androidBitmap.height

    /**
     * Return pixel at the specified position at in AARRGGBB format.
     */
    actual fun getPixel(x: Int, y: Int): Int {
        return androidBitmap.getPixel(x, y)
    }

}