package io.rebble.libpebblecommon.io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.js.GeolocationInterface
import io.rebble.libpebblecommon.js.JsRunner
import io.rebble.libpebblecommon.js.RegisterableJsInterface
import kotlinx.coroutines.CoroutineScope

class JSCGeolocationInterface(
    scope: CoroutineScope,
    jsRunner: JsRunner
): GeolocationInterface(scope, jsRunner), RegisterableJsInterface {
    override val interf = mapOf(
        "getCurrentPosition" to this::getCurrentPosition,
        "watchPosition" to this::watchPosition,
        "clearWatch" to this::clearWatch,
        "getRequestCallbackID" to this::getRequestCallbackID,
        "getWatchCallbackID" to this::getWatchCallbackID
    )
    override val name = "_PebbleGeo"

    override fun dispatch(method: String, args: List<Any?>) = when (method) {
        "getCurrentPosition" -> getCurrentPosition((args[0] as Number).toDouble())
        "watchPosition" -> watchPosition((args[0] as Number).toDouble(), (args[1] as Number).toDouble())
        "clearWatch" -> { clearWatch((args[0] as Number).toInt()); null }
        "getRequestCallbackID" -> getRequestCallbackID()
        "getWatchCallbackID" -> getWatchCallbackID()
        else -> error("Unknown method: $method")
    }

    override fun close() {

    }
}