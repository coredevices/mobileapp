package io.rebble.libpebblecommon.connection.bt.classic.pebble

import io.rebble.libpebblecommon.connection.ConnectionFailureReason
import io.rebble.libpebblecommon.connection.KnownWatchProperties
import io.rebble.libpebblecommon.connection.PebbleConnectionResult
import io.rebble.libpebblecommon.connection.TransportConnector
import kotlinx.coroutines.Deferred

class PebbleBtClassic(
    private val connector: BtClassicConnector,
) : TransportConnector {
    override suspend fun connect(knownWatchProperties: KnownWatchProperties?, lastError: ConnectionFailureReason?): PebbleConnectionResult {
        val result = connector.connect()
        return when (result) {
            ClassicConnectionResult.Success -> PebbleConnectionResult.Success(null)
            ClassicConnectionResult.Failure -> PebbleConnectionResult.Failed(ConnectionFailureReason.ClassicConnectionFailed)
        }
    }

    override suspend fun disconnect() {
        connector.disconnect()
    }

    override val disconnected: Deferred<ConnectionFailureReason> = connector.disconnected
}

enum class ClassicConnectionResult {
    Success,
    Failure,
}

interface BtClassicConnector {
    suspend fun connect(): ClassicConnectionResult
    suspend fun disconnect()
    val disconnected: Deferred<ConnectionFailureReason>
}