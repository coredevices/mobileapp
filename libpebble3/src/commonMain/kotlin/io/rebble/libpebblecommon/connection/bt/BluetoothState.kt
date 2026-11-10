package io.rebble.libpebblecommon.connection.bt

import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface BluetoothStateProvider {
    fun init()
    val state: StateFlow<BluetoothState>
    // On Android <12 this is false when location services are off, even if `state` is Enabled.
    fun scanningAvailable(): Boolean
}

enum class BluetoothState {
    Enabled,
    Disabled,
    ;

    fun enabled(): Boolean = this == Enabled
}

expect fun nativeBluetoothStateFlow(appContext: AppContext): Flow<BluetoothState>

expect fun locationServicesBlockScanning(appContext: AppContext): Boolean

class RealBluetoothStateProvider(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val appContext: AppContext,
) : BluetoothStateProvider {
    private val _state = MutableStateFlow(BluetoothState.Disabled)
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()

    override fun scanningAvailable(): Boolean = !locationServicesBlockScanning(appContext)

    override fun init() {
        libPebbleCoroutineScope.launch {
            nativeBluetoothStateFlow(appContext).collect { _state.value = it }
        }
    }
}
