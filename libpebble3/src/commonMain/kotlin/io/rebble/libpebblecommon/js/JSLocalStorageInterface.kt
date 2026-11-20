package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.serialization.json.Json

internal expect fun createJSSettings(appContext: AppContext, id: String): Settings

internal abstract class JSLocalStorageInterface(
    scopedSettingsUuid: String,
    appContext: AppContext,
) {
    private val settings = createJSSettings(appContext, scopedSettingsUuid)

    abstract fun setLength(value: Int)
    fun getLength(): Int = settings.keys.size

    open fun saveState(data: Any?) {
        if (data !is String) return
        val map = Json.decodeFromString<Map<String, String?>>(data)
        clear()
        for ((key, value) in map) {
            setItem(key, value)
        }
    }

    open fun restoreState(): String {
        val map = settings.keys.associateWith { getItem(it)?.toString() }
        return Json.encodeToString(map)
    }

    open fun getItem(key: Any?): Any? = settings.get<String>(key?.toString() ?: "null")

    open fun setItem(key: Any?, value: Any?) {
        settings.putString(key?.toString() ?: "null", value?.toString() ?: "null")
        setLength(settings.keys.size)
    }

    open fun removeItem(key: Any?) {
        settings.remove(key?.toString() ?: "null")
        setLength(settings.keys.size)
    }

    open fun clear() {
        settings.clear()
        setLength(0)
    }

    open fun key(index: Double): String? = settings.keys.elementAtOrNull(index.toInt())
}