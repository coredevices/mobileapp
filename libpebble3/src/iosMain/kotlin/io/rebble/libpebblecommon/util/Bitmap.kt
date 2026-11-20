package io.rebble.libpebblecommon.util

internal actual class Bitmap {
    actual val width: Int
        get() = throw UnsupportedOperationException("Not supported on iOS yet")
    actual val height: Int
        get() = throw UnsupportedOperationException("Not supported on iOS yet")

    /**
     * Return pixel at the specified position at in AARRGGBB format.
     */
    actual fun getPixel(x: Int, y: Int): Int {
        throw UnsupportedOperationException("Not supported on iOS yet")
    }
}