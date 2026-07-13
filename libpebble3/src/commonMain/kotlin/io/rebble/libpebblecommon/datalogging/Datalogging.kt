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

    // Non-health, non-system datalogging, exposed for PebbleKit to deliver to companion apps
    // (was dropped). Parity with legacy PebbleDataLogReceiver.
    private val _thirdPartyData = MutableSharedFlow<ThirdPartyDatalogRecord>(extraBufferCapacity = 128)
    val thirdPartyData: SharedFlow<ThirdPartyDatalogRecord> = _thirdPartyData.asSharedFlow()

    // Third-party session close, so companions can finalize a recording (legacy FINISH_SESSION).
    private val _thirdPartyFinished = MutableSharedFlow<ThirdPartyDatalogFinish>(extraBufferCapacity = 16)
    val thirdPartyFinished: SharedFlow<ThirdPartyDatalogFinish> = _thirdPartyFinished.asSharedFlow()

    fun logData(
        sessionId: UByte,
        uuid: Uuid,
        tag: UInt,
        data: ByteArray,
        watchInfo: WatchInfo,
        itemSize: UShort,
        itemsLeft: UInt,
        timestamp: UInt = 0u,
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

        // Third-party app data — deliver to companion apps instead of dropping it.
        if (!_thirdPartyData.tryEmit(ThirdPartyDatalogRecord(uuid, tag, timestamp, itemSize, itemsLeft, data))) {
            logger.w { "third-party datalog buffer full; dropped ${data.size} B for $uuid" }
        }
    }

    fun openSession(sessionId: UByte, tag: UInt, applicationUuid: Uuid, itemSize: UShort) {
        if (tag in HealthDataProcessor.HEALTH_TAGS) {
            healthDataProcessor.handleSessionOpen(sessionId, tag, applicationUuid, itemSize)
        }
    }

    fun closeSession(sessionId: UByte, tag: UInt, uuid: Uuid = Uuid.NIL, timestamp: UInt = 0u) {
        if (tag in HealthDataProcessor.HEALTH_TAGS) {
            healthDataProcessor.handleSessionClose(sessionId)
            return
        }
        if (uuid == SYSTEM_APP_UUID) return
        // Third-party session finished — let companion apps finalize the recording.
        _thirdPartyFinished.tryEmit(ThirdPartyDatalogFinish(uuid, tag, timestamp))
    }

    companion object {
        private val MEMFAULT_CHUNKS_TAG: UInt = 86u
        private val ANALYTICS_HEARTBEAT_TAG: UInt = 87u
    }
}

/** A batch of datalogging items from a third-party watchapp, for delivery to companion apps. */
data class ThirdPartyDatalogRecord(
    val uuid: Uuid,
    val tag: UInt,
    val timestamp: UInt,   // session start epoch — stable session id
    val itemSize: UShort,
    val itemsLeft: UInt,
    val data: ByteArray,
)

/** A third-party datalogging session closing (watch called data_logging_finish). */
data class ThirdPartyDatalogFinish(
    val uuid: Uuid,
    val tag: UInt,
    val timestamp: UInt,
)

class MemfaultChunk : StructMappable() {
    val chunkSize: SUInt = SUInt(m, 0u, Endian.Little)
    val bytes: SBytes = SBytes(m).apply { linkWithSize(chunkSize) }
}
