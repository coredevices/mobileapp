package io.rebble.libpebblecommon.connection.bt.ble.transport

import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

actual fun openGattServer(appContext: AppContext, bleConfigFlow: BleConfigFlow, libPebbleCoroutineScope: LibPebbleCoroutineScope): GattServer? {
    TODO("Not yet implemented")
}

actual class GattServer {
    actual suspend fun addServices() {
    }

    actual suspend fun closeServer() {
    }

    actual val characteristicReadRequest: Flow<ServerCharacteristicReadRequest>
        get() = TODO("Not yet implemented")

    actual fun registerDevice(
        identifier: PebbleBleIdentifier,
        sendChannel: SendChannel<ByteArray>
    ) {
    }

    actual fun unregisterDevice(identifier: PebbleBleIdentifier) {
    }

    actual suspend fun sendData(
        identifier: PebbleBleIdentifier,
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        data: ByteArray
    ): Boolean {
        TODO("Not yet implemented")
    }

    actual fun wasRestoredWithSubscribedCentral(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun initServer() {
    }
}