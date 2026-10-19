package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.serialization.json.Json

internal expect fun createJSSettings(appContext: AppContext, id: String): Settings

abstract class JSLocalStorageInterface(
    scopedSettingsUuid: String,
    appContext: AppContext,
    private val settings: Settings = createJSSettings(appContext, scopedSettingsUuid),
) {
    companion object {
        private val logger = Logger.withTag(JSLocalStorageInterface::class.simpleName!!)
    }

    abstract fun setLength(value: Int)
    fun getLength(): Int = settings.keys.size

    /**
     * Replace the stored keys with [data], a JSON object. New values are written before missing
     * keys are removed, so the store is never empty mid-save. An empty [data] is ignored while
     * keys are stored, since a real `localStorage.clear()` already emptied the store via the shim.
     */
    open fun saveState(data: Any?) {
        if (data !is String) return
        val map = Json.decodeFromString<Map<String, String?>>(data)
        val stored = settings.keys
        if (map.isEmpty() && stored.isNotEmpty()) {
            logger.w { "Refusing to replace ${stored.size} stored keys with an empty snapshot" }
            return
        }
        for ((key, value) in map) {
            settings.putString(key, value ?: "null")
        }
        for (key in stored - map.keys) {
            settings.remove(key)
        }
        setLength(settings.keys.size)
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