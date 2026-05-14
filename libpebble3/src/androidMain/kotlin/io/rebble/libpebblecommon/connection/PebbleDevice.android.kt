package io.rebble.libpebblecommon.connection

import java.util.Locale

actual data class PebbleBleIdentifier private constructor(
    val macAddress: String,
) : PebbleIdentifier {
    actual override val asString: String = macAddress

    init {
        check(macAddress == macAddress.uppercase(Locale.US))
    }

    companion object {
        // Force address to always be uppercase (so we can safely compare it)
        operator fun invoke(macAddress: String): PebbleBleIdentifier {
            return PebbleBleIdentifier(macAddress.uppercase(Locale.US))
        }
    }
}

actual fun String.asPebbleBleIdentifier(): PebbleBleIdentifier {
    return PebbleBleIdentifier(this)
}

actual data class PebbleBtClassicIdentifier private constructor(
    val macAddress: String,
) : PebbleIdentifier {
    actual override val asString: String = macAddress

    init {
        check(macAddress == macAddress.uppercase(Locale.US))
    }

    companion object {
        operator fun invoke(macAddress: String): PebbleBtClassicIdentifier {
            return PebbleBtClassicIdentifier(macAddress.uppercase(Locale.US))
        }
    }
}

actual fun String.asPebbleBtClassicIdentifier(): PebbleBtClassicIdentifier {
    return PebbleBtClassicIdentifier(this)
}
