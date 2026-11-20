package io.rebble.libpebblecommon.io.rebble.libpebblecommon.js

import android.webkit.JavascriptInterface
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.js.JSLocalStorageInterface
import kotlinx.serialization.json.Json

public class WebViewJSLocalStorageInterface(
    scopedSettingsUuid: String,
    appContext: AppContext,
    private val evaluateJavascript: (String) -> Unit
) {
    private val iface = object : JSLocalStorageInterface(scopedSettingsUuid, appContext) {
        override fun setLength(value: Int) {
            evaluateJavascript("localStorage.length = $value")
        }
    }

    @JavascriptInterface
    public fun clear() {
        iface.clear()
    }

    @JavascriptInterface
    public fun getItem(key: String?): String {
        return Json.encodeToString<String?>(iface.getItem(key)?.toString())
    }

    @JavascriptInterface
    public fun key(index: Double): String? {
        return iface.key(index)
    }

    @JavascriptInterface
    public fun removeItem(key: String?) {
        iface.removeItem(key)
    }

    @JavascriptInterface
    public fun setItem(key: String?, value: String?) {
        iface.setItem(key, value)
    }

    @JavascriptInterface
    public fun saveState(data: String) {
        iface.saveState(data)
    }

    @JavascriptInterface
    public fun restoreState(): String {
        return iface.restoreState()
    }
}