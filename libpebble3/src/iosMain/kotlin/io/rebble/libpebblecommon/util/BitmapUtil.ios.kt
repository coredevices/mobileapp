package io.rebble.libpebblecommon.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorType
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import co.touchlab.kermit.Logger

actual fun createImageBitmapFromPixelArray(
    pixels: IntArray,
    width: Int,
    height: Int
): ImageBitmap? {
    return try {
        val bitmap = Bitmap()
        val imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)

        val bytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            bytes[i * 4] = (p and 0xFF).toByte()
            bytes[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((p shr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((p shr 24) and 0xFF).toByte()
        }

        bitmap.installPixels(imageInfo, bytes, width * 4)
        bitmap.asComposeImageBitmap()
    } catch (e: Exception) {
        Logger.w(e) { "Failed to create image bitmap" }
        null
    }
}

actual fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean {
    return buffer.length >= expectedSize
}