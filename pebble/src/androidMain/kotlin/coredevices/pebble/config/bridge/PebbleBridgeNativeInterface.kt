package coredevices.pebble.config.bridge

import android.webkit.JavascriptInterface
import co.touchlab.kermit.Logger
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.ConcurrentHashMap

/**
 * The native side of `window.pebbleBridge`.
 *
 * This class is registered on the WebView via [addJavascriptInterface]. A small JS shim
 * (injected on page load) adapts the string-based native methods to the Promise-based
 * JavaScript API defined in `bridge-spec.md`.
 */
class PebbleBridgeNativeInterface(
    private val scope: CoroutineScope,
    private val configJson: String,
    private val storage: BridgeStorage,
    private val fetcher: BridgeFetch,
    private val webSocketFactory: BridgeWebSocket,
    private val onClose: (String) -> Unit,
    private val evaluateJavascript: (String) -> Unit,
) {

    private val logger = Logger.withTag("PebbleBridge")

    @Volatile
    var active = true
        private set

    private val webSockets = ConcurrentHashMap<String, WebSocketSession>()

    @JavascriptInterface
    fun version(): String = VERSION

    @JavascriptInterface
    fun getConfig(): String = configJson

    @JavascriptInterface
    fun fetch(requestJson: String, callbackId: String) {
        if (!active) return
        scope.launch(Dispatchers.IO) {
            try {
                val request = bridgeJson.decodeFromString(FetchRequest.serializer(), requestJson)
                val response = fetcher.execute(request)
                val responseJson = bridgeJson.encodeToString(FetchResponse.serializer(), response)
                emit(callbackId, "resolve", responseJson)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.e(t) { "fetch failed: ${t.message}" }
                emit(callbackId, "reject", quoteJsString(t.message ?: "network error"))
            }
        }
    }

    @JavascriptInterface
    fun storageGet(key: String, callbackId: String) {
        val value = storage.get(key)
        emit(callbackId, "resolve", if (value == null) "null" else quoteJsString(value))
    }

    @JavascriptInterface
    fun storageSet(key: String, value: String, callbackId: String) {
        storage.set(key, value)
        emit(callbackId, "resolve", "true")
    }

    @JavascriptInterface
    fun storageRemove(key: String, callbackId: String) {
        storage.remove(key)
        emit(callbackId, "resolve", "true")
    }

    @JavascriptInterface
    fun webSocketConnect(socketId: String, url: String, protocolsJson: String) {
        if (!active) return
        val protocols = try {
            bridgeJson.decodeFromString(ListSerializer(String.serializer()), protocolsJson)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to parse protocols" }
            emitSocket(socketId, "error", quoteJsString("invalid protocols"))
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val session = webSocketFactory.connect(url, protocols, object : BridgeWebSocket.JsListener {
                    override fun onOpen() {
                        emitSocket(socketId, "open", "")
                    }

                    override fun onMessage(text: String) {
                        emitSocket(socketId, "message", quoteJsString(text))
                    }

                    override fun onClosing(code: Short, reason: String) {
                        val payload = bridgeJson.encodeToString(WebSocketCloseEvent.serializer(), WebSocketCloseEvent(code, reason))
                        emitSocket(socketId, "close", payload)
                        webSockets.remove(socketId)
                    }

                    override fun onFailure(t: Throwable) {
                        logger.e(t) { "websocket error: ${t.message}" }
                        emitSocket(socketId, "error", quoteJsString(t.message ?: "websocket error"))
                    }
                })
                webSockets[socketId] = session
            } catch (t: Throwable) {
                logger.e(t) { "websocket connect failed: ${t.message}" }
                emitSocket(socketId, "error", quoteJsString(t.message ?: "websocket connect failed"))
            }
        }
    }

    @JavascriptInterface
    fun webSocketSend(socketId: String, data: String) {
        val session = webSockets[socketId] ?: return
        scope.launch(Dispatchers.IO) {
            try {
                webSocketFactory.send(session, data)
            } catch (t: Throwable) {
                logger.e(t) { "websocket send failed: ${t.message}" }
            }
        }
    }

    @JavascriptInterface
    fun webSocketClose(socketId: String, code: Int, reason: String) {
        val session = webSockets.remove(socketId) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                webSocketFactory.close(session, code.toShort(), reason)
            } catch (t: Throwable) {
                logger.e(t) { "websocket close failed: ${t.message}" }
            }
        }
    }

    @JavascriptInterface
    fun close(returnValueJson: String) {
        closeResources()
        onClose(returnValueJson)
    }

    /**
     * Tears down sockets and marks the bridge inactive without invoking the close callback.
     * Used when the containing screen is disposed without an explicit JS close() call.
     */
    fun dispose() {
        closeResources()
    }

    private fun closeResources() {
        active = false
        scope.launch(Dispatchers.IO) {
            webSockets.values.forEach { session ->
                try {
                    webSocketFactory.close(session, 1000, "config closed")
                } catch (_: Throwable) { }
            }
            webSockets.clear()
            webSocketFactory.dispose()
        }
    }

    private fun emit(callbackId: String, method: String, payload: String) {
        val js = "window.__pebbleBridgeCallbacks['$callbackId'] && window.__pebbleBridgeCallbacks['$callbackId'].$method($payload);"
        evaluateJavascript(js)
    }

    private fun emitSocket(socketId: String, event: String, payload: String) {
        val js = "window.__pebbleBridgeWebSockets['$socketId'] && window.__pebbleBridgeWebSockets['$socketId'].$event($payload);"
        evaluateJavascript(js)
    }

    private fun quoteJsString(text: String): String =
        bridgeJson.encodeToString(String.serializer(), text)

    @Serializable
    private data class WebSocketCloseEvent(val code: Short, val reason: String)

    companion object {
        const val VERSION = "1.0.0"
    }
}
