package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.database.entity.TransportType
import io.rebble.libpebblecommon.database.entity.type

public interface PebbleIdentifier {
    public val asString: String
}

// mac address on android, uuid on ios etc
public expect class PebbleBleIdentifier : PebbleIdentifier {
    override val asString: String
}

internal expect fun String.asPebbleBleIdentifier(): PebbleBleIdentifier

// Bluetooth Classic identifier — Android-only in practice (iOS Pebble has no BT Classic).
// The actual on Android wraps a MAC address; iOS/JVM are stubs that must compile but are
// unreachable at runtime.
internal expect class PebbleBtClassicIdentifier : PebbleIdentifier {
    override val asString: String
}

internal expect fun String.asPebbleBtClassicIdentifier(): PebbleBtClassicIdentifier

public data class PebbleSocketIdentifier(val address: String) : PebbleIdentifier {
    override val asString: String = address
}

// These gate on TransportType rather than `is` checks so the `when`s are exhaustive over the enum
// (a new transport forces every one to be updated). PebbleIdentifier can't be a sealed interface —
// expect/actual identifiers can't extend one — so an exhaustive `when (this)` isn't available.

/** Socket watches are emulators reached over TCP, so the Bluetooth adapter is irrelevant to them. */
internal fun PebbleIdentifier.requiresBluetooth(): Boolean = when (type()) {
    TransportType.BluetoothLe, TransportType.BluetoothClassic -> true
    TransportType.Socket -> false
}

/** The emulator has no recovery firmware slot, so it must not be treated as missing one. */
internal fun PebbleIdentifier.canHaveRecoveryFirmware(): Boolean = when (type()) {
    TransportType.BluetoothLe, TransportType.BluetoothClassic -> true
    TransportType.Socket -> false
}

/**
 * Whether the watch sends a phone-app-version request that negotiation should wait for. The
 * emulator only sends it once at boot with nobody attached, so the phone must not wait.
 */
internal fun PebbleIdentifier.sendsAppVersionRequest(): Boolean = when (type()) {
    TransportType.BluetoothLe, TransportType.BluetoothClassic -> true
    TransportType.Socket -> false
}

internal fun PebbleSocketIdentifier.displayName(): String =
    "QEMU ${address.substringBefore(':').substringAfterLast('.')}"
