package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.util.encodeBase64
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import platform.JavaScriptCore.JSValue

class WebSocketManager(
    private val scope: CoroutineScope,
    private val eval: (String) -> JSValue?,
): RegisterableJsInterface {
    private var lastInstance = 0
    private val instances = mutableMapOf<Int, WSInstance>()
    private val client = HttpClient(Darwin) {
        install(WebSockets)
    }
    private val logger = Logger.withTag("WebSocketManager")

    override val name = "_WebSocketManager"
    override val interf = mapOf(
        "createInstance" to this::createInstance,
        "send" to this::send,
        "close" to this::closeInstance,
    )

    override fun dispatch(method: String, args: List<Any?>) = when (method) {
        "createInstance" -> createInstance(args[0].toString(), args.getOrNull(1)?.toString())
        "send" -> { send((args[0] as Number).toInt(), args[1].toString()); null }
        "close" -> {
            closeInstance(
                (args[0] as Number).toInt(),
                (args.getOrNull(1) as? Number)?.toInt() ?: 1000,
                args.getOrNull(2)?.toString() ?: ""
            )
            null
        }
        else -> error("Unknown method: $method")
    }

    private fun createInstance(url: String, protocols: String?): Int {
        val id = ++lastInstance
        val instance = WSInstance(id, url, protocols)
        instances[id] = instance
        instance.connect()
        return id
    }

    private fun send(instanceId: Int, data: String) {
        val instance = instances[instanceId]
        if (instance == null) {
            logger.w { "send called on unknown instance $instanceId" }
            return
        }
        instance.send(data)
    }

    private fun closeInstance(instanceId: Int, code: Int, reason: String) {
        val instance = instances[instanceId]
        if (instance == null) {
            logger.w { "close called on unknown instance $instanceId" }
            return
        }
        instance.close(code, reason)
    }

    inner class WSInstance(val id: Int, private val url: String, private val protocols: String?) {
        private var session: WebSocketSession? = null
        private var connectionJob: Job? = null
        private val jsInstance = "WebSocket._instances.get($id)"

        fun connect() {
            connectionJob = scope.launch(Dispatchers.IO) {
                try {
                    val ws = client.webSocketSession(urlString = url) {
                        if (!protocols.isNullOrEmpty()) {
                            header("Sec-WebSocket-Protocol", protocols)
                        }
                    }
                    session = ws

                    val negotiatedProtocol = ws.call.response.headers["Sec-WebSocket-Protocol"] ?: ""
                    scope.launch {
                        eval("$jsInstance._onOpen(${Json.encodeToString(negotiatedProtocol)})")
                    }

                    for (frame in ws.incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                scope.launch {
                                    eval("$jsInstance._onMessage(${Json.encodeToString(text)}, false)")
                                }
                            }
                            is Frame.Binary -> {
                                val base64 = frame.data.encodeBase64()
                                scope.launch {
                                    eval("$jsInstance._onMessage(${Json.encodeToString(base64)}, true)")
                                }
                            }
                            else -> { /* Ping/Pong handled by ktor */ }
                        }
                    }

                    // Connection closed normally
                    val reason = ws.closeReason.await()
                    val code = reason?.code?.toInt() ?: 1000
                    val reasonText = reason?.message ?: ""
                    scope.launch {
                        eval("$jsInstance._onClose($code, ${Json.encodeToString(reasonText)}, true)")
                    }
                    instances.remove(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "WebSocket error for instance $id: ${e.message}" }
                    scope.launch {
                        eval("$jsInstance._onError()")
                        eval("$jsInstance._onClose(1006, '', false)")
                    }
                    instances.remove(id)
                }
            }
        }

        fun send(data: String) {
            scope.launch(Dispatchers.IO) {
                try {
                    session?.send(Frame.Text(data))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "WebSocket send error for instance $id: ${e.message}" }
                    scope.launch {
                        eval("$jsInstance._onError()")
                    }
                }
            }
        }

        fun close(code: Int, reason: String) {
            scope.launch(Dispatchers.IO) {
                try {
                    session?.close(CloseReason(code.toShort(), reason))
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
    }

    override fun close() {
        client.close()
        instances.values.forEach { it.cancel() }
        instances.clear()
    }
}
