package io.rebble.libpebblecommon.connection.bt.ble.transport

import com.juul.kable.Peripheral
import com.juul.kable.State
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectionFailureReason
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.transport.impl.kableGattConnector
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

//expect fun libpebbleGattConnector(scannedPebbleDevice: ScannedPebbleDevice, appContext: AppContext): GattConnector

fun gattConnector(
    identifier: PebbleBleIdentifier,
    name: String,
    appContext: AppContext,
    scope: ConnectionCoroutineScope,
): GattConnector?
// = libpebbleGattConnector(scannedPebbleDevice, appContext)
        = kableGattConnector(identifier = identifier, scope = scope, name = name)

sealed class GattConnectionResult {
    data class Success(val client: ConnectedGattClient) : GattConnectionResult()
    data class Failure(val reason: ConnectionFailureReason) : GattConnectionResult()
}

interface GattConnector : AutoCloseable {
    suspend fun connect(): GattConnectionResult
    suspend fun disconnect()
    val disconnected: Deferred<ConnectionFailureReason>
}

enum class GattWriteType {
    WithResponse,
    NoResponse,
}

interface ConnectedGattClient : AutoCloseable {
    suspend fun discoverServices(): Boolean
    fun subscribeToCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        onSubscription: (suspend () -> Unit)? = null,
    ): Flow<ByteArray>?

    suspend fun isBonded(): Boolean // TODO doesn't belong in here
    suspend fun writeCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        value: ByteArray,
        writeType: GattWriteType
    ): Boolean

    suspend fun readCharacteristic(serviceUuid: Uuid, characteristicUuid: Uuid): ByteArray?
    val services: List<GattService>?
    suspend fun requestMtu(mtu: Int): Int
    suspend fun getMtu(): Int
    suspend fun refreshServicesNative(): Boolean
}
