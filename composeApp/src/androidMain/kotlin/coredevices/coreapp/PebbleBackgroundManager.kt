package coredevices.coreapp

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import coredevices.libindex.LibIndex
import coredevices.libindex.device.DiscoveredIndexDevice
import coredevices.ring.database.Preferences
import coredevices.util.AndroidCompanionDevice
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
    private val libIndex: LibIndex,
    private val androidCompanionDevice: AndroidCompanionDevice
) {
    companion object {
        private val logger = Logger.withTag("PebbleBackgroundManager")
    }

    @Volatile
    private var shouldBeRunning = false

    private fun startBackground() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PebbleService::class.java))
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                logger.w(e) { "Cannot start PebbleService from background (no CDM exemption?)" }
                if (coreConfigFlow.value.enableIndex) {
                    if (!androidCompanionDevice.cdmPreviouslyCrashed()) {
                        libIndex.warnIfNoCompanionAssociations()
                    } else {
                        logger.w { "CDM previously crashed; skipping warnIfNoCompanionAssociations" }
                    }
                }
            } else {
                throw e
            }
        }
    }

    private fun stopBackground() {
        val serviceIntent = Intent(context, PebbleService::class.java).apply {
            action = PebbleService.ACTION_STOP
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                // App may already be backgrounded by the time we try to stop; startService is allowed for an already-running service.
                logger.w(e) { "Cannot deliver STOP via foreground service from background; falling back to startService" }
                context.startService(serviceIntent)
            } else {
                throw e
            }
        }
    }

    fun monitorToStartBackground() {
        var holdIndexEnabled = false
        combine(
            commonPrefs.ringPaired,
            libIndex.rings,
            coreConfigFlow.flow,
            libPebble.bluetoothEnabled,
            libPebble.watches,
        ) { ringPaired, rings, config, btState, watches ->
            val ringRecover = rings.any { it is DiscoveredIndexDevice && it.isFailsafe }
            if (ringRecover) {
                holdIndexEnabled = true
            }
            val ringActive = (ringPaired != null || ringRecover || holdIndexEnabled) && btState.enabled()
            val watchKeepAlive = config.androidForegroundServiceForWatchConnectionV2 &&
                btState.enabled() &&
                watches.any { it is ActiveDevice }
            ringActive || watchKeepAlive
        }
            .distinctUntilChanged()
            .onEach { shouldRun ->
                logger.d { "shouldRun=$shouldRun isRunning=${isRunning.value}" }
                shouldBeRunning = shouldRun
                if (shouldRun && !isRunning.value) {
                    startBackground()
                } else if (!shouldRun && isRunning.value) {
                    stopBackground()
                }
            }
            .launchIn(GlobalScope)
    }

    /**
     * Retry starting the background service when the app is foregrounded. The initial start may
     * have failed while backgrounded.
     */
    fun retryStartIfNeeded() {
        if (shouldBeRunning && !isRunning.value) {
            logger.i { "App foregrounded; retrying background service start (was not running)" }
            startBackground()
        }
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
