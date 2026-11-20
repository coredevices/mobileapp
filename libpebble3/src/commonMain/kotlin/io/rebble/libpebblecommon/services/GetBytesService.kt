package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.GetBytesCoreDump
import io.rebble.libpebblecommon.packets.GetBytesError
import io.rebble.libpebblecommon.packets.GetBytesError.Companion.fromCode
import io.rebble.libpebblecommon.packets.GetBytesInboundMessage
import io.rebble.libpebblecommon.util.getTempFilePath
import io.rebble.libpebblecommon.util.randomCookieByte
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Duration.Companion.seconds

internal class GetBytesService(
    private val protocolHandler: PebbleProtocolHandler,
    private val connectionCoroutineScope: ConnectionCoroutineScope,
    private val appContext: AppContext,
    private val identifier: PebbleIdentifier,
) : ProtocolService, ConnectedPebble.CoreDump {
    private val logger = Logger.withTag("GetBytesService")

    override suspend fun getCoreDump(unread: Boolean): Path? {
        val transactionId = randomCookieByte()
        protocolHandler.handleMessages<GetBytesInboundMessage>(connectionCoroutineScope) { messages ->
            protocolHandler.send(GetBytesCoreDump(unread = unread, transactionId = transactionId))
            val imageInfo = messages.receive()
            if (imageInfo !is GetBytesInboundMessage.GetBytesImageInfo) {
                logger.w("expected $imageInfo to be GetBytesImageInfo")
                return null
            }
            if (imageInfo.transactionId.get() != transactionId) {
                logger.w("wrong transactionId")
                return null
            }
            val error = fromCode(imageInfo.errorCode.get())
            if (error != GetBytesError.NO_ERROR) {
                logger.w("getbytes error: $error")
                return null
            }
            val numBytes = imageInfo.numBytes.get()
            var bytesRemaining = numBytes
            val tempFile = getTempFilePath(appContext, "coredump-${identifier.asString}")
            SystemFileSystem.sink(tempFile).buffered().use { sink ->
                while (bytesRemaining > 0u) {
                    val packet = withTimeoutOrNull(TIMEOUT) { messages.receive() }
                    if (packet !is GetBytesInboundMessage.GetBytesImageData) {
                        logger.w("expected $imageInfo to be GetBytesImageData")
                        return null
                    }
                    if (packet.transactionId.get() != transactionId) {
                        logger.w("wrong transactionId")
                        return null
                    }
                    val expectedOffset = numBytes - bytesRemaining
                    if (packet.offset.get() != expectedOffset) {
                        logger.w("expected offset to be $expectedOffset but it was ${packet.offset.get()}")
                        return null
                    }
                    val bytes = packet.data.get()
                    if (bytes.isEmpty()) {
                        logger.w("expected some bytes")
                        return null
                    }
                    sink.write(bytes.asByteArray())
                    bytesRemaining -= bytes.size.toUInt()
                }
                logger.d("finished coredump!")
                return tempFile
            }
        }
        logger.w("not sure how we got here")
        return null
    }

    companion object {
        private val TIMEOUT = 5.seconds
    }
}

/**
 * Collect all messages of type <T> into a channel. Only collect while [block] is running.
 *
 * This means that you can be safe in the knowledge that you will not miss any inbound messages
 * while thinking about other things.
 */
internal inline fun <reified T> PebbleProtocolHandler.handleMessages(
    connectionCoroutineScope: ConnectionCoroutineScope,
    block: (messages: ReceiveChannel<T>) -> Unit,
) {
    val channel = Channel<T>(capacity = 100)
    val collectJob = connectionCoroutineScope.launch {
        inboundMessages.filterIsInstance<T>()
            .collect {
                Logger.v("collecting message: $it") // FIXME
                channel.send(it)
            }
    }
    try {
        block(channel)
    } finally {
        collectJob.cancel()
    }
}