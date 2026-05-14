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

    override fun dispatch(method: String, args: List<Any?>): Int? {
        fun num(i: Int) = args.getOrNull(i) as? Number
        return when (method) {
            "getCurrentPosition" -> {
                val id = num(0)?.toDouble() ?: return null
                val maxAge = num(1)?.toDouble() ?: -1.0
                val timeout = num(2)?.toDouble() ?: -1.0
                val highAccuracy = num(3)?.toDouble() ?: 0.0
                getCurrentPosition(id, maxAge, timeout, highAccuracy)
            }
            "watchPosition" -> {
                val id = num(0)?.toDouble() ?: return null
                val interval = num(1)?.toDouble() ?: 500.0
                val highAccuracy = num(2)?.toDouble() ?: 0.0
                watchPosition(id, interval, highAccuracy)
            }
            "clearWatch" -> { num(0)?.toInt()?.let { clearWatch(it) }; null }
            "getRequestCallbackID" -> getRequestCallbackID()
            "getWatchCallbackID" -> getWatchCallbackID()
            else -> error("Unknown method: $method")
        }
    }

    override fun close() {

    }
}