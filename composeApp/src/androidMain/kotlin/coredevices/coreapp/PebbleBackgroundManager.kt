package coredevices.coreapp

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import coredevices.ring.database.Preferences
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.ActiveDevice
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PebbleBackgroundManager(
    private val context: Context,
    private val commonPrefs: Preferences,
    private val coreConfigFlow: CoreConfigFlow,
    private val libPebble: LibPebble,
) {
    companion object {
        private val logger = Logger.withTag("PebbleBackgroundManager")
    }

    private fun startBackground() {
        ContextCompat.startForegroundService(context, Intent(context, PebbleService::class.java))
    }

    private fun stopBackground() {
        val serviceIntent = Intent(context, PebbleService::class.java).apply {
            action = PebbleService.ACTION_STOP
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun monitorToStartBackground() {
        combine(
            commonPrefs.ringPaired,
            coreConfigFlow.flow,
            libPebble.bluetoothEnabled,
            libPebble.watches,
        ) { ringPaired, config, btState, watches ->
            val ringActive = ringPaired != null
            val watchKeepAlive = config.androidForegroundServiceForWatchConnection &&
                btState.enabled() &&
                watches.any { it is ActiveDevice }
            ringActive || watchKeepAlive
        }
            .distinctUntilChanged()
            .onEach { shouldRun ->
                logger.d { "shouldRun=$shouldRun isRunning=${isRunning.value}" }
                if (shouldRun && !isRunning.value) {
                    startBackground()
                } else if (!shouldRun && isRunning.value) {
                    stopBackground()
                }
            }
            .launchIn(GlobalScope)
    }

    fun onServiceStarted() {
        _isRunning.value = true
    }

    fun onServiceStopped() {
        _isRunning.value = false
    }

    private val _isRunning: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
}
