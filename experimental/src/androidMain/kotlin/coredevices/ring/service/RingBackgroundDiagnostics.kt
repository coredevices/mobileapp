package coredevices.ring.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import co.touchlab.kermit.Logger

/**
 * Logs screen on/off transitions and reports the power state that gates background BLE
 * scanning, so screen-off ring sync gaps can be correlated from a bug report alone.
 */
class RingBackgroundDiagnostics(private val context: Context) {
    private val logger = Logger.withTag("ScreenState")
    private val powerManager = context.getSystemService(PowerManager::class.java)

    @Volatile
    private var screenInteractive: Boolean? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val interactive = intent.action == Intent.ACTION_SCREEN_ON
            screenInteractive = interactive
            logger.i { if (interactive) "Screen on" else "Screen off" }
        }
    }

    fun start() {
        screenInteractive = powerManager?.isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(receiver, filter)
    }
}
