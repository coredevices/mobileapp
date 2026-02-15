package io.rebble.libpebblecommon.connection.bt.ble.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.asPebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.CHARACTERISTIC_CONFIGURATION_DESCRIPTOR
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.FAKE_SERVICE_UUID
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.META_CHARACTERISTIC_SERVER
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PPOGATT_DEVICE_CHARACTERISTIC_SERVER
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PPOGATT_DEVICE_SERVICE_UUID_SERVER
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.web.withTimeoutOr
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

private fun getService(appContext: AppContext): BluetoothManager? =
    appContext.context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

actual fun openGattServer(appContext: AppContext, bleConfigFlow: BleConfigFlow, libPebbleCoroutineScope: LibPebbleCoroutineScope): GattServer? {
    return try {
        val callback = GattServerCallback(bleConfigFlow)
        getService(appContext)?.openGattServer(appContext.context, callback)?.let {
            callback.server = it
            io.rebble.libpebblecommon.connection.bt.ble.transport.GattServer(it, callback)
        }
    } catch (e: SecurityException) {
        Logger.d("error opening gatt server", e)
        null
    }
}

data class RegisteredDevice(
    val dataChannel: SendChannel<ByteArray>,
    val device: BluetoothDevice,
    val notificationsEnabled: Boolean,
    val writing: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val mutex: Mutex = Mutex(),
)

class GattServerCallback(
    private val bleConfigFlow: BleConfigFlow,
) : BluetoothGattServerCallback() {
    private val logger = Logger.withTag("GattServerCallback")

    //    private val _connectionState = MutableStateFlow<ServerConnectionstateChanged?>(null)
//    val connectionState = _connectionState.asSharedFlow()
    val registeredDevices: MutableMap<String, RegisteredDevice> = mutableMapOf()
    var server: BluetoothGattServer? = null

    private fun verboseLog(message: () -> String) {
        if (bleConfigFlow.value.verbosePpogLogging) {
            logger.v(message = message)
        }
    }

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        logger.d("onConnectionStateChange: ${device.address} = $newState")
//        _connectionState.tryEmit(
//            ServerConnectionstateChanged(
//                deviceId = device.address,
//                connectionState = newState,
//            )
//        )
    }

    private val _serviceAdded = MutableSharedFlow<ServerServiceAdded?>()
    val serviceAdded = _serviceAdded.asSharedFlow()

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        logger.d("onServiceAdded: ${service.uuid}")
        runBlocking {
            _serviceAdded.emit(ServerServiceAdded(service.uuid.asUuid()))
        }
    }

    private val _characteristicReadRequest = MutableStateFlow<RawCharacteristicReadRequest?>(null)
    val characteristicReadRequest = _characteristicReadRequest.asSharedFlow()

    data class RawCharacteristicReadRequest(
        val device: BluetoothDevice,
        val requestId: Int,
        val offset: Int,
        val characteristic: BluetoothGattCharacteristic,
        // TODO Hack to make it emit again while using a StateFlow, because MutableSharedFlow is not
        //  working for some reason
        val uuidHack: Uuid = Uuid.random(),
    )

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
    ) {
        logger.d("onCharacteristicReadRequest: ${characteristic.uuid}")
        _characteristicReadRequest.tryEmit(
            RawCharacteristicReadRequest(device, requestId, offset, characteristic)
        )
    }

//    private val _characteristicWriteRequest =
//        MutableStateFlow<ServerCharacteristicWriteRequest?>(null)
//    val characteristicWriteRequest = _characteristicWriteRequest.asSharedFlow()

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
    ) {
//        Logger.d("onCharacteristicWriteRequest: ${device.address} / ${characteristic.uuid}: ${value.joinToString()}")
        val registeredDevice = registeredDevices[device.address]
        if (registeredDevice == null) {
            logger.e("onCharacteristicWriteRequest couldn't find registered device: ${device.address}")
            return
        }
        val result = registeredDevice.dataChannel.trySend(value)
        if (result.isFailure) {
            logger.e("onCharacteristicWriteRequest error writing to channel: $result")
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?,
    ) {
        verboseLog { "onDescriptorWriteRequest: ${device.address} / ${descriptor.characteristic.uuid}" }
        val registeredDevice = registeredDevices[device.address]
        if (registeredDevice == null) {
            logger.e("onDescriptorWriteRequest device not registered!")
            return
        }
        val gattServer = server
        if (gattServer == null) {
            logger.e("onDescriptorWriteRequest no server!!")
            return
        }
        if (!gattServer.sendResponse(device, requestId, GATT_SUCCESS, offset, value)) {
            logger.e("onDescriptorWriteRequest failed to respond")
            return
        }
        registeredDevices[device.address] = registeredDevice.copy(notificationsEnabled = true)
//        logger.d("/onDescriptorWriteRequest")
    }

    override fun onNotificationSent(device: BluetoothDevice, status: Int) {
//        logger.d("onNotificationSent: ${device.address}")
        val device = registeredDevices[device.address]
        if (device == null) {
            logger.e("onNotificationSent device not registered!")
            return
        }
        device.writing.value = false
    }
}

