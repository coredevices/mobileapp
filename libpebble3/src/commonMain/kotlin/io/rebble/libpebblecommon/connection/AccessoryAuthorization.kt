package io.rebble.libpebblecommon.connection

import kotlinx.coroutines.flow.StateFlow

// Live per-accessory admission, separate from radio state and general TCC.
// Non-iOS targets always expose an empty set. This never grants secure readiness.
expect fun accessoryAuthorizationFlow(): StateFlow<Set<PebbleBleIdentifier>>

expect fun accessoryAuthorizationRequired(): Boolean

fun admitsBluetoothConnection(identifier: PebbleIdentifier, bluetoothEnabled: Boolean,
                              authorized: Set<PebbleBleIdentifier>): Boolean {
    if (!identifier.requiresBluetooth()) return true
    if (accessoryAuthorizationRequired()) {
        return identifier is PebbleBleIdentifier && identifier in authorized
    }
    return bluetoothEnabled
}
