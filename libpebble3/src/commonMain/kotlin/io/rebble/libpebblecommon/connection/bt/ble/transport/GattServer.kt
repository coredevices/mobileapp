package io.rebble.libpebblecommon.connection.bt.ble.transport

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.BluetoothState
import io.rebble.libpebblecommon.connection.bt.BluetoothStateProvider
import io.rebble.libpebblecommon.connection.bt.ble.BlePlatformConfig
import io.rebble.libpebblecommon.connection.bt.ble.pebble.SERVER_META_RESPONSE
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

expect fun openGattServer(appContext: AppContext, bleConfigFlow: BleConfigFlow, libPebbleCoroutineScope: LibPebbleCoroutineScope): GattServer?

expect class GattServer {
    suspend fun addServices()
    suspend fun closeServer()
    val characteristicReadRequest: Flow<ServerCharacteristicReadRequest>

    //    val connectionState: Flow<ServerConnectionstateChanged>
    fun registerDevice(identifier: PebbleBleIdentifier, sendChannel: SendChannel<ByteArray>)
    fun unregisterDevice(identifier: PebbleBleIdentifier)
    suspend fun sendData(
        identifier: PebbleBleIdentifier, serviceUuid: Uuid,
        characteristicUuid: Uuid, data: ByteArray
    ): Boolean
    fun wasRestoredWithSubscribedCentral(): Boolean
    fun initServer()
}

class GattServerManager(
    private val config: BleConfigFlow,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val bluetoothStateProvider: BluetoothStateProvider,
    private val appContext: AppContext,
    private val bleConfigFlow: BleConfigFlow,
    private val blePlatformConfig: BlePlatformConfig,
) {
    private val serverMutex = Mutex()
    private val logger = Logger.withTag("GattServerManager")
    private var gattServer: GattServer? = null

    fun init() {
        libPebbleCoroutineScope.launch {
            bluetoothStateProvider.state.collect { bluetooth ->
                logger.d("Bluetooth state: $bluetooth")
                when (bluetooth) {
                    BluetoothState.Enabled -> openIfNeeded()
                    BluetoothState.Disabled -> {
                        if (blePlatformConfig.closeGattServerWhenBtDisabled) {
                            close()
                        }
                    }
                }
            }
        }
    }

    suspend fun registerDevice(
        identifier: PebbleBleIdentifier,
        sendChannel: SendChannel<ByteArray>
    ): Boolean {
        if (gattServer == null && bluetoothStateProvider.state.value == BluetoothState.Enabled) {
            logger.w("Trying to open gatt server to register device. This should only happen after bluetooth permission was just granted on android, during first use")
            openIfNeeded()
        }
        val gs = gattServer
        if (gs != null) {
            gs.registerDevice(identifier, sendChannel)
            return true
        } else {
            return false
        }
    }

    fun unregisterDevice(identifier: PebbleBleIdentifier) {
        gattServer?.unregisterDevice(identifier)
    }

    suspend fun sendData(
        identifier: PebbleBleIdentifier,
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        data: ByteArray,
    ): Boolean {
        return gattServer?.sendData(identifier, serviceUuid, characteristicUuid, data) ?: false
    }

    fun wasRestoredWithSubscribedCentral(): Boolean {
        return gattServer?.wasRestoredWithSubscribedCentral() ?: false
    }

    private suspend fun openIfNeeded() {
        if (config.value.reversedPPoG) {
            return
        }
        serverMutex.withLock {
            if (gattServer != null) return@withLock
            logger.d("open gatt server")
            gattServer = openGattServer(appContext, bleConfigFlow, libPebbleCoroutineScope)
            gattServer?.initServer()
            gattServer?.addServices()
            libPebbleCoroutineScope.launch {
                gattServer?.characteristicReadRequest?.collect {
                    logger.d("sending meta response")
                    it.respond(SERVER_META_RESPONSE)
                }
            }
        }
    }

    private suspend fun close() {
        serverMutex.withLock {
            logger.d("close gatt server")
            val gs = gattServer ?: return@withLock
            gs.closeServer()
            gattServer = null
        }
    }
}

data class ServerServiceAdded(val uuid: Uuid)
data class ServerConnectionstateChanged(
    val deviceId: PebbleBleIdentifier,
    val connectionState: Int
)

// Watch reading meta characteristic
data class ServerCharacteristicReadRequest(
    val deviceId: PebbleBleIdentifier,
    val uuid: Uuid,
    val respond: (ByteArray) -> Boolean
)

data class NotificationSent(val deviceId: PebbleBleIdentifier, val status: Int)

data class GattService(
    val uuid: Uuid,
    val characteristics: List<GattCharacteristic>
)

data class GattCharacteristic(
    val uuid: Uuid,
    val properties: Int,
    val permissions: Int,
    val descriptors: List<GattDescriptor>,
)

data class GattDescriptor(
    val uuid: Uuid,
    val permissions: Int,
)