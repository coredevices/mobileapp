package io.rebble.libpebblecommon.connection.bt

import com.juul.kable.Bluetooth
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
    // Whether BLE scanning prerequisites are met (Kable availability). On Android <12 this
    // is false when location services are off even if `state` is Enabled.
    val scanningAvailable: StateFlow<Boolean>
}

enum class BluetoothState {
    Enabled,
    Disabled,
    ;

    fun enabled(): Boolean = this == Enabled
}

expect fun nativeBluetoothStateFlow(appContext: AppContext): Flow<BluetoothState>?

class RealBluetoothStateProvider(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val appContext: AppContext,
) : BluetoothStateProvider {
    private val _state = MutableStateFlow(BluetoothState.Disabled)
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()

    private val _scanningAvailable = MutableStateFlow(false)
    override val scanningAvailable: StateFlow<Boolean> = _scanningAvailable.asStateFlow()

    override fun init() {
        val nativeFlow = nativeBluetoothStateFlow(appContext)
        libPebbleCoroutineScope.launch {
            Bluetooth.availability.collect {
                val available = it is Bluetooth.Availability.Available
                _scanningAvailable.value = available
                if (nativeFlow == null) {
                    _state.value = if (available) BluetoothState.Enabled else BluetoothState.Disabled
                }
            }
        }
        if (nativeFlow != null) {
            libPebbleCoroutineScope.launch {
                nativeFlow.collect { _state.value = it }
            }
        }
    }
}