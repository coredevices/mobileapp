package io.rebble.libpebblecommon.connection.bt.classic.transport

import io.rebble.libpebblecommon.connection.PebbleScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class IosClassicScanner : ClassicScanner {
    override fun scan(): Flow<PebbleScanResult> = emptyFlow()
}
