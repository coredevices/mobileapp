package io.rebble.libpebblecommon.connection

// mac address on android, uuid on ios etc
actual class PebbleBleIdentifier(id: String) : PebbleIdentifier {
    actual override val asString: String = id
}

actual fun String.asPebbleBleIdentifier(): PebbleBleIdentifier {
    return PebbleBleIdentifier(this)
}

actual class PebbleBtClassicIdentifier internal constructor() : PebbleIdentifier {
    actual override val asString: String
        get() = throw UnsupportedOperationException("BT Classic not supported on JVM")
}

actual fun String.asPebbleBtClassicIdentifier(): PebbleBtClassicIdentifier {
    throw UnsupportedOperationException("BT Classic not supported on JVM")
}