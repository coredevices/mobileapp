package coredevices.pebble.health

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the last-synced timestamps per data type to avoid duplicate writes
 * to Apple Health / Google Health Connect.
 */
class HealthSyncTracker(private val settings: Settings) {

    companion object {
        private const val KEY_ENABLED = "health_platform_sync_enabled"
        private const val KEY_LAST_SYNCED_STEPS = "health_sync_last_steps_timestamp"
        private const val KEY_LAST_SYNCED_OVERLAY = "health_sync_last_overlay_timestamp"
    }

    private val _enabled = MutableStateFlow<Boolean>(settings.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(newEnabled: Boolean) {
        _enabled.value = newEnabled
        settings[KEY_ENABLED] = newEnabled
    }

    var lastSyncedStepsTimestamp: Long
        get() = settings.getLong(KEY_LAST_SYNCED_STEPS, 0L)
        set(value) { settings[KEY_LAST_SYNCED_STEPS] = value }

    var lastSyncedOverlayTimestamp: Long
        get() = settings.getLong(KEY_LAST_SYNCED_OVERLAY, 0L)
        set(value) { settings[KEY_LAST_SYNCED_OVERLAY] = value }
}
