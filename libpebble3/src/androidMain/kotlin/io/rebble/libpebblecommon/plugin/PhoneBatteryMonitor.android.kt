package io.rebble.libpebblecommon.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

actual class PhoneBatteryMonitor(
    private val appContext: AppContext,
) {
    actual val batteryLevel: Flow<Int?> = callbackFlow {
        val context = appContext.context
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(intent?.computePercent())
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // ACTION_BATTERY_CHANGED is a sticky broadcast — registerReceiver returns the
        // most recent value immediately, which we can emit as the initial snapshot.
        val sticky = context.registerReceiver(receiver, filter)
        trySend(sticky?.computePercent())
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged()

    private fun Intent.computePercent(): Int? {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }
}
