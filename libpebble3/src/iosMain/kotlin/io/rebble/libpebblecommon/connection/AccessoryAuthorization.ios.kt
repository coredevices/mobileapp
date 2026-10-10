package io.rebble.libpebblecommon.connection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

private val authorizedAccessories = MutableStateFlow<Set<PebbleBleIdentifier>>(emptySet())
actual fun accessoryAuthorizationFlow(): StateFlow<Set<PebbleBleIdentifier>> = authorizedAccessories.asStateFlow()

// Called by the native live ASAccessorySession observer for the current authorized
// set, including removals/invalidation. Never populated from persisted settings.
fun updateIosAccessoryAuthorizations(identifiers: List<String>) {
    authorizedAccessories.value = identifiers.mapNotNull { text ->
        runCatching { PebbleBleIdentifier(uuid = Uuid.parse(text)) }.getOrNull()
    }.toSet()
}

actual fun accessoryAuthorizationRequired(): Boolean {
    val version = UIDevice.currentDevice.systemVersion.split('.').mapNotNull { it.toIntOrNull() }
    val available = version.firstOrNull()?.let { it > 26 || it == 26 && (version.getOrNull(1) ?: 0) >= 5 } == true
    val supports = NSBundle.mainBundle.infoDictionary?.get("NSAccessorySetupKitSupports") as? List<*>
    val enabled = NSBundle.mainBundle.infoDictionary?.get("PebbleNotificationsEnabled") as? Boolean == true
    return enabled && available && supports?.contains("Bluetooth") == true
}
