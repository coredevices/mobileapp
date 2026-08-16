package io.rebble.libpebblecommon.connection.devconnection

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.send
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class CloudpebbleProxyProtocolVersion {
    V1,
    V2
}

class DevConnectionCloudpebbleProxy(
    libPebble: LibPebble,
    private val url: String,
    private val protocolVersion: CloudpebbleProxyProtocolVersion,
    private val scope: LibPebbleCoroutineScope,
    private val token: StateFlow<String?>
): DevConnectionTransport(libPebble) {
    private val client = HttpClient {
        install(WebSockets)
    }
    private var job: Job? = null
    private val logger = Logger.withTag("DevConnectionCloudpebbleProxy")
    private var session: WebSocketSession? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY

    override suspend fun start(
        identifier: PebbleIdentifier,
        inboundPKJSLogs: Flow<String>,
        inboundDeviceMessages: Flow<ByteArray>,
        outboundDeviceMessages: suspend (ByteArray) -> Unit
    ) {
        reconnectDelay = INITIAL_RECONNECT_DELAY
        job = scope.launch {
            while (true) {
                val currentToken = token.filterNotNull().first()
                try {
                    runSession(currentToken, inboundPKJSLogs, inboundDeviceMessages, outboundDeviceMessages)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "CloudPebble proxy connection lost" }
                } finally {
                    session = null
                }
                delay(reconnectDelay)
                reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
            }
        }
    }

    private suspend fun runSession(
        currentToken: String,
        inboundPKJSLogs: Flow<String>,
        inboundDeviceMessages: Flow<ByteArray>,
        outboundDeviceMessages: suspend (ByteArray) -> Unit
    ) {
        coroutineScope {
            session = client.webSocketSession(
                urlString = url
            )
            session?.apply {
                when (protocolVersion) {
                    CloudpebbleProxyProtocolVersion.V1 -> send(ProxyAuthenticationMessage(currentToken))
                    CloudpebbleProxyProtocolVersion.V2 -> send(ProxyAuthenticationMessageV2(currentToken))
                }
                val authResultPacket = withTimeout(5.seconds) {
                    incoming.receive() as? Frame.Binary
                }
                val authResult = authResultPacket?.data[0] == ServerMessageType.ProxyAuthentication.value && authResultPacket.data[1] == 0.toByte()
                if (!authResult) {
                    logger.w { "Authentication failed" }
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication failed"))
                    return@apply
                } else {
                    logger.i { "Authentication successful" }
                    reconnectDelay = INITIAL_RECONNECT_DELAY
                }
                delay(10)
                val relays = listOf(
                    launch {
                        delay(100)
                        send(ConnectionStatusUpdateMessage(true))
                        inboundDeviceMessages.collect {
                            send(byteArrayOf(ServerMessageType.RelayFromWatch.value) + it)
                        }
                    },
                    launch {
                        inboundPKJSLogs.collect {
                            send(PhoneAppLogMessage(it))
                        }
                    },
                )
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Binary -> {
                                logger.d { "Received binary frame of size ${frame.data.size}" }
                                val data = frame.data
                                if (data.isEmpty()) {
                                    logger.w { "Received empty binary frame" }
                                    continue
                                }
                                val messageType = ClientMessageType.fromValue(data[0])
                                val payload = data.copyOfRange(1, data.size)

                                when (messageType) {
                                    ClientMessageType.RelayToWatch -> {
                                        logger.d { "Relaying message to watch" }
                                        outboundDeviceMessages(payload)
                                    }
                                    ClientMessageType.InstallBundle -> {
                                        logger.d { "Received InstallBundle message with payload size ${payload.size}" }
                                        send(InstallStatusMessage(installPBW(payload)))
                                    }
                                    // Handle other message types as needed
                                    ClientMessageType.TimelinePin -> {
                                        logger.d { "Received TimelinePin message with payload size ${payload.size}" }
                                        val message = "Mobile app currently doesn't support operation."
                                        send(PhoneAppLogMessage(message))
                                        close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, message))
                                    }
                                    ClientMessageType.ConnectionStatus -> {
                                        val connected = payload.getOrNull(0)?.toInt() != 0
                                        logger.i { "Client connection status changed: ${if (connected) "Connected" else "Disconnected"}" }
                                    }
                                    null -> {
                                        logger.w { "Received unsupported or unknown message type: ${data[0]}" }
                                        val message = "Unknown operation."
                                        send(PhoneAppLogMessage(message))
                                        close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, message))
                                    }
                                }
                            }
                            else -> {
                                logger.w { "Received unsupported frame type: ${frame.frameType}" }
                            }
                        }
                    }
                } finally {
                    relays.forEach { it.cancel() }
                }
            }
        }
    }

    override suspend fun stop() {
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Stopped by user"))
        job?.cancel()
        job = null
    }

    companion object {
        private val INITIAL_RECONNECT_DELAY: Duration = 1.seconds
        private val MAX_RECONNECT_DELAY: Duration = 1.minutes
    }
}