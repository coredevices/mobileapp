package io.rebble.libpebblecommon.services.appmessage

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.AppCustomizationSetStockAppIconMessage
import io.rebble.libpebblecommon.packets.AppCustomizationSetStockAppTitleMessage
import io.rebble.libpebblecommon.packets.AppMessage
import io.rebble.libpebblecommon.packets.AppMessageTuple
import io.rebble.libpebblecommon.services.ProtocolService
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val APPMESSAGE_BUFFER_SIZE = 32
private const val MAX_SUBSCRIBERS_PER_APP = 2
private val APPMESSAGE_TIMEOUT = 10.seconds

class AppMessageService(
    private val protocolHandler: PebbleProtocolHandler,
    private val scope: ConnectionCoroutineScope
) : ProtocolService, ConnectedPebble.AppMessages {
    private val logger = Logger.withTag("AppMessageService")
    private val channelGroups = HashMap<Uuid, ChannelGroup>()
    override val transactionSequence: Iterator<UByte> = AppMessageTransactionSequence().iterator()
    private val mapAccessMutex = Mutex()

    private class ChannelGroup {
        val all = mutableListOf<Channel<AppMessageData>>()
        val unclaimed = ArrayDeque<Channel<AppMessageData>>()

        init {
            repeat(MAX_SUBSCRIBERS_PER_APP) {
                val ch = Channel<AppMessageData>(APPMESSAGE_BUFFER_SIZE)
                all.add(ch)
                unclaimed.addLast(ch)
            }
        }

        fun claim(): Channel<AppMessageData> {
            return unclaimed.removeFirstOrNull()
                ?: error("All $MAX_SUBSCRIBERS_PER_APP AppMessage channels already claimed")
        }

        fun release(channel: Channel<AppMessageData>) {
            unclaimed.addLast(channel)
        }
    }

    fun init() {
        protocolHandler.inboundMessages.onEach {
            when (it) {
                is AppMessage.AppMessagePush -> {
                    val data = it.appMessageData()
                    val channels = mapAccessMutex.withLock {
                        getOrCreateGroup(it.uuid.get()).all.toList()
                    }
                    channels.forEach { channel -> channel.trySend(data) }
                }
            }
        }.launchIn(scope)
    }

    private fun getOrCreateGroup(appUuid: Uuid): ChannelGroup {
        return channelGroups.getOrPut(appUuid) { ChannelGroup() }
    }

    /**
     * Send an AppMessage
     */
    override suspend fun sendAppMessage(appMessageData: AppMessageData): AppMessageResult {
        val appMessage = AppMessage.AppMessagePush(
            transactionId = appMessageData.transactionId,
            uuid = appMessageData.uuid,
            tuples = appMessageData.data.toAppMessageTuples()
        )
        val result = scope.async {
            withTimeoutOrNull(APPMESSAGE_TIMEOUT) {
                protocolHandler.inboundMessages.first {
                    it is AppMessage && (it is AppMessage.AppMessageACK || it is AppMessage.AppMessageNACK)
                            && it.transactionId.get() == appMessageData.transactionId
                }
            } ?: run {
                logger.w { "Timed out sending AppMessage ${appMessageData.transactionId}" }
                AppMessageResult.NACK(appMessageData.transactionId)
            }
        }
        protocolHandler.send(appMessage)
        return when (val msg = result.await()) {
            is AppMessage.AppMessageACK -> msg.appMessageResult()
            is AppMessage.AppMessageNACK -> msg.appMessageResult()
            else -> throw IllegalStateException("Unexpected result: $result")
        }
    }

    override suspend fun sendAppMessageResult(appMessageResult: AppMessageResult) {
        val appMessage = when (appMessageResult) {
            is AppMessageResult.ACK -> AppMessage.AppMessageACK(appMessageResult.transactionId)
            is AppMessageResult.NACK -> AppMessage.AppMessageNACK(appMessageResult.transactionId)
        }
        protocolHandler.send(appMessage)
    }

    suspend fun send(packet: AppCustomizationSetStockAppIconMessage) {
        protocolHandler.send(packet)
    }

    suspend fun send(packet: AppCustomizationSetStockAppTitleMessage) {
        protocolHandler.send(packet)
    }

    override fun inboundAppMessages(appUuid: Uuid): Flow<AppMessageData> = channelFlow {
        val channel = mapAccessMutex.withLock {
            getOrCreateGroup(appUuid).claim()
        }
        try {
            for (msg in channel) {
                send(msg)
            }
        } finally {
            mapAccessMutex.withLock {
                channelGroups[appUuid]?.release(channel)
            }
        }
    }
}

private fun Map<Int, Any>.toAppMessageTuples(): List<AppMessageTuple> {
    return map { (key, value) ->
        val k = key.toUInt()
        when (value) {
            is String -> AppMessageTuple.createString(k, value)
            is UByteArray -> AppMessageTuple.createUByteArray(k, value)
            is ByteArray -> AppMessageTuple.createUByteArray(k, value.asUByteArray())
            is Int -> AppMessageTuple.createInt(k, value)
            is UInt -> AppMessageTuple.createUInt(k, value)
            is Short -> AppMessageTuple.createShort(k, value)
            is UShort -> AppMessageTuple.createUShort(k, value)
            is Byte -> AppMessageTuple.createByte(k, value)
            is UByte -> AppMessageTuple.createUByte(k, value)
            is Boolean -> AppMessageTuple.createShort(k, if (value) 1 else 0)
            else -> throw IllegalArgumentException("Unsupported type: ${value::class.simpleName}")
        }
    }
}
typealias AppMessageDictionary = Map<Int, Any>

data class AppMessageData(
    val transactionId: UByte,
    val uuid: Uuid,
    val data: AppMessageDictionary
)

sealed class AppMessageResult(val transactionId: UByte) {
    class ACK(transactionId: UByte) : AppMessageResult(transactionId)
    class NACK(transactionId: UByte) : AppMessageResult(transactionId)
}

private fun AppMessage.AppMessagePush.appMessageData(): AppMessageData {
    return AppMessageData(
        transactionId = transactionId.get(),
        uuid = uuid.get(),
        data = dictionary.list.associate { it.key.get().toInt() to it.getTypedData() }
    )
}

private fun AppMessage.AppMessageACK.appMessageResult() = AppMessageResult.ACK(transactionId.get())
private fun AppMessage.AppMessageNACK.appMessageResult() = AppMessageResult.NACK(transactionId.get())
