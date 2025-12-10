package coredevices

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import coredevices.database.UserConfigDao
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class EnableExperimentalDevices(
    private val settings: Settings,
    private val userConfigDao: UserConfigDao
) {
    private val _enabled = MutableStateFlow(settings[PREF_ENABLE_EXPERIMENTAL_DEVICES] ?: false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        Firebase.auth.authStateChanged
            .map {
                it?.uid?.let { uid -> userConfigDao.getUserConfig(uid).experimentalDevices } ?: false
            }
            .distinctUntilChanged()
            .onEach { enableExperimental ->
                if (enableExperimental != _enabled.value) {
                    if (settings.get<Boolean>(PREF_ENABLE_EXPERIMENTAL_DEVICES) == null) {
                        Logger.Companion.d("EnableExperimentalDevices") { "Updating from UserConfig to $enableExperimental" }
                        _enabled.value = enableExperimental
                    } else {
                        Logger.Companion.d("EnableExperimentalDevices") { "Not updating from UserConfig because local setting exists" }
                    }
                }
            }
            .catch {
                Logger.Companion.e("EnableExperimentalDevices", it) { "Error observing UserConfig" }
            }
            .launchIn(GlobalScope)
    }

    fun set(value: Boolean) {
        settings[PREF_ENABLE_EXPERIMENTAL_DEVICES] = value
        _enabled.value = value
    }

    companion object {
        const val PREF_ENABLE_EXPERIMENTAL_DEVICES = "enable_experimental_devices"
    }
}