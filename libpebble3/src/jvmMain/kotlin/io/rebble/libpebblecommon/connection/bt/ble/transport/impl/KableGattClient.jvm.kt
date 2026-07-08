package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import com.juul.kable.Peripheral
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier

actual fun peripheralFromIdentifier(identifier: PebbleBleIdentifier, name: String): Peripheral? {
    TODO("Not yet implemented")
}

actual suspend fun Peripheral.requestMtuNative(mtu: Int): Int {
    return mtu
}

actual suspend fun Peripheral.refreshServicesNative(): Boolean = false