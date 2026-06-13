package io.rebble.libpebblecommon.connection.bt

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.flow.Flow

actual fun nativeBluetoothStateFlow(appContext: AppContext): Flow<BluetoothState>? = null
