package coredevices.pebble.config.bridge

import android.content.Context
import android.webkit.WebView
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
 *     config = mapOf("ha_url" to "...", "token" to "..."),
 *     onClose = { returnValueJson ->
 *         // forward to PKJS session triggerOnWebviewClosed(returnValueJson)
 *     }
 * )
 * bridge.attach(webView)
 * ```
 */
class PebbleBridgeManager(
    context: Context,
    appUuid: String,
    config: Map<String, String>,
    private val onClose: (String) -> Unit,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val storage = BridgeStorage(context, appUuid)
    private val fetcher = BridgeFetch()
    private val webSocketFactory = BridgeWebSocket()

    private val configJson = buildConfigJson(config)

    private val evaluateJavascript: (String) -> Unit = { script ->
        // Posts to the main thread; the caller must call this only when the WebView
        // is attached to a window. The attach() helper below handles that safely.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
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

        /**
         * JavaScript shim that turns the native string bridge into the Promise-based
         * `window.pebbleBridge` API expected by config pages.
         */
        val bridgeJsShim: String = """
(function() {
    if (window.pebbleBridge) return;

    function makeCallback(resolve, reject) {
        const id = 'cb_' + Math.random().toString(36).slice(2);
        window.__pebbleBridgeCallbacks = window.__pebbleBridgeCallbacks || {};
        window.__pebbleBridgeCallbacks[id] = {
            resolve: function(v) {
                delete window.__pebbleBridgeCallbacks[id];
                resolve(v);
            },
            reject: function(e) {
                delete window.__pebbleBridgeCallbacks[id];
                reject(new Error(e));
            }
        };
        return id;
    }

    function BridgeResponse(raw) {
        const data = (typeof raw === 'string') ? JSON.parse(raw) : raw;
        this.ok = data.ok;
        this.status = data.status;
        this.statusText = data.statusText;
        this.headers = data.headers || {};
        this._body = data.body || '';
    }
    BridgeResponse.prototype.text = function() {
        return Promise.resolve(typeof this._body === 'string' ? this._body : JSON.stringify(this._body));
    };
    BridgeResponse.prototype.json = function() {
        if (typeof this._body === 'string') {
            return Promise.resolve(JSON.parse(this._body));
        }
        return Promise.resolve(this._body);
    };

    function BridgeWebSocket(url, protocols) {
        const self = this;
        protocols = protocols || [];
        this.url = url;
        this.readyState = 0;
        this.id = 'ws_' + Math.random().toString(36).slice(2);
        window.__pebbleBridgeWebSockets = window.__pebbleBridgeWebSockets || {};
        window.__pebbleBridgeWebSockets[this.id] = this;
        this.onopen = null;
        this.onmessage = null;
        this.onerror = null;
        this.onclose = null;

        // Native bridge emits events by calling these methods on the socket object.
        this.open = function() {
            self.readyState = BridgeWebSocket.OPEN;
            if (self.onopen) self.onopen();
        };
        this.message = function(data) {
            if (self.onmessage) self.onmessage({ data: data });
        };
        this.error = function(msg) {
            if (self.onerror) self.onerror(new Error(msg));
        };
        // Native emits close event with {code, reason}; user calls close(code, reason).
        this.close = function(arg1, arg2) {
            if (arg1 && typeof arg1 === 'object' && 'code' in arg1) {
                self.readyState = BridgeWebSocket.CLOSED;
                if (self.onclose) self.onclose({ code: arg1.code, reason: arg1.reason || '', wasClean: arg1.code === 1000 });
            } else {
                self.readyState = BridgeWebSocket.CLOSING;
                window.PebbleBridgeNative.webSocketClose(self.id, arg1 || 1000, arg2 || '');
            }
        };

        window.PebbleBridgeNative.webSocketConnect(this.id, url, JSON.stringify(protocols));

        this.send = function(data) {
            window.PebbleBridgeNative.webSocketSend(self.id, data);
        };
    }
    BridgeWebSocket.CONNECTING = 0;
    BridgeWebSocket.OPEN = 1;
    BridgeWebSocket.CLOSING = 2;
    BridgeWebSocket.CLOSED = 3;

    window.pebbleBridge = {
        version: window.PebbleBridgeNative.version(),
        config: JSON.parse(window.PebbleBridgeNative.getConfig()),
        fetch: function(url, options) {
            options = options || {};
            return new Promise(function(resolve, reject) {
                const req = {
                    url: url,
                    method: options.method || 'GET',
                    headers: options.headers || {},
                    body: options.body || null,
                    timeout: options.timeout || 30000
                };
                window.PebbleBridgeNative.fetch(
                    JSON.stringify(req),
                    makeCallback(function(raw) { resolve(new BridgeResponse(raw)); }, reject)
                );
            });
        },
        WebSocket: BridgeWebSocket,
        storage: {
            get: function(key) {
                return new Promise(function(resolve, reject) {
                    window.PebbleBridgeNative.storageGet(key, makeCallback(resolve, reject));
                });
            },
            set: function(key, value) {
                return new Promise(function(resolve, reject) {
                    window.PebbleBridgeNative.storageSet(key, value, makeCallback(resolve, reject));
                });
            },
            remove: function(key) {
                return new Promise(function(resolve, reject) {
                    window.PebbleBridgeNative.storageRemove(key, makeCallback(resolve, reject));
                });
            }
        },
        close: function(returnValue) {
            window.PebbleBridgeNative.close(JSON.stringify(returnValue || {}));
        }
    };
})();
        """.trimIndent()

        private fun buildConfigJson(config: Map<String, String>): String {
            val json = JsonObject(config.mapValues { JsonPrimitive(it.value) })
            return bridgeJson.encodeToString(JsonObject.serializer(), json)
        }
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
        nativeInterface.close("{}")
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
