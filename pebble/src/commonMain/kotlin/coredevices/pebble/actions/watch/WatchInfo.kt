package coredevices.pebble.actions.watch

import coredevices.util.imageBitmapToPngBase64
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble

/**
 * Returns the battery level (0–100) of the connected watch, or null if no watch is connected
 * or battery is unknown.
 */
fun getWatchBatteryLevel(libPebble: LibPebble): Int? =
    libPebble.watches.value
        .filterIsInstance<ConnectedPebble.Battery>()
        .firstOrNull()?.batteryLevel

/**
 * True if a watch is fully connected (ConnectedPebbleDevice).
 */
fun isWatchConnected(libPebble: LibPebble): Boolean =
    libPebble.watches.value.filterIsInstance<ConnectedPebbleDevice>().firstOrNull() != null

/**
 * Name of the connected watch, or null if none.
 */
fun getConnectedWatchName(libPebble: LibPebble): String? =
    libPebble.watches.value.filterIsInstance<ConnectedPebbleDevice>().firstOrNull()?.name

/** Enables or disables motion backlight (backlight on wrist raise) on the watch. */
fun setBacklightMotion(libPebble: LibPebble, enabled: Boolean) {
    libPebble.setWatchPref(
        io.rebble.libpebblecommon.database.dao.WatchPreference(
            io.rebble.libpebblecommon.database.entity.BoolWatchPref.BacklightMotion,
            enabled,
        ),
    )
}

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


