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
     * Replace the stored keys with [data], a JSON object of key to value.
     *
     * The store never passes through an empty state on the way: the new values are written
     * first and only then are the keys missing from [data] removed, so a process death part way
     * through (a reboot with a PKJS session open) leaves the old or the new contents on disk
     * rather than nothing.
     *
     * An empty [data] against a non-empty store is refused. An app that really called
     * `localStorage.clear()` has already emptied the store through the shim, so an empty
     * snapshot while keys are still stored means the DOM copy was lost, not cleared.
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