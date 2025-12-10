package coredevices.pebble

import coredevices.util.Permission

class PebbleIosDelegate {
    fun requiredPermissions(): Set<Permission> = setOf(
        Permission.Calendar,
        Permission.PostNotifications,
        Permission.Bluetooth,
        Permission.Location,
        Permission.BackgroundLocation,
    )
}