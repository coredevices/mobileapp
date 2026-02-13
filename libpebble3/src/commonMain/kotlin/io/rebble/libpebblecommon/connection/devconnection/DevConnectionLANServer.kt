package io.rebble.libpebblecommon.connection.devconnection

import co.touchlab.kermit.Logger
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.core.writeFully
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.send
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.structmapper.SByte
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.StructMappable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class DevConnectionServer(libPebble: LibPebble): DevConnectionTransport(libPebble) {
    companion object {
        private const val PORT = 9000
        private val logger = Logger.withTag("DevConnectionServer")
    }
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    override suspend fun start(
        identifier: PebbleIdentifier,
        inboundPKJSLogs: Flow<String>,
        inboundDeviceMessages: Flow<ByteArray>,
        outboundDeviceMessages: suspend (ByteArray) -> Unit
    ) {
        server?.stopSuspend()
        logger.i { "Starting server for $identifier on port $PORT" }
        val newServer = embeddedServer(CIO, configure = {
            connectors.add(EngineConnectorBuilder().apply {
                host = "0.0.0.0"
                port = PORT
            })
            connectionGroupSize = 1
            workerGroupSize = 2
            callGroupSize = 2
        }) {
            install(WebSockets)
            routing {
                webSocket("/") {
                    logger.i { "WebSocket connection established for $identifier" }
                    launch {
                        val reason = this@webSocket.closeReason.await()
                        logger.i { "WebSocket connection closed for $identifier: (${reason?.code}) ${reason?.message ?: "No reason provided"}" }
                    }
                    launch {
                        send(ConnectionStatusUpdateMessage(true))
                        inboundDeviceMessages.collect {
                            send(byteArrayOf(ServerMessageType.RelayFromWatch.value) + it)
                        }
                    }
                    launch {
                        inboundPKJSLogs.collect {
                            send(PhoneAppLogMessage(it))
                        }
                    }
                    delay(10) //XXX: Give the client a moment to set up the connection
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Binary -> {
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
                }
            }
        }
        // Assign before starting so stop() can always reach the server,
        // even if startSuspend() is cancelled by collectLatest.
        server = newServer
        try {
            newServer.startSuspend()
        } catch (e: CancellationException) {
            newServer.stop()
            throw e
        } catch (e: Exception) {
            newServer.stop()
            server = null
            logger.e(e) { "Failed to start dev connection server on port $PORT" }
        }
    }

    override fun stop() {
        server?.stop()
        server = null
        logger.i { "Server stopped" }
    }
}

suspend fun WebSocketSession.send(message: StructMappable) =
    send(message.toBytes().asByteArray())