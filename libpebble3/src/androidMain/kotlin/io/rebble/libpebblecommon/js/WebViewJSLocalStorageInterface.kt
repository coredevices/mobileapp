package io.rebble.libpebblecommon.io.rebble.libpebblecommon.js

import android.webkit.JavascriptInterface
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.js.JSLocalStorageInterface
import kotlinx.serialization.json.Json

/**
 * The store behind a WebView's `localStorage`, added as `_localStorage`; [INSTALL_JS] then makes
 * `window.localStorage` read and write it directly.
 */
public class WebViewJSLocalStorageInterface(
    scopedSettingsUuid: String,
    appContext: AppContext,
) {
    private val iface = object : JSLocalStorageInterface(scopedSettingsUuid, appContext) {
        override fun setLength(value: Int) {}
    }

    @JavascriptInterface
    public fun getLength(): Int {
        return iface.getLength()
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

    public companion object {
        /** A Proxy, so property-style use (`localStorage.foo = 'x'`) reaches the store too. */
        public val INSTALL_JS: String = """
            (function() {
                // Storage is disabled inside data: URLs (a plugin's config page); leave it that way.
                try { window.localStorage; } catch (e) { return; }
                const native = window._localStorage;
                const methods = {
                    getItem: function(key) { return JSON.parse(native.getItem(String(key))); },
                    setItem: function(key, value) { native.setItem(String(key), String(value)); },
                    removeItem: function(key) { native.removeItem(String(key)); },
                    clear: function() { native.clear(); },
                    key: function(index) {
                        const key = native.key(index);
                        return key === undefined ? null : key;
                    },
                };
                const isMethod = function(prop) { return typeof prop === 'symbol' || prop in methods; };
                const storage = new Proxy(methods, {
                    get: function(target, prop) {
                        if (prop === 'length') return native.getLength();
                        return isMethod(prop) ? target[prop] : target.getItem(prop);
                    },
                    set: function(target, prop, value) {
                        if (!isMethod(prop) && prop !== 'length') target.setItem(prop, value);
                        return true;
                    },
                    deleteProperty: function(target, prop) {
                        if (!isMethod(prop)) target.removeItem(prop);
                        return true;
                    },
                    has: function(target, prop) {
                        return isMethod(prop) || target.getItem(prop) !== null;
                    },
                    ownKeys: function(target) {
                        const keys = [];
                        for (let i = 0; i < native.getLength(); i++) keys.push(native.key(i));
                        return keys;
                    },
                    getOwnPropertyDescriptor: function(target, prop) {
                        if (isMethod(prop)) return Reflect.getOwnPropertyDescriptor(target, prop);
                        const value = target.getItem(prop);
                        return value === null ? undefined : { value: value, writable: true, enumerable: true, configurable: true };
                    },
                });
                Object.defineProperty(window, 'localStorage', { value: storage, configurable: true });
            })();
            window.__localStorageShimmed = true;
        """.trimIndent()
    }
}