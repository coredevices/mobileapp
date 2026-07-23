package coredevices.pebble.config.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Convenience builder that wires together the encrypted store, fetch, WebSocket,
 * and JavaScript interface for a single config page session.
 *
 * Typical usage inside [webViewFactory]:
 *
 * ```kotlin
 * val bridge = PebbleBridgeManager(
 *     context = webView.context,
 *     appUuid = uuid.toString(),
 *     config = mapOf("ha_url" to "...", "token" to ""),
 *     onClose = { returnValueJson ->
 *         // forward to PKJS session triggerOnWebviewClosed(returnValueJson)
 *     }
 * )
 * bridge.attach(webView)
 * ```
 */
class PebbleBridgeManager(
    private val context: Context,
    appUuid: String,
    config: Map<String, String>,
    private val onClose: (String) -> Unit,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val storage = BridgeStorage(context, appUuid)

    /**
     * Single Ktor/OkHttp client shared by fetch and WebSocket. Both plugins are
     * installed once so redirects, timeouts, and TLS are configured in one place.
     */
    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout)
        install(WebSockets)
        engine {
            config {
                followRedirects(true)
                retryOnConnectionFailure(true)
            }
        }
    }

    private val fetcher = BridgeFetch(httpClient)
    private val webSocketFactory = BridgeWebSocket(httpClient)

    private val configJson = buildConfigJson(config)

    private val evaluateJavascript: (String) -> Unit = { script ->
        // Posts to the main thread; the caller must call this only when the WebView
        // is attached to a window. The attach() helper below handles that safely.
        Handler(Looper.getMainLooper()).post {
            currentWebView?.evaluateJavascript(script, null)
        }
    }

    private var currentWebView: WebView? = null

    companion object {

        /**
         * Parses the URL hash and returns the decoded settings as a plain string map.
         *
         * Config pages currently encode settings in the URL hash (e.g.
         * `https://.../config.html#%7B%22token%22%3A%22...%22%7D`). The bridge exposes
         * these through `window.pebbleBridge.config` so the page no longer has to read
         * the URL itself.
         */
        fun parseConfigFromUrlHash(url: String): Map<String, String> =
            parseBridgeConfigFromUrlHash(url)

        private fun buildConfigJson(config: Map<String, String>): String {
            val json = JsonObject(config.mapValues { JsonPrimitive(it.value) })
            return bridgeJson.encodeToString(JsonObject.serializer(), json)
        }
    }

    private val bridgeJsShim: String by lazy {
        context.assets.open("bridge-shim.js").bufferedReader().use { it.readText() }
    }

    val nativeInterface = PebbleBridgeNativeInterface(
        scope = scope,
        configJson = configJson,
        storage = storage,
        fetcher = fetcher,
        webSocketFactory = webSocketFactory,
        onClose = onClose,
        evaluateJavascript = evaluateJavascript,
    )

    /**
     * Registers the native interface and the JS shim on the given WebView.
     * Safe to call from a factory that creates NativeWebView wrappers.
     */
    fun attach(webView: WebView) {
        currentWebView = webView
        webView.addJavascriptInterface(nativeInterface, "PebbleBridgeNative")
        webView.evaluateJavascript(bridgeJsShim, null)
    }

    /**
     * Re-injects the JS shim. Call from a page-finished listener when the page
     * navigates to a new document.
     */
    fun reInject(webView: WebView) {
        currentWebView = webView
        webView.evaluateJavascript(bridgeJsShim, null)
    }

    fun dispose() {
        nativeInterface.dispose()
        httpClient.close()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
