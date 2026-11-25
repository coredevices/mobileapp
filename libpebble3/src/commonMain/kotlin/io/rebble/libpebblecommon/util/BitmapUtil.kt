package io.rebble.libpebblecommon.util

internal expect fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean
