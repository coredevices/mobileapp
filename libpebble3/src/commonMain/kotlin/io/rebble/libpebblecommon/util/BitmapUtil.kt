package io.rebble.libpebblecommon.util

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun createImageBitmapFromPixelArray(
    pixels: IntArray,
    width: Int,
    height: Int
): ImageBitmap?

internal expect fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean