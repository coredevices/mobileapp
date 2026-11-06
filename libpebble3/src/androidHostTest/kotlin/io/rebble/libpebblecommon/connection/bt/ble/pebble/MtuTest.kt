package io.rebble.libpebblecommon.connection.bt.ble.pebble

import io.rebble.libpebblecommon.connection.PebbleConnectionResult
import io.rebble.libpebblecommon.connection.bt.ble.BlePlatformConfig
import io.rebble.libpebblecommon.connection.bt.ble.transport.ConnectedGattClient
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattService
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattWriteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class MtuTest {
    private class FakeGattClient(
        private val negotiatedMtu: Int,
        private val reportedMtu: Int,
    ) : ConnectedGattClient {
        var requestedMtu: Int? = null

        override suspend fun requestMtu(mtu: Int): Int {
            requestedMtu = mtu
            return negotiatedMtu
        }

        override suspend fun getMtu(): Int = reportedMtu

        override suspend fun discoverServices() = true
        override fun subscribeToCharacteristic(
            serviceUuid: Uuid,
            characteristicUuid: Uuid,
            onSubscription: (suspend () -> Unit)?,
        ): Flow<ByteArray>? = null

        override suspend fun isBonded() = true
        override suspend fun writeCharacteristic(
            serviceUuid: Uuid,
            characteristicUuid: Uuid,
            value: ByteArray,
            writeType: GattWriteType,
        ) = true

        override suspend fun readCharacteristic(serviceUuid: Uuid, characteristicUuid: Uuid): ByteArray? = null
        override val services: List<GattService>? = null
        override suspend fun refreshServicesNative() = true
        override fun close() {}
    }

    @Test
    fun nativeMtuUsesTheNegotiatedValue() = runTest {
        val mtu = Mtu(BlePlatformConfig(useNativeMtu = true))
        // The library's cached MTU can still be the default when the request returns.
        val client = FakeGattClient(negotiatedMtu = 247, reportedMtu = 23)

        assertIs<PebbleConnectionResult.Success>(mtu.update(client, 339))

        assertEquals(339, client.requestedMtu)
        assertEquals(247, mtu.mtu.value)
    }

    @Test
    fun nonNativeMtuUsesTheReportedValue() = runTest {
        val mtu = Mtu(BlePlatformConfig(useNativeMtu = false))
        val client = FakeGattClient(negotiatedMtu = 247, reportedMtu = 185)

        assertIs<PebbleConnectionResult.Success>(mtu.update(client, 339))

        assertEquals(null, client.requestedMtu)
        assertEquals(185, mtu.mtu.value)
    }
}
