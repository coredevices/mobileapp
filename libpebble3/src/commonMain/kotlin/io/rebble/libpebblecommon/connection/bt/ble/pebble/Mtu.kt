package io.rebble.libpebblecommon.connection.bt.ble.pebble

import co.touchlab.kermit.Logger
import com.juul.kable.GattStatusException
import com.oldguy.common.getUShortAt
import io.rebble.libpebblecommon.connection.ConnectionFailureReason
import io.rebble.libpebblecommon.connection.PebbleConnectionResult
import io.rebble.libpebblecommon.connection.bt.ble.BlePlatformConfig
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.DEFAULT_MTU
import io.rebble.libpebblecommon.connection.bt.ble.transport.ConnectedGattClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ArrayIndexOutOfBoundsException

class Mtu(private val blePlatformConfig: BlePlatformConfig) {
    private val logger = Logger.withTag("Mtu")
    private val _mtu = MutableStateFlow(DEFAULT_MTU)
    val mtu: StateFlow<Int> = _mtu.asStateFlow()

    suspend fun update(gattClient: ConnectedGattClient, mtu: Int): PebbleConnectionResult {
        if (blePlatformConfig.useNativeMtu) {
            try {
                _mtu.value = gattClient.requestMtu(mtu)
            } catch (e: GattStatusException) {
                logger.w(e) { "error setting mtu: ${e.status}" }
                return PebbleConnectionResult.Failed(ConnectionFailureReason.MtuGattError)
            }
        }
        _mtu.value = gattClient.getMtu()
        return PebbleConnectionResult.Success(null)
    }
}

fun ByteArray.toUShortLittleEndian(): UShort? = try {
    toUByteArray().getUShortAt(0, littleEndian = true)
} catch (e: ArrayIndexOutOfBoundsException) {
    Logger.w("toUShortLittleEndian", e)
    null
}