package coredevices.pebble.actions.watch

import coredevices.util.imageBitmapToPngBase64
import coredevices.util.imageBitmapToPngBytes
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble

/**
 * Takes a screenshot of the connected watch and returns it as base64 PNG.
 * Returns empty string if no watch connected, screenshot unsupported, or it fails.
 */
suspend fun getWatchScreenshotBase64(libPebble: LibPebble): String {
    val watch = libPebble.watches.value
        .filterIsInstance<ConnectedPebbleDevice>()
        .firstOrNull()
    if (watch is ConnectedPebble.Screenshot) {
        val bitmap = watch.takeScreenshot()
        if (bitmap != null) {
            return imageBitmapToPngBase64(bitmap)
        }
    }
    return ""
}

/**
 * Takes a screenshot of the connected watch and returns it as raw PNG bytes.
 * Returns null if no watch connected, screenshot unsupported, or encoding fails.
 */
suspend fun getWatchScreenshotBytes(libPebble: LibPebble): ByteArray? {
    val watch = libPebble.watches.value
        .filterIsInstance<ConnectedPebbleDevice>()
        .firstOrNull()
    if (watch is ConnectedPebble.Screenshot) {
        val bitmap = watch.takeScreenshot() ?: return null
        return imageBitmapToPngBytes(bitmap)
    }
    return null
}
