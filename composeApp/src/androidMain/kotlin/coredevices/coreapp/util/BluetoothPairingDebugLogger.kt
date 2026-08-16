package coredevices.coreapp.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger

private val logger = Logger.withTag("BluetoothPairingDebug")

@SuppressLint("InlinedApi")
fun registerBluetoothPairingDebugLogger(context: Context) {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                }
            logger.i {
                "${intent.action} device=${device?.address} extras={${intent.describeExtras()}}"
            }
        }
    }
    val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            addAction(BluetoothDevice.ACTION_KEY_MISSING)
        }
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
}

private fun Intent.describeExtras(): String {
    val extras = extras ?: return ""
    @Suppress("DEPRECATION")
    return extras.keySet().joinToString { key -> "$key=${extras.get(key)}" }
}
