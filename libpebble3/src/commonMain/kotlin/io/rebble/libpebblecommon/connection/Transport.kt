package io.rebble.libpebblecommon.connection

interface PebbleIdentifier {
    val asString: String
}

// mac address on android, uuid on ios etc
expect class PebbleBleIdentifier : PebbleIdentifier {
    override val asString: String
}

expect fun String.asPebbleBleIdentifier(): PebbleBleIdentifier

// Bluetooth Classic identifier — Android-only in practice (iOS Pebble has no BT Classic).
// The actual on Android wraps a MAC address; iOS/JVM are stubs that must compile but are
// unreachable at runtime.
expect class PebbleBtClassicIdentifier : PebbleIdentifier {
    override val asString: String
}

expect fun String.asPebbleBtClassicIdentifier(): PebbleBtClassicIdentifier

data class PebbleSocketIdentifier(val address: String) : PebbleIdentifier {
    override val asString: String = address
}
