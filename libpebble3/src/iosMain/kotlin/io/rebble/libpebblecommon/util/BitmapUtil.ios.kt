package io.rebble.libpebblecommon.util

internal actual fun isScreenshotFinished(
    buffer: DataBuffer,
    expectedSize: Int
): Boolean {
    return buffer.length >= expectedSize
}
