package io.rebble.libpebblecommon.js

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.coroutines.resume

actual class JsEngine actual constructor(
    private val appContext: AppContext,
    scope: CoroutineScope,
    name: String,
    private val interfaces: List<JsEngineInterface>,
) {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    actual suspend fun start(): Unit = withContext(Dispatchers.Main) {
        val view = WebView(appContext.context)
        view.settings.javaScriptEnabled = true
        view.addJavascriptInterface(Dispatcher(interfaces), DISPATCH_NAMESPACE)
        webView = view
        // The interface is only injected on the next page load.
        awaitPageLoad(view)
        view.evalSuspend(LOCKDOWN_JS)
        interfaces.forEach { view.evalSuspend(it.proxyJs()) }
    }

    actual suspend fun eval(js: String) {
        val view = webView ?: return
        withContext(Dispatchers.Main) { view.evalSuspend(js) }
    }

    actual suspend fun stop(): Unit = withContext(Dispatchers.Main) {
        webView?.let {
            it.removeJavascriptInterface(DISPATCH_NAMESPACE)
            it.destroy()
        }
        webView = null
    }

    private suspend fun awaitPageLoad(view: WebView) = suspendCancellableCoroutine { cont ->
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
        view.loadDataWithBaseURL(BASE_URL, "<html><body></body></html>", "text/html", "utf-8", null)
    }

    private suspend fun WebView.evalSuspend(js: String) = suspendCancellableCoroutine { cont ->
        evaluateJavascript(js) { if (cont.isActive) cont.resume(it) }
    }

    /**
     * Arguments cross as JSON and come back out shaped like JavaScriptCore's — every number a
     * Double — so [JsEngineInterface] implementations don't have to know which engine they're on.
     */
    private class Dispatcher(interfaces: List<JsEngineInterface>) {
        private val byName = interfaces.associateBy { it.name }

        @JavascriptInterface
        fun dispatch(name: String, method: String, argsJson: String): String? {
            val iface = byName[name] ?: error("Unknown interface: $name")
            val args = Json.decodeFromString(JsonArray.serializer(), argsJson).map { it.toKotlin() }
            return when (val result = iface.dispatch(method, args)) {
                null -> null
                else -> Json.encodeToString(JsonPrimitive.serializer(), result.toJson())
            }
        }

        private fun kotlinx.serialization.json.JsonElement.toKotlin(): Any? = when {
            this is JsonNull -> null
            this is JsonPrimitive && isString -> content
            this is JsonPrimitive -> booleanOrNull ?: doubleOrNull ?: content
            else -> toString()
        }

        private fun Any.toJson(): JsonPrimitive = when (this) {
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            else -> JsonPrimitive(toString())
        }
    }

    private companion object {
        const val DISPATCH_NAMESPACE = "__jsEngineDispatch"

        // Scripts never load remote content; the origin only has to be stable and non-null.
        const val BASE_URL = "https://plugin.localhost/"

        fun JsEngineInterface.proxyJs(): String {
            val methodList = methods.joinToString(",") { "'$it'" }
            return """
                (function (global) {
                  var target = {};
                  [$methodList].forEach(function (m) {
                    target[m] = function () {
                      var raw = $DISPATCH_NAMESPACE.dispatch(
                        '$name', m, JSON.stringify(Array.prototype.slice.call(arguments)));
                      return raw === null || raw === undefined ? null : JSON.parse(raw);
                    };
                  });
                  global['$name'] = target;
                })(globalThis);
            """.trimIndent()
        }

        /**
         * The WebView hands over a whole browser; JavaScriptCore hands over bare ECMAScript.
         * Everything the iOS engine can't offer goes here, so a script that reaches for it fails
         * on both platforms rather than only on iOS. `document` goes too — an iframe would
         * otherwise hand back pristine copies of all of it.
         */
        val LOCKDOWN_JS = """
            (function (global) {
              var blocked = [
                'fetch', 'XMLHttpRequest', 'WebSocket', 'EventSource', 'Worker', 'SharedWorker',
                'ServiceWorker', 'importScripts', 'postMessage', 'BroadcastChannel',
                'RTCPeerConnection', 'Audio', 'AudioContext', 'webkitAudioContext', 'Image',
                'Notification', 'navigator', 'document', 'indexedDB', 'openDatabase', 'caches',
                'sessionStorage', 'localStorage', 'alert', 'confirm', 'prompt', 'open', 'print',
                'requestAnimationFrame', 'MutationObserver', 'FileReader', 'Bluetooth',
                'setTimeout', 'setInterval', 'clearTimeout', 'clearInterval', 'geolocation',
                'speechSynthesis', 'getComputedStyle', 'matchMedia', 'screen', 'history',
                'location', 'frames', 'parent', 'top', 'opener', 'self', 'window',
              ];
              blocked.forEach(function (key) {
                try {
                  Object.defineProperty(global, key, {
                    get: function () { throw new Error(key + ' is not available to plugins'); },
                    configurable: true,
                  });
                } catch (e) { /* non-configurable; nothing more we can do */ }
              });
            })(globalThis);
        """.trimIndent()
    }
}
