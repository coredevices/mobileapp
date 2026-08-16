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
    suspend fun removeServices()
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
    private var servicesAdded = false

    fun init() {
        // Eagerly open the GATT server (create the platform manager, call
        // initServer) as soon as BT is enabled. On iOS the CBPeripheralManager
        // opts into state restoration via CBPeripheralManagerOptionRestoreIdentifierKey,
        // and Apple requires the manager to be constructed early — otherwise
        // willRestoreState never fires and pending BT events (subscribes,
        // writes from a paired watch) are silently dropped.
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
        if (bluetoothStateProvider.state.value != BluetoothState.Enabled) return false
        openIfNeeded()
        val gs = gattServer ?: return false
        gs.registerDevice(identifier, sendChannel)
        registeredDevices.add(identifier)
        serverMutex.withLock {
            if (!servicesAdded) {
                logger.d("adding forward-PPoG services for first registered device")
                gs.addServices()
                servicesAdded = true
            }
        }
        return true
    }

    fun unregisterDevice(identifier: PebbleBleIdentifier) {
        gattServer?.unregisterDevice(identifier)
        registeredDevices.remove(identifier)
        // Don't remove services - the churn seems to be breaking connectivity
//        if (registeredDevices.isEmpty()) {
//            libPebbleCoroutineScope.launch {
//                serverMutex.withLock {
//                    // Re-check under the lock — another registerDevice may have
//                    // slotted in between the map removal above and this coroutine.
//                    if (registeredDevices.isEmpty() && servicesAdded) {
//                        logger.d("removing forward-PPoG services (no registered devices)")
//                        gattServer?.removeServices()
//                        servicesAdded = false
//                    }
//                }
//            }
//        }
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
            // Note: addServices() intentionally NOT called here. Services are
            // added lazily by registerDevice() on the first forward-PPoG
            // device. See init() for the reasoning around eager
            // CBPeripheralManager construction on iOS.
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
            servicesAdded = false
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