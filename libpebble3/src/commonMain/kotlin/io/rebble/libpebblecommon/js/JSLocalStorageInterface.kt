package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.rebble.libpebblecommon.connection.AppContext

internal expect fun createJSSettings(appContext: AppContext, id: String): Settings

internal abstract class JSLocalStorageInterface(
    scopedSettingsUuid: String,
    appContext: AppContext,
) {
    private val settings = createJSSettings(appContext, scopedSettingsUuid)

    abstract fun setLength(value: Int)
    fun getLength(): Int = settings.keys.size

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