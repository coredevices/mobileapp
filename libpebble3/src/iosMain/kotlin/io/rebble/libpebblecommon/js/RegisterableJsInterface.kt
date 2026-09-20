package io.rebble.libpebblecommon.js

import platform.JavaScriptCore.JSContext

interface RegisterableJsInterface : JsEngineInterface, AutoCloseable {
    val interf: Map<String, *>
    override val methods: List<String> get() = interf.keys.toList()
    fun onRegister(jsContext: JSContext) {}
}
