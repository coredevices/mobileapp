package io.rebble.libpebblecommon.util
 
import androidx.compose.ui.graphics.ImageBitmap
 
actual fun createImageBitmapFromPixelArray(
    pixels: IntArray,
    width: Int,
    height: Int
): ImageBitmap? {
    TODO("Not yet implemented")
}

actual fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean {
    return buffer.remaining == 0
}