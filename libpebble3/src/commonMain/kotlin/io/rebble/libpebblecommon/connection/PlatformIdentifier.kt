package io.rebble.libpebblecommon.connection

import com.juul.kable.Peripheral
import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.connection.bt.ble.BlePlatformConfig
import io.rebble.libpebblecommon.connection.bt.ble.transport.impl.peripheralFromIdentifier

sealed class PlatformIdentifier {
    data class BlePlatformIdentifier(val peripheral: Peripheral, val autoConnect: Boolean) : PlatformIdentifier()
    data class SocketPlatformIdentifier(val addr: String) : PlatformIdentifier()
    data class BtClassicPlatformIdentifier(val identifier: PebbleBtClassicIdentifier) : PlatformIdentifier()
}


interface CreatePlatformIdentifier {
    fun identifier(
        identifier: PebbleIdentifier,
        name: String,
        lastAttemptFailed: Boolean,
    ): PlatformIdentifier?
}

class RealCreatePlatformIdentifier(
    private val bleConfig: BleConfigFlow,
    private val blePlatformConfig: BlePlatformConfig,
) : CreatePlatformIdentifier {
    override fun identifier(
        identifier: PebbleIdentifier,
        name: String,
        lastAttemptFailed: Boolean,
    ): PlatformIdentifier? = when (identifier) {
        is PebbleBleIdentifier -> {
            val autoConnect = blePlatformConfig.supportsGattAutoConnect &&
                    lastAttemptFailed && bleConfig.value.autoConnectAfterFailure
            peripheralFromIdentifier(identifier, name, autoConnect)?.let {
                PlatformIdentifier.BlePlatformIdentifier(it, autoConnect)
            }
        }

        is PebbleBtClassicIdentifier -> PlatformIdentifier.BtClassicPlatformIdentifier(identifier)

        else -> error("unknown identifier type: $identifier")
    }
}
