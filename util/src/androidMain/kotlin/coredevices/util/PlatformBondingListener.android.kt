package coredevices.util

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Composable
actual fun rememberPlatformBondingListener(): Flow<BondingEvent> {
    val context = LocalContext.current
    return remember {
        callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(p0: Context?, p1: Intent?) {
                    val action = p1?.action
                    if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                        val device: BluetoothDevice? = p1.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val bondState = p1.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        when (bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                device?.address?.let { trySend(BondingEvent.Bonded(it)) }
                            }
                            BluetoothDevice.BOND_NONE -> {
                                device?.address?.let { trySend(BondingEvent.Unbonded(it)) }
                            }
                            BluetoothDevice.ERROR -> {
                                val deviceId = device?.address
                                trySend(BondingEvent.Error(deviceId))
                            }
                            else -> {
                                // Ignore other states
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(receiver, filter)
            awaitClose {
                context.unregisterReceiver(receiver)
            }
        }
    }
}