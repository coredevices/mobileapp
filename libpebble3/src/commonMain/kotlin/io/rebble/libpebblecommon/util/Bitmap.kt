package io.rebble.libpebblecommon.util

internal expect class Bitmap {
    val width: Int
    val height: Int

    /**
     * Return pixel at the specified position at in AARRGGBB format.
     */
    fun getPixel(x: Int, y: Int): Int
}