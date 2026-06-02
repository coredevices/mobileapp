package io.rebble.libpebblecommon.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.image.PebbleBitmap

fun Bitmap.toPebbleBitmap(): PebbleBitmap {
    val argb = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
    val pixels = IntArray(argb.width * argb.height)
    argb.getPixels(pixels, 0, argb.width, 0, 0, argb.width, argb.height)
    return PebbleBitmap(width = argb.width, height = argb.height, pixels = pixels)
}

actual fun createImageBitmapFromPixelArray(
    pixels: IntArray,
    width: Int,
    height: Int
): ImageBitmap? {
    if (width <= 0 || height <= 0) return null
    return try {
        Bitmap.createBitmap(
            pixels,
            width,
            height,
            Bitmap.Config.ARGB_8888
        ).asImageBitmap()
    } catch (e: Exception) {
        Logger.w(e) { "Failed to create image bitmap" }
        null
    }
}

actual fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean {
    return buffer.remaining == 0
}