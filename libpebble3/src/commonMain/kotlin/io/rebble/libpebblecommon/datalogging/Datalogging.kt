package io.rebble.libpebblecommon.datalogging

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.connection.CustomDataLogging
import io.rebble.libpebblecommon.connection.CustomDataLoggingEvent
import io.rebble.libpebblecommon.connection.CustomDataLoggingSink
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.uuid.Uuid

class Datalogging(
    private val webServices: WebServices,
    private val healthDataProcessor: HealthDataProcessor,
    libPebbleScope: LibPebbleCoroutineScope,
) : CustomDataLogging {
    private val logger = Logger.withTag("Datalogging")

    private val _customData =
        MutableSharedFlow<CustomDataLoggingEvent>(
            extraBufferCapacity = BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val customData: SharedFlow<CustomDataLoggingEvent> = _customData.asSharedFlow()
    private val sinkRef = AtomicReference<CustomDataLoggingSink?>(null)
    private val sinkChannel =
        Channel<CustomDataLoggingEvent>(
            capacity = SINK_CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    init {
        libPebbleScope.launch {
            for (event in sinkChannel) {
                val sink = sinkRef.load() ?: continue
                try {
                    sink.onData(event)
                } catch (e: Throwable) {
                    logger.e(e) { "Sink threw for tag=${event.tag} uuid=${event.appUuid}" }
                }
            }
        }
    }

    override fun setDataSink(sink: CustomDataLoggingSink?) {
        sinkRef.store(sink)
    }

    suspend fun logData(
        sessionId: UByte,
        uuid: Uuid,
        tag: UInt,
        data: ByteArray,
        watchInfo: WatchInfo,
        itemSize: UShort,
        itemsLeft: UInt,
    ) {
        // Handle health tags
        if (tag in HealthDataProcessor.HEALTH_TAGS) {
            healthDataProcessor.handleSendDataItems(sessionId, data, itemsLeft)
            return
        }

        // Handle system-app datalogging tags
        if (uuid == SYSTEM_APP_UUID) {
            when (tag) {
                MEMFAULT_CHUNKS_TAG -> {
                    // A single SendDataItems payload can contain multiple items,
                    // each itemSize bytes. Parse each one as a MemfaultChunk.
                    val size = itemSize.toInt()
                    var offset = 0
                    while (offset + size <= data.size) {
                        val itemData = data.copyOfRange(offset, offset + size)
                        val chunk = MemfaultChunk()
                        chunk.fromBytes(DataBuffer(itemData.toUByteArray()))
                        webServices.uploadMemfaultChunk(chunk.bytes.get().toByteArray(), watchInfo)
                        offset += size
                    }
                }

                ANALYTICS_HEARTBEAT_TAG -> {
                    // Fixed-size native_heartbeat_record items (no inner length prefix).
                    val size = itemSize.toInt()
                    if (size <= 0) {
                        logger.w { "Analytics heartbeat with itemSize=$size; ignoring" }
                        return
                    }
                    var offset = 0
                    while (offset + size <= data.size) {
                        val itemData = data.copyOfRange(offset, offset + size)
                        webServices.uploadAnalyticsHeartbeat(itemData, watchInfo)
                        offset += size
                    }
                }
            }
            return
        }
        val event =
            CustomDataLoggingEvent(
                sessionId = sessionId,
                appUuid = uuid,
                tag = tag,
                data = data,
                itemSize = itemSize,
                itemsLeft = itemsLeft,
            )

        if (sinkRef.load() != null) {
            sinkChannel.send(event)
        }
        _customData.tryEmit(event)
    }

    fun openSession(
        sessionId: UByte,
        tag: UInt,
        applicationUuid: Uuid,
        itemSize: UShort,
    ) {
        if (tag in HealthDataProcessor.HEALTH_TAGS) {
            healthDataProcessor.handleSessionOpen(sessionId, tag, applicationUuid, itemSize)
        }
    }

    fun closeSession(
        sessionId: UByte,
        tag: UInt,
    ) {
        if (tag in HealthDataProcessor.HEALTH_TAGS) {
            healthDataProcessor.handleSessionClose(sessionId)
        }
    }

    companion object {
        private val MEMFAULT_CHUNKS_TAG: UInt = 86u
        private val ANALYTICS_HEARTBEAT_TAG: UInt = 87u
        private const val SINK_CHANNEL_CAPACITY = 256
        private const val BUFFER_CAPACITY = 256
    }
}

class MemfaultChunk : StructMappable() {
    val chunkSize: SUInt = SUInt(m, 0u, Endian.Little)
    val bytes: SBytes = SBytes(m).apply { linkWithSize(chunkSize) }
}
