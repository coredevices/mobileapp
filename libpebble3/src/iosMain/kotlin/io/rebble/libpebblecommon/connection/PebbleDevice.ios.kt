package io.rebble.libpebblecommon.connection

import kotlin.uuid.Uuid

// mac address on android, uuid on ios etc
public actual data class PebbleBleIdentifier(
    val uuid: Uuid,
) : PebbleIdentifier {
    actual override val asString: String = uuid.toString()
}

internal actual fun String.asPebbleBleIdentifier(): PebbleBleIdentifier {
    return PebbleBleIdentifier(Uuid.parse(this))
}

internal actual class PebbleBtClassicIdentifier internal constructor() : PebbleIdentifier {
    actual override val asString: String
        get() = throw UnsupportedOperationException("BT Classic not supported on iOS")
}

internal actual fun String.asPebbleBtClassicIdentifier(): PebbleBtClassicIdentifier {
    throw UnsupportedOperationException("BT Classic not supported on iOS")
}
