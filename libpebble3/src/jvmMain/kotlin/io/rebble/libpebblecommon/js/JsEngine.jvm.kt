package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.CoroutineScope

/** JVM target stub — no JS engine, so JS plugins never produce data here. */
actual class JsEngine actual constructor(
    appContext: AppContext,
    scope: CoroutineScope,
    name: String,
    interfaces: List<JsEngineInterface>,
) {
    actual suspend fun start() = Unit
    actual suspend fun eval(js: String) = Unit
    actual suspend fun stop() = Unit
}
