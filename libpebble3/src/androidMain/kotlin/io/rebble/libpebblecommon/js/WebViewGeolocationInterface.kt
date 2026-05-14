package io.rebble.libpebblecommon.io.rebble.libpebblecommon.js

import android.webkit.JavascriptInterface
import io.rebble.libpebblecommon.js.GeolocationInterface
import io.rebble.libpebblecommon.js.JsRunner
import kotlinx.coroutines.CoroutineScope

class WebViewGeolocationInterface(
    scope: CoroutineScope,
    jsRunner: JsRunner
): GeolocationInterface(scope, jsRunner) {

    @JavascriptInterface
    override fun getCurrentPosition(
        id: Double,
        maximumAgeMs: Double,
        timeoutMs: Double,
        highAccuracy: Double,
    ): Int {
        return super.getCurrentPosition(id, maximumAgeMs, timeoutMs, highAccuracy)
    }

    @JavascriptInterface
    override fun watchPosition(id: Double, interval: Double, highAccuracy: Double): Int {
        return super.watchPosition(id, interval, highAccuracy)
    }

    @JavascriptInterface
    override fun clearWatch(id: Int) {
        super.clearWatch(id)
    }

    @JavascriptInterface
    override fun getRequestCallbackID(): Int {
        return super.getRequestCallbackID()
    }

    @JavascriptInterface
    override fun getWatchCallbackID(): Int {
        return super.getWatchCallbackID()
    }

}