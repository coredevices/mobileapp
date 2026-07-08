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

enum class SendResult {
    Success,
    Failed,
    RestartRequired,
}

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
    ): SendResult
    fun wasRestoredWithSubscribedCentral(): Boolean
    fun initServer()
}

class GattServerManager(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val bluetoothStateProvider: BluetoothStateProvider,
    private val appContext: AppContext,
    private val bleConfigFlow: BleConfigFlow,
    private val blePlatformConfig: BlePlatformConfig,
) {
    private val serverMutex = Mutex()
    private val logger = Logger.withTag("GattServerManager")
    private var gattServer: GattServer? = null
    private val registeredDevices = mutableSetOf<PebbleBleIdentifier>()

    fun init() {
        // The GATT server is only needed when a watch uses forward PPoG (phone
        // hosts the PPoG service). Reversed-PPoG connections skip
        // registerDevice() and don't need the server open. We now open the
        // server lazily in registerDevice() and close it in unregisterDevice()
        // when the last registered device is gone, so a reversed-only setup
        // never advertises the forward-PPoG service at all.
        libPebbleCoroutineScope.launch {
            bluetoothStateProvider.state.collect { bluetooth ->
                logger.d("Bluetooth state: $bluetooth")
                if (bluetooth == BluetoothState.Disabled &&
                    blePlatformConfig.closeGattServerWhenBtDisabled) {
                    close()
                }
            }
        }
    }

    suspend fun registerDevice(
        identifier: PebbleBleIdentifier,
        sendChannel: SendChannel<ByteArray>
    ): Boolean {
        if (gattServer == null && bluetoothStateProvider.state.value == BluetoothState.Enabled) {
            openIfNeeded()
        }
        val gs = gattServer
        if (gs != null) {
            gs.registerDevice(identifier, sendChannel)
            registeredDevices.add(identifier)
            return true
        } else {
            return false
        }
    }

    fun unregisterDevice(identifier: PebbleBleIdentifier) {
        gattServer?.unregisterDevice(identifier)
        registeredDevices.remove(identifier)
        if (registeredDevices.isEmpty() && gattServer != null) {
            libPebbleCoroutineScope.launch { close() }
        }
    }

    suspend fun sendData(
        identifier: PebbleBleIdentifier,
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        data: ByteArray,
    ): Boolean {
        val result = gattServer?.sendData(identifier, serviceUuid, characteristicUuid, data)
        return when (result) {
            SendResult.Success -> true
            SendResult.Failed, null -> false
            SendResult.RestartRequired -> {
                close()
                false
            }
        }
    }

    fun wasRestoredWithSubscribedCentral(): Boolean {
        return gattServer?.wasRestoredWithSubscribedCentral() ?: false
    }

    private suspend fun openIfNeeded() {
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