package io.rebble.libpebblecommon.connection.bt.classic.transport

import io.rebble.libpebblecommon.connection.PebbleScanResult
import kotlinx.coroutines.flow.Flow

internal interface ClassicScanner {
    fun scan(): Flow<PebbleScanResult>
}
