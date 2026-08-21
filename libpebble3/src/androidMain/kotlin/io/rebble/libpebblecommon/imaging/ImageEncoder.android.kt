package io.rebble.libpebblecommon.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/** Centre-crops and scales this bitmap to [width] x [height], then encodes it for the watch. */
fun Bitmap.encodeForWatch(width: Int, height: Int): EncodedImage {
    val scaled = centerCropScale(this, width, height)
    val argb = IntArray(width * height)
    scaled.getPixels(argb, 0, width, 0, 0, width, height)
    if (scaled !== this) scaled.recycle()
    return ImageEncoder.encode(argb, width, height)
}

private fun centerCropScale(source: Bitmap, width: Int, height: Int): Bitmap {
    if (source.width == width && source.height == height) return source
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val srcAspect = source.width.toFloat() / source.height
    val dstAspect = width.toFloat() / height
    val src = if (srcAspect > dstAspect) {
        val cropW = (source.height * dstAspect).toInt()
        val x = (source.width - cropW) / 2
        Rect(x, 0, x + cropW, source.height)
    } else {
        val cropH = (source.width / dstAspect).toInt()
        val y = (source.height - cropH) / 2
        Rect(0, y, source.width, y + cropH)
    }
    // Bilinear filter the downscale so the ditherer sees a smooth image, not an aliased one.
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, src, Rect(0, 0, width, height), paint)
    return out
}
