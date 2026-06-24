package io.rebble.libpebblecommon.js

import android.webkit.JavascriptInterface
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class WebViewWebSocketManager(
    private val scope: CoroutineScope,
    private val eval: (String) -> Unit,
) {
    private val lastInstance = AtomicInteger(0)
    private val generation = AtomicInteger(0)
    private val instances = ConcurrentHashMap<Int, WSInstance>()
    @Volatile
    private var client = createClient()
    @Volatile
    private var shuttingDown = false
    private val logger = Logger.withTag("WebViewWebSocketManager")

    @JavascriptInterface
    fun createInstance(url: String, protocols: String?): Int {
        WebSocketBridge.validateUrl(url)
        val requestedProtocols = WebSocketBridge.parseProtocols(protocols)
        ensureClient()
        val id = lastInstance.incrementAndGet()
        val instance = WSInstance(id, url, requestedProtocols, generation.get())
        instances[id] = instance
        return id
    }

    @JavascriptInterface
    fun connect(instanceId: Int) {
        val instance = instances[instanceId]
        if (instance == null) {
            logger.w { "connect called on unknown instance $instanceId" }
            return
        }
        instance.connect()
    }

    @JavascriptInterface
    fun send(instanceId: Int, data: String, isBinary: Boolean) {
        val instance = instances[instanceId]
        if (instance == null) {
            logger.w { "send called on unknown instance $instanceId" }
            return
        }
        instance.send(data, isBinary)
    }

    @JavascriptInterface
    fun close(instanceId: Int, code: Int, reason: String) {
        WebSocketBridge.validateClose(code, reason)
        val instance = instances[instanceId]
        if (instance == null) {
            logger.w { "close called on unknown instance $instanceId" }
            return
        }
        instance.close(code, reason)
    }

    private fun evalOnMain(instanceGeneration: Int, js: String) {
        scope.launch(Dispatchers.Main) {
            if (!shuttingDown && generation.get() == instanceGeneration) {
                eval(js)
            }
        }
    }

    @Synchronized
    private fun ensureClient(): HttpClient {
        if (shuttingDown) {
            client = createClient()
            shuttingDown = false
        }
        return client
    }

    private fun createClient() = HttpClient(OkHttp) {
        install(WebSockets)
    }

    inner class WSInstance(
        private val id: Int,
        private val url: String,
        private val protocols: List<String>,
        private val instanceGeneration: Int,
    ) {
        private var session: WebSocketSession? = null
        private var connectionJob: Job? = null
        private val stateLock = Any()
        private var connectStarted = false
        private var opened = false
        private var closeRequested: CloseReason? = null
        private var closeDispatched = false
        private val jsInstance = "WebSocket._instances.get($id)"

        fun connect() {
            synchronized(stateLock) {
                if (connectStarted || closeDispatched) {
                    return
                }
                connectStarted = true
            }
            connectionJob = scope.launch(Dispatchers.IO) {
                try {
                    val ws = ensureClient().webSocketSession(urlString = url) {
                        if (protocols.isNotEmpty()) {
                            header("Sec-WebSocket-Protocol", protocols.joinToString(","))
                        }
                    }
                    val requestedClose = synchronized(stateLock) {
                        session = ws
                        closeRequested
                    }
                    if (requestedClose != null) {
                        ws.close(requestedClose)
                        failConnection(dispatchError = false)
                        return@launch
                    }

                    val negotiatedProtocol = ws.call.response.headers["Sec-WebSocket-Protocol"]?.trim().orEmpty()
                    if (!WebSocketBridge.acceptsProtocol(negotiatedProtocol, protocols)) {
                        ws.close(CloseReason(1002.toShort(), "Invalid subprotocol"))
                        failConnection()
                        return@launch
                    }
                    synchronized(stateLock) {
                        opened = true
                    }
                    evalOnMain(
                        instanceGeneration,
                        """
                            (function(ws) {
                                if (ws && ws.readyState === WebSocket.CONNECTING) {
                                    ws._onOpen(${Json.encodeToString(negotiatedProtocol)});
                                }
                            })($jsInstance)
                        """.trimIndent()
                    )

                    for (frame in ws.incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                evalOnMain(instanceGeneration, "$jsInstance._onMessage(${Json.encodeToString(text)}, false)")
                            }
                            is Frame.Binary -> {
                                @OptIn(ExperimentalEncodingApi::class)
                                val base64 = Base64.encode(frame.data)
                                evalOnMain(instanceGeneration, "$jsInstance._onMessage(${Json.encodeToString(base64)}, true)")
                            }
                            else -> { /* Ping/Pong handled by ktor */ }
                        }
                    }

                    val reason = ws.closeReason.await()
                    val code = reason?.code?.toInt() ?: 1000
                    val reasonText = reason?.message ?: ""
                    dispatchClose(code, reasonText, true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "WebSocket error for instance $id: ${e.message}" }
                    failConnection()
                } finally {
                    instances.remove(id)
                }
            }
        }

        fun send(data: String, isBinary: Boolean = false) {
            val currentSession = synchronized(stateLock) {
                session.takeIf { opened && closeRequested == null && !closeDispatched }
            }
            if (currentSession == null) {
                logger.w { "send called while instance $id is not open" }
                return
            }
            scope.launch(Dispatchers.IO) {
                try {
                    if (isBinary) {
                        @OptIn(ExperimentalEncodingApi::class)
                        currentSession.send(Frame.Binary(true, Base64.decode(data)))
                    } else {
                        currentSession.send(Frame.Text(data))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "WebSocket send error for instance $id: ${e.message}" }
                    evalOnMain(instanceGeneration, "$jsInstance._onError()")
                }
            }
        }

        fun close(code: Int, reason: String) {
            val closeReason = CloseReason(code.toShort(), reason)
            val currentSession = synchronized(stateLock) {
                if (closeRequested == null) {
                    closeRequested = closeReason
                }
                session
            }
            if (currentSession == null) {
                connectionJob?.cancel()
                failConnection(dispatchError = false)
                instances.remove(id)
                return
            }
            scope.launch(Dispatchers.IO) {
                try {
                    currentSession.close(closeReason)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "WebSocket close error for instance $id: ${e.message}" }
                }
            }
        }

        fun cancel() {
            connectionJob?.cancel()
            connectionJob = null
        }

        private fun failConnection(dispatchError: Boolean = true) {
            dispatchClose(1006, "", false, dispatchError)
        }

        private fun dispatchClose(
            code: Int,
            reason: String,
            wasClean: Boolean,
            dispatchError: Boolean = false,
        ) {
            val shouldDispatch = synchronized(stateLock) {
                if (closeDispatched) {
                    false
                } else {
                    closeDispatched = true
                    true
                }
            }
            if (!shouldDispatch) {
                return
            }
            evalOnMain(
                instanceGeneration,
                """
                    (function(ws) {
                        if (ws) {
                            ${if (dispatchError) "ws._onError();" else ""}
                            ws._onClose($code, ${Json.encodeToString(reason)}, $wasClean);
                        }
                    })($jsInstance)
                """.trimIndent()
            )
        }
    }

    fun shutdown() {
        shuttingDown = true
        generation.incrementAndGet()
        instances.values.forEach { it.cancel() }
        instances.clear()
        client.close()
    }
}
