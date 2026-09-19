package io.rebble.libpebblecommon.datalogging

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.uuid.Uuid

class Datalogging(
    private val webServices: WebServices,
    private val healthDataProcessor: HealthDataProcessor,
) {
    private val logger = Logger.withTag("Datalogging")

    private val _thirdPartyEvents = MutableSharedFlow<ThirdPartyDatalogEvent>(extraBufferCapacity = 256)

    /** Data logging events of third-party watchapps, in protocol order. */
    val thirdPartyEvents: SharedFlow<ThirdPartyDatalogEvent> = _thirdPartyEvents.asSharedFlow()

    fun logData(
        sessionId: UByte,
        uuid: Uuid,
        tag: UInt,
        timestamp: UInt,
        data: ByteArray,
        watchInfo: WatchInfo,
        itemSize: UShort,
        itemsLeft: UInt,
    ) {
        if (uuid == SYSTEM_APP_UUID) {
            // Handle health tags
            if (tag in HealthDataProcessor.HEALTH_TAGS) {
                healthDataProcessor.handleSendDataItems(sessionId, data, itemsLeft)
                return
            }

            // Handle system-app datalogging tags
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

        // A third-party session: emit it for delivery to companion apps
        if (itemSize.toInt() <= 0 || data.size % itemSize.toInt() != 0) {
            logger.w { "Dropped a malformed datalogging batch (uuid=$uuid tag=$tag size=${data.size} itemSize=$itemSize)" }
            return
        }
        val batch = ThirdPartyDatalogEvent.Batch(
            uuid = uuid,
            tag = tag,
            timestamp = timestamp,
            itemSize = itemSize,
            watchSerial = watchInfo.serial,
            itemsLeft = itemsLeft,
            data = data,
        )
        if (!_thirdPartyEvents.tryEmit(batch)) {
            logger.w { "Third-party datalogging buffer is full; dropped a batch (uuid=$uuid tag=$tag)" }
        }
    }

    fun openSession(sessionId: UByte, tag: UInt, applicationUuid: Uuid, itemSize: UShort) {
        if (applicationUuid == SYSTEM_APP_UUID && tag in HealthDataProcessor.HEALTH_TAGS) {
            healthDataProcessor.handleSessionOpen(sessionId, tag, applicationUuid, itemSize)
        }
    }

    fun closeSession(
        sessionId: UByte,
        tag: UInt,
        uuid: Uuid,
        timestamp: UInt,
        itemSize: UShort,
        watchSerial: String?,
    ) {
        if (uuid == SYSTEM_APP_UUID) {
            if (tag in HealthDataProcessor.HEALTH_TAGS) {
                healthDataProcessor.handleSessionClose(sessionId)
            }
            return
        }
        if (watchSerial == null) {
            return
        }
        val finished = ThirdPartyDatalogEvent.Finished(uuid, tag, timestamp, itemSize, watchSerial)
        if (!_thirdPartyEvents.tryEmit(finished)) {
            logger.w { "Third-party datalogging buffer is full; dropped a session finish (uuid=$uuid tag=$tag)" }
        }
    }

    companion object {
        private val MEMFAULT_CHUNKS_TAG: UInt = 86u
        private val ANALYTICS_HEARTBEAT_TAG: UInt = 87u
    }
}

class MemfaultChunk : StructMappable() {
    val chunkSize: SUInt = SUInt(m, 0u, Endian.Little)
    val bytes: SBytes = SBytes(m).apply { linkWithSize(chunkSize) }
}

/**
 * A data logging event of a third-party watchapp. [uuid], [tag] and [timestamp] identify the
 * session; all items of a session are [itemSize] bytes.
 */
sealed interface ThirdPartyDatalogEvent {
    val uuid: Uuid
    val tag: UInt
    val timestamp: UInt
    val itemSize: UShort
    val watchSerial: String

    /** One batch of whole items. [itemsLeft] is the number of items that stay on the watch. */
    data class Batch(
        override val uuid: Uuid,
        override val tag: UInt,
        override val timestamp: UInt,
        override val itemSize: UShort,
        override val watchSerial: String,
        val itemsLeft: UInt,
        val data: ByteArray,
    ) : ThirdPartyDatalogEvent

    /** The session is complete and the watch sent all its data. */
    data class Finished(
        override val uuid: Uuid,
        override val tag: UInt,
        override val timestamp: UInt,
        override val itemSize: UShort,
        override val watchSerial: String,
    ) : ThirdPartyDatalogEvent
}
