package io.rebble.libpebblecommon.connection.bt.classic.transport

import io.rebble.libpebblecommon.connection.PebbleScanResult
import kotlinx.coroutines.flow.Flow

interface ClassicScanner {
    fun scan(): Flow<PebbleScanResult>
}
