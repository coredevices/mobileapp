package io.rebble.libpebblecommon.js

import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.JavaScriptCore.JSContext
import platform.JavaScriptCore.JSValue
import platform.JavaScriptCore.objectForKeyedSubscript
import platform.JavaScriptCore.setObject

internal operator fun JSContext.set(key: String, value: Any?) {
    @Suppress("CAST_NEVER_SUCCEEDS")
    this.setObject(value, forKeyedSubscript = key as NSString)
}
internal operator fun JSContext.get(key: String): JSValue? {
    @Suppress("CAST_NEVER_SUCCEEDS")
    return this.objectForKeyedSubscript(key as NSString)
}
internal operator fun JSValue.set(key: String, value: Any?) {
    @Suppress("CAST_NEVER_SUCCEEDS")
    this.setObject(value, forKeyedSubscript = key as NSString)
}
internal operator fun JSValue.get(key: String): JSValue? {
    @Suppress("CAST_NEVER_SUCCEEDS")
    return this.objectForKeyedSubscript(key as NSString)
}
internal fun JSContext.evalCatching(script: String): JSValue? {
    return evaluateScript(
        """
            try {
                $script
            } catch (e) {
                globalThis._Pebble.onError(e.message, e.fileName, e.lineNumber, e.columnNumber)
                throw e;
            }
        """.trimIndent()
    )
}
internal fun JSContext.evalCatching(script: String, sourceUrl: NSURL?): JSValue? {
    return evaluateScript(
        """
            try {
                $script
            } catch (e) {
                globalThis._Pebble.onError(e.message, e.fileName, e.lineNumber, e.columnNumber);
                throw e;
            }
        """.trimIndent(),
        sourceUrl
    )
}