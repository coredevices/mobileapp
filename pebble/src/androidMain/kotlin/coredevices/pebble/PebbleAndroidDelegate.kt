package coredevices.pebble

import android.os.Build
import coredevices.util.Permission
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
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
        val knownWatches = watches.filterIsInstance<KnownPebbleDevice>()
        if (knownWatches.isNotEmpty()) {
            buildSet {
                addAll(BASE_PERMISSIONS)
                addAll(AFTER_FIRST_CONNECTION_PERMISSIONS)
                if (mediaRoutingPermissionRequired(
                        Build.VERSION.SDK_INT,
                        knownWatches.map { it.capabilities },
                    )
                ) {
                    add(Permission.MediaRouting)
                }
            }
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
        }
    }
}

internal fun mediaRoutingPermissionRequired(
    sdkInt: Int,
    watchCapabilities: Iterable<Set<ProtocolCapsFlag>>,
): Boolean = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
        watchCapabilities.any { ProtocolCapsFlag.SupportsMusicOutputRouting in it }