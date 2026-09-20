package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.CoroutineScope

/**
 * A bare JS context: no DOM, no browser globals, nothing but the [interfaces] handed to it.
 * [start] brings the engine up; everything else is evaluated by the caller.
 *
 * Android's WebView arrives with a whole browser attached, so the actual there deletes every
 * global the iOS JSC context doesn't have — PKJS authors have historically reached for whatever
 * the WebView happened to expose (audio, sockets) and then broken on iOS.
 *
 * This is the seam an engine swap goes through. [JsRunner] does not use it yet — PKJS binds its
 * interfaces per platform, so moving it over means unifying that binding first.
 */
expect class JsEngine(
    appContext: AppContext,
    scope: CoroutineScope,
    name: String,
    interfaces: List<JsEngineInterface>,
) {
    suspend fun start()
    suspend fun stop()

    /** Evaluate [js] in the context. Results come back through an interface, not the return. */
    suspend fun eval(js: String)
}

/**
 * A named object exposed to JS as a global. The engine generates a JS proxy per [methods] entry;
 * calls arrive at [dispatch].
 *
 * Arguments are normalised to what JavaScriptCore hands over — `Double` for every number,
 * `String`, `Boolean`, or null — so one implementation serves both platforms.
 */
interface JsEngineInterface {
    val name: String
    val methods: List<String>
    fun dispatch(method: String, args: List<Any?>): Any?
}