actual class GattServer(
    val server: BluetoothGattServer,
    val callback: GattServerCallback,
    val cbTimeout: Long = 8000,
) : BluetoothGattServerCallback() {
    private val logger = Logger.withTag("GattServer")

    actual val characteristicReadRequest = callback.characteristicReadRequest.filterNotNull().map {
        ServerCharacteristicReadRequest(
            deviceId = it.device.address.asPebbleBleIdentifier(),
            uuid = it.characteristic.uuid.asUuid(),
            respond = { bytes ->
                try {
                    server.sendResponse(
                        it.device,
                        it.requestId,
                        GATT_SUCCESS,
                        it.offset,
                        bytes
                    )
                } catch (e: SecurityException) {
                    logger.d("error sending read response", e)
                    false
                }
            },
        )
    }

//    actual val connectionState = callback.connectionState.filterNotNull()

    actual suspend fun closeServer() {
        try {
            server.clearServices()
        } catch (e: SecurityException) {
            logger.d("error clearing gatt services", e)
        }
        try {
            server.close()
        } catch (e: SecurityException) {
            logger.d("error closing gatt server", e)
        }
    }

    actual suspend fun addServices() {
        logger.d("addServices")
        addService(
            PPOGATT_DEVICE_SERVICE_UUID_SERVER, listOf(
                BluetoothGattCharacteristic(
                    META_CHARACTERISTIC_SERVER.toJavaUuid(),
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
                ),
                BluetoothGattCharacteristic(
                    PPOGATT_DEVICE_CHARACTERISTIC_SERVER.toJavaUuid(),
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
                ).apply {
                    addDescriptor(
                        BluetoothGattDescriptor(
                            CHARACTERISTIC_CONFIGURATION_DESCRIPTOR.toJavaUuid(),
                            BluetoothGattDescriptor.PERMISSION_WRITE,
                        )
                    )
                },
            )
        )
        addService(
            FAKE_SERVICE_UUID, listOf(
                BluetoothGattCharacteristic(
                    FAKE_SERVICE_UUID.toJavaUuid(),
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
                ),
            )
        )
        logger.d("/addServices")
    }

    private suspend fun addService(
        serviceUuid: Uuid,
        characteristics: List<BluetoothGattCharacteristic>,
    ) {
        logger.d("addService: $serviceUuid")
        val service = BluetoothGattService(serviceUuid.toJavaUuid(), SERVICE_TYPE_PRIMARY)
        characteristics.forEach { service.addCharacteristic(it) }
        try {
            callback.serviceAdded.onSubscription {
                server.addService(service)
            }.first { service.uuid.asUuid() == serviceUuid }
        } catch (e: SecurityException) {
            logger.d("error adding gatt service ${service.uuid}", e)
        }
    }

    actual fun registerDevice(
        identifier: PebbleBleIdentifier,
        sendChannel: SendChannel<ByteArray>
    ) {
        logger.d("registerDevice: $identifier")
        @Suppress("DEPRECATION")
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothDevice = adapter.getRemoteDevice(identifier.macAddress)
        callback.registeredDevices[identifier.macAddress] =
            RegisteredDevice(
                dataChannel = sendChannel,
                device = bluetoothDevice,
                notificationsEnabled = false,
            )
    }

    actual fun unregisterDevice(identifier: PebbleBleIdentifier) {
        callback.registeredDevices.remove(identifier.macAddress)
    }

    @SuppressLint("MissingPermission")
    actual suspend fun sendData(
        identifier: PebbleBleIdentifier,
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        data: ByteArray,
    ): Boolean {
        val registeredDevice = callback.registeredDevices[identifier.macAddress]
        if (registeredDevice == null) {
            logger.e("sendData: couldn't find registered device: $identifier")
            return false
        }
        val service = server.getService(serviceUuid.toJavaUuid())
        if (service == null) {
            logger.e("sendData: couldn't find service")
            return false
        }
        val characteristic = service.getCharacteristic(characteristicUuid.toJavaUuid())
        if (characteristic == null) {
            logger.e("sendData: couldn't find characteristic")
            return false
        }
        return withTimeoutOr(
            timeout = cbTimeout.milliseconds,
            block = {
                registeredDevice.mutex.withLock {
                    registeredDevice.writing.value = true
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val writeRes = server.notifyCharacteristicChanged(
                            registeredDevice.device,
                            characteristic,
                            false,
                            data
                        )
                        if (writeRes != BluetoothStatusCodes.SUCCESS) {
                            logger.e("couldn't notify data characteristic: $writeRes")
                            return@withLock false
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = data
                        @Suppress("DEPRECATION")
                        if (!server.notifyCharacteristicChanged(
                                registeredDevice.device,
                                characteristic,
                                false
                            )
                        ) {
                            logger.e("couldn't notify data characteristic")
                            return@withLock false
                        }
                    }
                    registeredDevice.writing.first { writing -> !writing }
                    true
                }
            },
            onTimeout = {
                logger.w { "Timeout sending data to $identifier" }
                false
            })
    }

    actual fun wasRestoredWithSubscribedCentral(): Boolean {
        return false
    }

    actual fun initServer() {
    }
}

private fun UUID.asUuid(): Uuid = Uuid.parse(toString())
