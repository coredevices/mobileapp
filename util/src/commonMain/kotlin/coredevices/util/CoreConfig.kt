package coredevices.util

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.util.models.CactusSTTMode
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
        return loadFromStorage() ?: defaultValue.also { saveToStorage(it) }
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

enum class WeatherUnit(val code: String, val displayName: String) {
    Metric("m", "Metric"),
    Imperial("e", "Imperial"),
    UkHybrid("h", "Mixed (UK)"),
}

@Serializable
data class CoreConfig(
    val useNativeAppStore: Boolean = false,
    val ignoreOtherPebbleApps: Boolean = false,
    val disableCompanionDeviceManager: Boolean = false,
    val weatherPinsV2: Boolean = true,
    val fetchWeather: Boolean = true,
    val disableFirmwareUpdateNotifications: Boolean = false,
    val enableIndex: Boolean = false,
    val weatherUnits: WeatherUnit = WeatherUnit.Metric,
    val showAllSettingsTab: Boolean = false,
    val sttConfig: STTConfig = STTConfig(),
    val interceptPKJSWeather: Boolean = true,
    val regularSyncInterval: Duration = 6.hours,
    val weatherSyncInterval: Duration = 1.hours,
)

@Serializable
data class STTConfig(
    val mode: CactusSTTMode = CactusSTTMode.RemoteFirst,
    val modelName: String? = null,
)