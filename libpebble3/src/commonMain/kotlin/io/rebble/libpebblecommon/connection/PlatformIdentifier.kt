package io.rebble.libpebblecommon.connection

import com.juul.kable.Peripheral
import io.rebble.libpebblecommon.connection.bt.ble.transport.impl.peripheralFromIdentifier

sealed class PlatformIdentifier {
    class BlePlatformIdentifier(val peripheral: Peripheral) : PlatformIdentifier()
    class SocketPlatformIdentifier(val addr: String) : PlatformIdentifier()
    class BtClassicPlatformIdentifier(val identifier: PebbleBtClassicIdentifier) : PlatformIdentifier()
}


interface CreatePlatformIdentifier {
    fun identifier(identifier: PebbleIdentifier, name: String): PlatformIdentifier?
}

class RealCreatePlatformIdentifier : CreatePlatformIdentifier {
    override fun identifier(identifier: PebbleIdentifier, name: String): PlatformIdentifier? = when (identifier) {
        is PebbleBleIdentifier -> peripheralFromIdentifier(identifier, name)?.let {
            PlatformIdentifier.BlePlatformIdentifier(
                it
            )
        }

        is PebbleBtClassicIdentifier -> PlatformIdentifier.BtClassicPlatformIdentifier(identifier)

        else -> error("unknown identifier type: $identifier")
    }
}