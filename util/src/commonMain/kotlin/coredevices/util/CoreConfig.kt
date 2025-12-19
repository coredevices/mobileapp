package coredevices.util

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class CoreConfigHolder(
    private val defaultValue: CoreConfig,
    private val settings: Settings,
    private val json: Json,
) {
    private fun defaultValue(): CoreConfig {
        return loadFromStorage() ?: defaultValue.also { saveToStorage(it) }
    }

    private fun loadFromStorage(): CoreConfig? = settings.getStringOrNull(SETTINGS_KEY)?.let { string ->
        try {
            json.decodeFromString(string)
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

@Serializable
data class CoreConfig(
    val useNativeAppStore: Boolean = false,
    val ignoreOtherPebbleApps: Boolean = false,
    val disableCompanionDeviceManager: Boolean = false,
    val weatherPins: Boolean = false,
    val disableFirmwareUpdateNotifications: Boolean = false,
)