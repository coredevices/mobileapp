package coredevices.ring.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import coredevices.ring.database.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class RingBackgroundManager: KoinComponent {
    private val context: Context by inject()
    private val commonPrefs: Preferences by inject()

    actual fun startBackground() {
        ContextCompat.startForegroundService(context, Intent(context, RingService::class.java))
    }

    actual fun stopBackground() {
        val serviceIntent = Intent(context, RingService::class.java).apply {
            action = RingService.ACTION_STOP
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    actual fun startBackgroundIfEnabled() {
        val paired = commonPrefs.ringPaired.value
        if (!isRunning.value && paired != null) {
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
    actual val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
}