package io.rebble.libpebblecommon.connection

import kotlin.uuid.Uuid

// mac address on android, uuid on ios etc
actual data class PebbleBleIdentifier(
    val uuid: Uuid,
) : PebbleIdentifier {
    actual override val asString: String = uuid.toString()
}

actual fun String.asPebbleBleIdentifier(): PebbleBleIdentifier {
    return PebbleBleIdentifier(Uuid.parse(this))
}

actual class PebbleBtClassicIdentifier internal constructor() : PebbleIdentifier {
    actual override val asString: String
        get() = throw UnsupportedOperationException("BT Classic not supported on iOS")
}

actual fun String.asPebbleBtClassicIdentifier(): PebbleBtClassicIdentifier {
    throw UnsupportedOperationException("BT Classic not supported on iOS")
}
