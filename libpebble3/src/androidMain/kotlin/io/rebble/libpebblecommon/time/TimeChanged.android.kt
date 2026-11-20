package io.rebble.libpebblecommon.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.rebble.libpebblecommon.connection.AppContext

internal class AndroidTimeChangedReceiver(private val context: Context) : TimeChanged {
    override fun registerForTimeChanges(onChanged: () -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onChanged()
            }
        }
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        context.registerReceiver(receiver, filter)
    }
}

internal actual fun createTimeChanged(appContext: AppContext): TimeChanged =
    AndroidTimeChangedReceiver(appContext.context)