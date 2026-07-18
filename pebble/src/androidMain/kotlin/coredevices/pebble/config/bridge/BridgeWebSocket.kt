package coredevices.pebble.config.bridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Bridges a JavaScript WebSocket to a Ktor/OkHttp WebSocket session.
 *
 * Messages are forwarded in both directions. The native app owns TLS validation,
 * so self-signed/user-CA certificates trusted at the OS level work automatically.
 */
class BridgeWebSocket {

    interface JsListener {
        fun onOpen()
        fun onMessage(text: String)
        fun onClosing(code: Short, reason: String)
        fun onFailure(t: Throwable)
    }

    private val client = HttpClient(OkHttp) {
        install(WebSockets)
        engine {
            config {
                followRedirects(true)
                retryOnConnectionFailure(true)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect(url: String, protocols: List<String>, listener: JsListener): WebSocketSession {
        val session = client.webSocketSession(url) {
            // Ktor OkHttp engine will send the subprotocol if requested.
            // Note: protocols handling depends on engine support.
        }

        scope.launch {
            try {
                listener.onOpen()
                for (frame in session.incoming) {
                    when (frame) {
                        is Frame.Text -> listener.onMessage(frame.readText())
                        is Frame.Close -> {
                            val reason = frame.readReason()
                            listener.onClosing(reason?.code ?: 1000.toShort(), reason?.message ?: "")
                            break
                        }
                        else -> { /* ignore binary/continuation frames */ }
                    }
                }
            } catch (t: Throwable) {
                listener.onFailure(t)
            }
        }

        return session
    }

    suspend fun send(session: WebSocketSession, data: String) {
        session.send(Frame.Text(data))
    }

    suspend fun close(session: WebSocketSession, code: Short, reason: String) {
        session.close(CloseReason(code, reason))
    }

    fun dispose() {
        scope.cancel()
        client.close()
    }
}
