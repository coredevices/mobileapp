package io.rebble.libpebblecommon.connection.bt.classic.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleScanResult
import io.rebble.libpebblecommon.connection.asPebbleBtClassicIdentifier
import io.rebble.libpebblecommon.connection.bt.classic.PEBBLE_NAME_REGEX
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private val logger = Logger.withTag("AndroidClassicScanner")

class AndroidClassicScanner(
    private val appContext: AppContext,
) : ClassicScanner {
    @SuppressLint("MissingPermission")
    override fun scan(): Flow<PebbleScanResult> = callbackFlow {
        val context = appContext.context
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null) {
            logger.w { "No BluetoothAdapter; cannot scan for classic Pebbles" }
            close()
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return
                val device: BluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return
                val name = try { device.name } catch (e: SecurityException) { null } ?: return
                if (!PEBBLE_NAME_REGEX.matches(name)) return
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0.toShort()).toInt()
                trySend(
                    PebbleScanResult(
                        identifier = device.address.asPebbleBtClassicIdentifier(),
                        name = name,
                        rssi = rssi,
                        leScanRecord = null,
                    )
                )
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        try {
            if (!adapter.startDiscovery()) {
                logger.w { "startDiscovery() returned false" }
            }
        } catch (e: SecurityException) {
            logger.e(e) { "startDiscovery threw SecurityException" }
            context.unregisterReceiver(receiver)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            try {
                adapter.cancelDiscovery()
            } catch (e: SecurityException) {
                logger.w(e) { "cancelDiscovery threw" }
            }
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // already unregistered
            }
        }
    }
}
