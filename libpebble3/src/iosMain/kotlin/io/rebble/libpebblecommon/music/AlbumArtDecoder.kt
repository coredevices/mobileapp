package io.rebble.libpebblecommon.io.rebble.libpebblecommon.music

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/** ARGB8888 pixels of [encoded], center-cropped and scaled to [width] x [height]; null if undecodable. */
internal fun decodeAlbumArtArgb(encoded: ByteArray, width: Int, height: Int): IntArray? {
    val image = try {
        Image.makeFromEncoded(encoded)
    } catch (e: IllegalArgumentException) {
        return null
    }
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.drawImageRect(
        image,
        centerCrop(image.width, image.height, width, height),
        Rect.makeWH(width.toFloat(), height.toFloat()),
        SamplingMode.LINEAR,
        null,
        true,
    )
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
    if (!surface.readPixels(bitmap, 0, 0)) return null
    val bytes = bitmap.readPixels() ?: return null
    return IntArray(width * height) { i ->
        val b = bytes[i * 4].toInt() and 0xFF
        val g = bytes[i * 4 + 1].toInt() and 0xFF
        val r = bytes[i * 4 + 2].toInt() and 0xFF
        val a = bytes[i * 4 + 3].toInt() and 0xFF
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

private fun centerCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
    val srcAspect = srcW.toFloat() / srcH
    val dstAspect = dstW.toFloat() / dstH
    return if (srcAspect > dstAspect) {
        val w = srcH * dstAspect
        Rect.makeXYWH((srcW - w) / 2, 0f, w, srcH.toFloat())
    } else {
        val h = srcW / dstAspect
        Rect.makeXYWH(0f, (srcH - h) / 2, srcW.toFloat(), h)
    }
}
