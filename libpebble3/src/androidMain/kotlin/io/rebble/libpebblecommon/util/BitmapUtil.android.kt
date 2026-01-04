package io.rebble.libpebblecommon.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import co.touchlab.kermit.Logger

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