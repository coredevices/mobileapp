package io.rebble.libpebblecommon.datalogging

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.dao.KnownWatchDao
import io.rebble.libpebblecommon.database.dao.insertHealthDataWithPriority
import io.rebble.libpebblecommon.database.dao.insertOverlayDataWithDeduplication
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.health.parsers.parseOverlayData
import io.rebble.libpebblecommon.health.parsers.parseStepsData
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class Datalogging(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val webServices: WebServices,
    private val healthDao: HealthDao,
    private val knownWatchDao: KnownWatchDao,
) {
    private val logger = Logger.withTag("Datalogging")

    fun logData(uuid: Uuid, tag: UInt, data: ByteArray, watchInfo: WatchInfo, itemSize: UShort) {
        if (uuid == SYSTEM_APP_UUID) {
            when (tag) {
                MEMFAULT_CHUNKS_TAG -> {
                    libPebbleCoroutineScope.launch {
                        val chunk = MemfaultChunk()
                        chunk.fromBytes(DataBuffer(data.toUByteArray()))
                        val chunkBytes = chunk.bytes.get()
                        webServices.uploadMemfaultChunk(chunkBytes.toByteArray(), watchInfo)
                    }
                }
                HEALTH_STEPS_TAG -> {
                    libPebbleCoroutineScope.launch {
                        logger.i {
                            "Received HEALTH_STEPS data: ${data.size} bytes, itemSize=$itemSize"
                        }

                        // Parse the health steps data using the centralized parser
                        val records = parseStepsData(data, itemSize)
                        if (records.isEmpty()) {
                            logger.w { "No health step records parsed" }
                            return@launch
                        }

                        logger.i {
                            "Parsed ${records.size} health step records (total steps: ${records.sumOf { it.steps }}, time range: ${records.firstOrNull()?.timestamp}-${records.lastOrNull()?.timestamp})"
                        }

                        // Filter out health data recorded before this watch was last connected/selected
                        // This prevents stale resting calories from being dumped when switching watches
                        val filteredRecords = filterStaleHealthData(records, watchInfo)
                        if (filteredRecords.size < records.size) {
                            logger.i {
                                "HEALTH_FILTER: Filtered out ${records.size - filteredRecords.size} stale health records recorded before watch was last selected (kept ${filteredRecords.size})"
                            }
                        }

                        healthDao.insertHealthDataWithPriority(filteredRecords)
                        logger.d { "Inserted ${filteredRecords.size} health records into database" }
                    }
                }
                HEALTH_OVERLAY_TAG -> {
                    libPebbleCoroutineScope.launch {
                        logger.i {
                            "Received HEALTH_OVERLAY data: ${data.size} bytes, itemSize=$itemSize"
                        }

                        // Parse the health overlay data using the centralized parser
                        val records = parseOverlayData(data, itemSize)
                        if (records.isEmpty()) {
                            logger.w { "No health overlay records parsed" }
                            return@launch
                        }

                        logger.i { "Parsed ${records.size} health overlay records" }

                        // Filter out overlay data (sleep/activities) recorded before this watch was last connected/selected
                        val filteredRecords = filterStaleOverlayData(records, watchInfo)
                        if (filteredRecords.size < records.size) {
                            logger.i {
                                "HEALTH_FILTER: Filtered out ${records.size - filteredRecords.size} stale overlay records recorded before watch was last selected (kept ${filteredRecords.size})"
                            }
                        }

                        healthDao.insertOverlayDataWithDeduplication(filteredRecords)
                        logger.d { "Inserted ${filteredRecords.size} overlay records into database (with deduplication)" }
                    }
                }
            }
        }
    }

    /**
     * Filter out health data records that were recorded before this watch was last connected/selected.
     * This prevents duplicate calorie data when switching between watches.
     */
    private suspend fun filterStaleHealthData(
        records: List<HealthDataEntity>,
        watchInfo: WatchInfo
    ): List<HealthDataEntity> {
        val lastConnectedMillis = getLastConnectedTimestamp(watchInfo.serial) ?: return records
        val lastConnectedSeconds = lastConnectedMillis / 1000

        return records.filter { record ->
            record.timestamp >= lastConnectedSeconds
        }
    }

    /**
     * Filter out overlay data (sleep/activities) that were recorded before this watch was last connected/selected.
     * This prevents duplicate data when switching between watches.
     */
    private suspend fun filterStaleOverlayData(
        records: List<OverlayDataEntity>,
        watchInfo: WatchInfo
    ): List<OverlayDataEntity> {
        val lastConnectedMillis = getLastConnectedTimestamp(watchInfo.serial) ?: return records
        val lastConnectedSeconds = lastConnectedMillis / 1000

        return records.filter { record ->
            record.startTime >= lastConnectedSeconds
        }
    }

    /**
     * Get the timestamp when this watch was last connected/selected by the user.
     * Returns null if this is the first connection or if the watch is not found.
     */
    private suspend fun getLastConnectedTimestamp(serial: String): Long? {
        val knownWatches = knownWatchDao.knownWatches()
        val thisWatch = knownWatches.find { it.serial == serial }
        return thisWatch?.lastConnected?.instant?.toEpochMilliseconds()
    }

    companion object {
        private val MEMFAULT_CHUNKS_TAG: UInt = 86u
        private val HEALTH_STEPS_TAG: UInt = 81u
        private val HEALTH_SLEEP_TAG: UInt = 83u
        private val HEALTH_OVERLAY_TAG: UInt = 84u
        private val HEALTH_HR_TAG: UInt = 85u
    }
}

class MemfaultChunk : StructMappable() {
    val chunkSize: SUInt = SUInt(m, 0u, Endian.Little)
    val bytes: SBytes = SBytes(m).apply { linkWithSize(chunkSize) }
}
