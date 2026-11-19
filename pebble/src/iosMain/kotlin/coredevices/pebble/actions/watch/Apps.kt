package coredevices.pebble.actions.watch

import coredevices.pebble.watchesOrEmpty
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Launches an app or watchface on the connected watch by UUID.
 * Only launches if the watch is connected; does not sync or install.
 */
fun launchAppByUuid(libPebble: LibPebble, uuidString: String) {
    GlobalScope.launch {
        val uuid = runCatching { Uuid.parse(uuidString) }.getOrNull() ?: return@launch
        if (libPebble.watchesOrEmpty().filterIsInstance<ConnectedPebbleDevice>().firstOrNull() == null) return@launch
        libPebble.launchApp(uuid)
    }
}
