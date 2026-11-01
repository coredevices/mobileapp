package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.rebble.libpebblecommon.permissions.NetworkAccess
import io.rebble.libpebblecommon.plugin.PluginNetworkVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

// RFC 6455 close codes reported to JS; Ktor keeps the abnormal one internal.
private const val NORMAL_CLOSURE = 1000
private const val NO_STATUS_RECEIVED = 1005
private const val ABNORMAL_CLOSURE = 1006

private val UPGRADE_HEADERS = setOf("host", "connection", "upgrade")

class WebSocketManager(
    private val scope: CoroutineScope,
    private val eval: (String) -> Unit,
    private val client: HttpClient,
    private val appUuid: Uuid,
    private val networkAccess: NetworkAccess,
) : JsEngineInterface, AutoCloseable {
    private var lastInstance = 0
    private val instances = mutableMapOf<Int, Instance>()
    private val logger = Logger.withTag("WebSocketManager")

    override val name = "_WebSocketManager"
    override val methods = listOf("createInstance", "send", "close")

    override fun dispatch(method: String, args: List<Any?>): Any? = when (method) {
        "createInstance" -> createInstance(
            args[0].toString(),
            args.getOrNull(1)?.toString().orEmpty(),
            handshakeHeaders(args.getOrNull(2)?.toString().orEmpty()),
        )
        "send" -> {
            val binary = when (val v = args.getOrNull(2)) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                else -> false
            }
            instances[(args[0] as Number).toInt()]?.send(args[1].toString(), binary)
            null
        }
        "close" -> {
            instances[(args[0] as Number).toInt()]?.close(
                (args.getOrNull(1) as? Number)?.toInt() ?: NORMAL_CLOSURE,
                args.getOrNull(2)?.toString().orEmpty(),
            )
            null
        }
        else -> error("Unknown method: $method")
    }

    private fun createInstance(url: String, protocols: String, headers: Map<String, String>): Int {
        val id = ++lastInstance
        val instance = Instance(id, url, protocols, headers)
        instances[id] = instance
        instance.connect()
        return id
    }

    override fun close() {
        instances.values.toList().forEach { it.cancel() }
        instances.clear()
    }

    private fun handshakeHeaders(json: String): Map<String, String> =
        runCatching { Json.decodeFromString<Map<String, String>>(json) }.getOrDefault(emptyMap())
            .filterKeys { name ->
                name.lowercase().let { it !in UPGRADE_HEADERS && !it.startsWith("sec-websocket-") }
            }

    private inner class Instance(
        val id: Int,
        private val url: String,
        private val protocols: String,
        private val extraHeaders: Map<String, String>,
    ) {
        private val js = "WebSocket._instances.get($id)"
        private val outgoing = Channel<Frame>(Channel.UNLIMITED)
        private var job: Job? = null

        private val session = atomic<DefaultClientWebSocketSession?>(null)

        fun connect() {
            job = scope.launch { run() }
        }

        private suspend fun run() {
            try {
                val verdict = networkAccess.check(url, wait = false)
                if (verdict is PluginNetworkVerdict.Denied) {
                    logger.w { "$appUuid blocked: ${verdict.reason}" }
                    fail()
                    return
                }
                val ws = client.webSocketSession(url) {
                    extraHeaders.forEach { (name, value) -> header(name, value) }
                    if (protocols.isNotEmpty()) header(HttpHeaders.SecWebSocketProtocol, protocols)
                }
                session.value = ws
                val negotiated = ws.call.response.headers[HttpHeaders.SecWebSocketProtocol].orEmpty()
                eval("$js._onOpen(${Json.encodeToString(negotiated)})")
                coroutineScope {
                    val sender = launch { for (frame in outgoing) ws.send(frame) }
                    for (frame in ws.incoming) {
                        when (frame) {
                            is Frame.Text -> eval("$js._onMessage(${Json.encodeToString(frame.readText())}, false)")
                            is Frame.Binary -> eval("$js._onMessage(${Json.encodeToString(frame.data.encodeBase64())}, true)")
                            else -> Unit
                        }
                    }
                    sender.cancel()
                }
                val reason = ws.closeReason.await()
                val code = reason?.code?.toInt() ?: NO_STATUS_RECEIVED
                eval("$js._onClose($code, ${Json.encodeToString(reason?.message.orEmpty())}, true)")
            } catch (e: CancellationException) {
                eval("$js._onClose($ABNORMAL_CLOSURE, '', false)")
                throw e
            } catch (e: Exception) {
                logger.w(e) { "$appUuid websocket to $url failed: ${e.message}" }
                fail()
            } finally {
                session.value?.cancel()
                instances.remove(id)
            }
        }

        private fun fail() {
            eval("$js._onError()")
            eval("$js._onClose($ABNORMAL_CLOSURE, '', false)")
        }

        fun send(data: String, binary: Boolean) {
            val frame = if (binary) Frame.Binary(true, data.decodeBase64Bytes()) else Frame.Text(data)
            outgoing.trySend(frame)
        }

        fun close(code: Int, reason: String) {
            // Not connected yet: give up on the connect, which reports an abnormal close.
            if (session.value == null) {
                job?.cancel()
                return
            }
            outgoing.trySend(Frame.Close(CloseReason(code.toShort(), reason)))
        }

        fun cancel() {
            job?.cancel()
        }
    }
}
