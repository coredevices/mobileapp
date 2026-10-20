package coredevices.pebble

import coredevices.util.Permission
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

fun supportsGuidedPebbleEnrollment(): Boolean {
    val version = UIDevice.currentDevice.systemVersion.split('.').mapNotNull { it.toIntOrNull() }
    val available = version.firstOrNull()?.let { it > 26 || it == 26 && (version.getOrNull(1) ?: 0) >= 5 } == true
    val supports = NSBundle.mainBundle.infoDictionary?.get("NSAccessorySetupKitSupports") as? List<*>
    val enabled = NSBundle.mainBundle.infoDictionary?.get("PebbleNotificationsEnabled") as? Boolean == true
    return enabled && available && supports?.contains("Bluetooth") == true
}

fun notificationBridgeName(suffix: String): String =
    ((NSBundle.mainBundle.infoDictionary?.get("PebbleNotificationNamespace") as? String)
        ?: "coredevices.coreapp") + "." + suffix

fun notificationBridgeAppGroup(): String? =
    NSBundle.mainBundle.infoDictionary?.get("PebbleNotificationAppGroup") as? String

class PebbleIosDelegate {
    fun requiredPermissions(): Set<Permission> = setOf(
        Permission.Calendar,
        Permission.PostNotifications,
        Permission.Bluetooth,
        Permission.Location,
        Permission.BackgroundLocation,
    ).let { permissions ->
        // ASK owns each selected watch's access. General Bluetooth TCC is not
        // required for this path; do not claim it is granted or hide other warnings.
        if (supportsGuidedPebbleEnrollment()) permissions - Permission.Bluetooth else permissions
    }
}
