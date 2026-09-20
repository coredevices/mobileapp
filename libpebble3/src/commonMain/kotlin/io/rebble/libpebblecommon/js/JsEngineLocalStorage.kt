package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.connection.AppContext

/**
 * `localStorage` for a [JsEngine] context, scoped to the same settings PKJS uses for
 * [scopedSettingsUuid] — a pbw's plugin and its PKJS app therefore read and write the same keys.
 */
class JsEngineLocalStorage(
    scopedSettingsUuid: String,
    appContext: AppContext,
    private val eval: (String) -> Unit,
) : JSLocalStorageInterface(scopedSettingsUuid, appContext), JsEngineInterface {

    override val name = "_localStorage"

    override val methods = listOf("getItem", "setItem", "removeItem", "clear", "key")

    override fun dispatch(method: String, args: List<Any?>): Any? = when (method) {
        "getItem" -> getItem(args.getOrNull(0))
        "setItem" -> { setItem(args.getOrNull(0), args.getOrNull(1)); null }
        "removeItem" -> { removeItem(args.getOrNull(0)); null }
        "clear" -> { clear(); null }
        "key" -> key((args[0] as Number).toDouble())
        else -> error("Unknown method: $method")
    }

    override fun setLength(value: Int) {
        eval("globalThis.localStorage.length = $value")
    }

    /** Installed over whatever the platform provides, so both engines expose the same object. */
    val installJs = """
        (function (global) {
          var native = global.$name;
          var storage = {
            getItem: function (key) { return native.getItem(String(key)); },
            setItem: function (key, value) { native.setItem(String(key), String(value)); },
            removeItem: function (key) { native.removeItem(String(key)); },
            clear: function () { native.clear(); },
            key: function (index) { return native.key(index); },
            length: 0,
          };
          Object.defineProperty(global, 'localStorage', {
            value: storage, writable: false, configurable: true,
          });
        })(globalThis);
    """.trimIndent()
}
