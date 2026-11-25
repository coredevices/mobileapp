package io.rebble.libpebblecommon.util

import android.graphics.Bitmap
import io.rebble.libpebblecommon.image.PebbleBitmap

internal fun Bitmap.toPebbleBitmap(): PebbleBitmap {
    val argb = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
    val pixels = IntArray(argb.width * argb.height)
    argb.getPixels(pixels, 0, argb.width, 0, 0, argb.width, argb.height)
    return PebbleBitmap(width = argb.width, height = argb.height, pixels = pixels)
}

internal actual fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean {
    return buffer.remaining == 0
}
