package io.rebble.libpebblecommon.util

import androidx.compose.ui.graphics.ImageBitmap

expect fun createImageBitmapFromPixelArray(
    pixels: IntArray,
    width: Int,
    height: Int
): ImageBitmap?

expect fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean