package coredevices.pebble

import android.os.Build
import coredevices.util.Permission
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PebbleAndroidDelegate(
    private val libPebble: LibPebble,
) {
    fun initPostPermissions() {
        libPebble.doStuffAfterPermissionsGranted()
    }

    /**
     * We only expect the notification permission to be granted after first connecting
     * to a watch (via companion device manager).
     */
    val requiredPermissions: Flow<Set<Permission>> = libPebble.watches.map { watches ->
        if (watches.any { it is KnownPebbleDevice }) {
            BASE_PERMISSIONS + AFTER_FIRST_CONNECTION_PERMISSIONS
        } else {
            BASE_PERMISSIONS
        }
    }

    companion object {
        private val BASE_PERMISSIONS = setOf(
            Permission.Location,
            Permission.BackgroundLocation,
            Permission.Bluetooth,
            Permission.PostNotifications,
        )
        private val AFTER_FIRST_CONNECTION_PERMISSIONS = buildSet {
            add(Permission.ReadNotifications)
            add(Permission.ReadCallLog)
            add(Permission.Calendar)
            add(Permission.Contacts)
            add(Permission.ReadPhoneState)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(Permission.MediaRouting)
            }
        }
    }
}