package coredevices.util

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.util.models.CactusSTTMode
import io.rebble.libpebblecommon.database.entity.QuietTimeSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class CoreConfigHolder(
    private val defaultValue: CoreConfig,
    private val settings: Settings,
    private val json: Json,
) {
    private fun defaultValue(): CoreConfig {
        val stored = loadFromStorage()
        val config = (stored ?: defaultValue).migrateNavBarTabs()
        if (config != stored) saveToStorage(config)
        return config
    }

    private fun migrateCactusSettings(oldConfig: CoreConfig): CoreConfig {
        val mode = settings.getIntOrNull("cactus_mode")
        val model = settings.getStringOrNull("cactus_stt_model")
        if (mode != null) {
            Logger.i("CoreConfigHolder") { "Migrating old Cactus STT settings: mode=$mode, model=$model" }
            settings.remove("cactus_mode")
            settings.remove("cactus_stt_model")
            return oldConfig.copy(
                sttConfig = STTConfig(
                    mode = CactusSTTMode.fromId(mode),
                    modelName = model,
                )
            )
        } else {
            return oldConfig
        }
    }

    private fun loadFromStorage(): CoreConfig? = settings.getStringOrNull(SETTINGS_KEY)?.let { string ->
        try {
            migrateCactusSettings(json.decodeFromString(string))
        } catch (e: SerializationException) {
            Logger.w("Error loading settings", e)
            null
        }
    }

    private fun saveToStorage(value: CoreConfig) {
        settings.set(SETTINGS_KEY, json.encodeToString(value))
    }

    fun update(value: CoreConfig) {
        saveToStorage(value)
        _config.value = value
    }

    private val _config: MutableStateFlow<CoreConfig> = MutableStateFlow(defaultValue())
    val config: StateFlow<CoreConfig> = _config.asStateFlow()
}

class CoreConfigFlow(val flow: StateFlow<CoreConfig>) {
    val value get() = flow.value
}

private const val SETTINGS_KEY = "coreapp.config"

internal fun CoreConfig.migrateNavBarTabs(): CoreConfig =
    if (navBarTabs != null) this
    else copy(
        navBarTabs = listOf("WatchFaces", "Index", "Watches") +
            (if (preferHealthTab) listOf("Health", "Notifications") else listOf("Notifications", "Health")) +
            "Settings"
    )

enum class WeatherUnit(val code: String) {
    Metric("m"),
    Imperial("e"),
    UkHybrid("h"),
}

@Serializable
data class CoreConfig(
    val ignoreOtherPebbleApps: Boolean = false,
    val disableCompanionDeviceManager: Boolean = false,
    val weatherPinsV2: Boolean = true,
    val fetchWeather: Boolean = true,
    val disableFirmwareUpdateNotifications: Boolean = false,
    val enableIndex: Boolean = false,
    val indexPermissionsConfirmed: Boolean = false,
    /** No longer written — weather units are the watch's imperial/metric pref. The migration in
     * [coredevices.pebble.weather.WeatherFetcher] reads it, and an upgrade can arrive from any
     * older version, so it has to stay. */
    val weatherUnits: WeatherUnit? = null,
    val showAllSettingsTab: Boolean = false,
    val sttConfig: STTConfig = STTConfig(),
    val interceptPKJSWeather: Boolean = true,
    val regularSyncInterval: Duration = 6.hours,
    val weatherSyncInterval: Duration = 1.hours,
    /** No longer written; only seeds [navBarTabs] in [migrateNavBarTabs]. */
    val preferHealthTab: Boolean = true,
    /** `WatchHomeNavTab` names in bottom bar order; only null before [migrateNavBarTabs]. */
    val navBarTabs: List<String>? = null,
    /** Bottom bar slots; if the available tabs don't fit, the last one is More. */
    val navBarSlots: Int = 5,
    /** Otherwise the launch tab is picked from the paired devices. */
    val startOnFirstNavBarTab: Boolean = false,
    val obfuscateSensitiveLogs: Boolean = true,
    val hidePermissionWarningBadges: Boolean = false,
    val androidForegroundServiceForWatchConnectionV2: Boolean = true,
    val showWatchConnectionDebugInfo: Boolean = false,
    /** Stops the ring scan/sync loop, for when another profile's app instance owns the ring. */
    val disableRingBluetoothSync: Boolean = false,
    val notifyWatchFullyCharged: Boolean = true,
    /** Set the day/night backlight switch times from the local sunrise/sunset during weather sync. */
    val backlightColorAutoSunriseSunset: Boolean = true,
    val autoFirmwareUpdate: Boolean = false,
    /** Null means updates are installed any time. */
    val autoFirmwareUpdateWindow: QuietTimeSchedule? = null,
)

@Serializable
data class STTConfig(
    val mode: CactusSTTMode = CactusSTTMode.RemoteOnly,
    val modelName: String? = null,
    /** ISO 639-1 language code. Null means auto-detect. */
    val spokenLanguage: String? = null,
)