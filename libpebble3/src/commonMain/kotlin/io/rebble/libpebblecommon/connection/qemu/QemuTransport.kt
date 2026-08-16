package io.rebble.libpebblecommon.connection.qemu

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeFully
import io.rebble.libpebblecommon.connection.ConnectionFailureReason
import io.rebble.libpebblecommon.connection.KnownWatchProperties
import io.rebble.libpebblecommon.connection.PebbleConnectionResult
import io.rebble.libpebblecommon.connection.PebbleProtocolStreams
import io.rebble.libpebblecommon.connection.PebbleSocketIdentifier
import io.rebble.libpebblecommon.connection.TransportConnector
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Talks Pebble Protocol to a PebbleOS QEMU emulator over TCP, in place of a watch.
 *
 * The emulator has no Bluetooth stack — its BT driver is a stub that hands bytes straight to
 * comm_session — so this speaks the QEMU serial framing directly, the same way the legacy Android
 * app's QemuDeviceConnector did.
 */
class QemuTransport(
    private val identifier: PebbleSocketIdentifier,
    private val pebbleProtocolStreams: PebbleProtocolStreams,
    private val connectionCoroutineScope: ConnectionCoroutineScope,
) : TransportConnector {
    private val logger = Logger.withTag("QemuTransport-${identifier.address}")
    private val _disconnected = CompletableDeferred<ConnectionFailureReason>()
    private var socket: Socket? = null
    private var selector: SelectorManager? = null

    override suspend fun connect(
        knownWatchProperties: KnownWatchProperties?,
        lastError: ConnectionFailureReason?,
    ): PebbleConnectionResult {
        val host = identifier.address.substringBeforeLast(':')
        val port = identifier.address.substringAfterLast(':').toIntOrNull()
        if (host.isBlank() || port == null) {
            logger.w { "malformed address, expected host:port" }
            return PebbleConnectionResult.Failed(ConnectionFailureReason.SocketConnectionFailed)
        }

        val selectorManager = SelectorManager(Dispatchers.Default)
        selector = selectorManager
        val connected = try {
            withContext(Dispatchers.Default) {
                aSocket(selectorManager).tcp().connect(host, port)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "failed to connect" }
            selectorManager.close()
            return PebbleConnectionResult.Failed(ConnectionFailureReason.SocketConnectionFailed)
        }
        socket = connected

        val read = connected.openReadChannel()
        val write = connected.openWriteChannel(autoFlush = false)

        // The firmware discards SPP data unless a CommSession is open.
        write.writeFrame(QemuFraming.PROTOCOL_BLUETOOTH_CONNECTION, byteArrayOf(1))

        connectionCoroutineScope.launch { readLoop(read) }
        connectionCoroutineScope.launch { writeLoop(write) }
        return PebbleConnectionResult.Success(null)
    }

    private suspend fun readLoop(read: ByteReadChannel) {
        val parser = QemuFrameParser()
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val count = read.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) continue
                for (frame in parser.feed(buffer.copyOf(count))) {
                    if (frame.protocol != QemuFraming.PROTOCOL_SPP) continue
                    pebbleProtocolStreams.inboundPPBytes.writeByteArray(frame.payload)
                    pebbleProtocolStreams.inboundPPBytes.flush()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "read loop ended" }
        } finally {
            cleanup()
        }
    }

    private suspend fun writeLoop(write: ByteWriteChannel) {
        try {
            pebbleProtocolStreams.outboundPPBytes.consumeAsFlow().collect { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + QemuFraming.MAX_DATA_LEN, bytes.size)
                    write.writeFrame(QemuFraming.PROTOCOL_SPP, bytes.copyOfRange(offset, end))
                    offset = end
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "write loop ended" }
        } finally {
            cleanup()
        }
    }

    private suspend fun ByteWriteChannel.writeFrame(protocol: Int, payload: ByteArray) {
        writeFully(QemuFraming.frame(protocol, payload))
        flush()
    }

    /**
     * Idempotent: the socket outlives [disconnect] when the peer goes away and the connection
     * scope is cancelled instead, so whichever of the two loops ends first closes it.
     */
    private fun cleanup() {
        _disconnected.complete(ConnectionFailureReason.SocketDisconnected)
        socket?.takeIf { !it.isClosed }?.close()
        socket = null
        selector?.close()
        selector = null
    }

    override suspend fun disconnect() = cleanup()

    override val disconnected: Deferred<ConnectionFailureReason> = _disconnected
}
