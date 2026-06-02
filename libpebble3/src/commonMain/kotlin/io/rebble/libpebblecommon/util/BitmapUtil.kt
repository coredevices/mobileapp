package io.rebble.libpebblecommon.util

import androidx.compose.ui.graphics.ImageBitmap
import io.rebble.libpebblecommon.image.PebbleBitmap

expect fun createImageBitmapFromPixelArray(
    pixels: IntArray,
    width: Int,
    height: Int
): ImageBitmap?

fun PebbleBitmap.toImageBitmap(): ImageBitmap? =
    createImageBitmapFromPixelArray(pixels = pixels, width = width, height = height)

expect fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean