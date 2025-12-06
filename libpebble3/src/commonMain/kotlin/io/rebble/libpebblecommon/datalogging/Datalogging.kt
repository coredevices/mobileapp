package io.rebble.libpebblecommon.datalogging

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.dao.insertHealthDataWithPriority
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.health.OverlayType
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

class Datalogging(
        private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
        private val webServices: WebServices,
        private val healthDao: HealthDao,
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
                        if (itemSize.toInt() == 0) {
                            logger.w { "Item size is 0, cannot parse health steps" }
                            return@launch
                        }
                        val buffer = DataBuffer(data.toUByteArray())
                        val records = mutableListOf<HealthDataEntity>()

                        if (data.size % itemSize.toInt() != 0) {
                            logger.w {
                                "Steps payload size (${data.size}) is not a multiple of item size ($itemSize); parsing what we can"
                            }
                        }

                        val packetCount = data.size / itemSize.toInt()
                        logger.d { "Processing $packetCount health steps packets" }

                        for (i in 0 until packetCount) {
                            val itemStart = buffer.readPosition

                            buffer.setEndian(Endian.Little)

                            val version = buffer.getUShort()
                            val timestamp = buffer.getUInt()
                            buffer.getByte() // unused
                            val recordLength = buffer.getUByte() // not currently used but helps debugging
                            val recordNum = buffer.getUByte()

                            if (!SUPPORTED_STEP_VERSIONS.contains(version)) {
                                logger.w {
                                    "Unsupported health steps record version=$version, skipping remaining payload"
                                }
                                return@launch
                            }

                            logger.d {
                                "Steps packet $i: version=$version, timestamp=$timestamp, recordLength=$recordLength, recordNum=$recordNum"
                            }

                            var currentTimestamp = timestamp

                            repeat(recordNum.toInt()) {
                                val steps = buffer.getUByte().toInt()
                                val orientation = buffer.getUByte().toInt()
                                val intensity = buffer.getUShort().toInt()
                                val lightIntensity = buffer.getUByte().toInt()

                                val flags =
                                        if (version >= VERSION_FW_3_10_AND_BELOW) {
                                            buffer.getUByte().toInt()
                                        } else {
                                            0
                                        }

                                var restingGramCalories = 0
                                var activeGramCalories = 0
                                var distanceCm = 0
                                var heartRate = 0
                                var heartRateWeight = 0
                                var heartRateZone = 0

                                if (version >= VERSION_FW_3_11) {
                                    restingGramCalories = buffer.getUShort().toInt()
                                    activeGramCalories = buffer.getUShort().toInt()
                                    distanceCm = buffer.getUShort().toInt()
                                }

                                if (version >= VERSION_FW_4_0) {
                                    heartRate = buffer.getUByte().toInt()
                                }

                                if (version >= VERSION_FW_4_1) {
                                    heartRateWeight = buffer.getUShort().toInt()
                                }

                                if (version >= VERSION_FW_4_3) {
                                    heartRateZone = buffer.getUByte().toInt()
                                }

                                records.add(
                                        HealthDataEntity(
                                                timestamp = currentTimestamp.toLong(),
                                                steps = steps,
                                                orientation = orientation,
                                                intensity = intensity,
                                                lightIntensity = lightIntensity,
                                                activeMinutes =
                                                        if ((flags and 2) > 0) 1 else 0,
                                                restingGramCalories = restingGramCalories,
                                                activeGramCalories = activeGramCalories,
                                                distanceCm = distanceCm,
                                                heartRate = heartRate,
                                                heartRateZone = heartRateZone,
                                                heartRateWeight = heartRateWeight
                                        )
                                )

                                currentTimestamp += 60u
                            }

                            val consumed = buffer.readPosition - itemStart
                            val expected = itemSize.toInt()
                            if (consumed < expected) {
                                val skip = expected - consumed
                                buffer.getBytes(skip)
                                logger.d { "Skipped $skip padding bytes in steps packet $i" }
                            } else if (consumed > expected) {
                                logger.w {
                                    "Health steps item over-read: consumed=$consumed, expected=$expected"
                                }
                            }
                        }

                        logger.i {
                            "Parsed ${records.size} health step records (total steps: ${records.sumOf { it.steps }}, time range: ${records.firstOrNull()?.timestamp}-${records.lastOrNull()?.timestamp})"
                        }
                        healthDao.insertHealthDataWithPriority(records)
                        logger.d { "Inserted ${records.size} health records into database" }
                    }
                }
                HEALTH_OVERLAY_TAG -> {
                    libPebbleCoroutineScope.launch {
                        logger.i {
                            "Received HEALTH_OVERLAY data: ${data.size} bytes, itemSize=$itemSize"
                        }
                        if (itemSize.toInt() == 0) {
                            logger.w { "Item size is 0, cannot parse health overlay data" }
                            return@launch
                        }
                        val buffer = DataBuffer(data.toUByteArray())
                        buffer.setEndian(Endian.Little)
                        val records = mutableListOf<OverlayDataEntity>()

                        if (data.size % itemSize.toInt() != 0) {
                            logger.w {
                                "Overlay payload size (${data.size}) is not a multiple of item size ($itemSize); parsing what we can"
                            }
                        }

                        val packetCount = data.size / itemSize.toInt()
                        logger.d { "Processing $packetCount health overlay packets" }

                        for (i in 0 until packetCount) {
                            val itemStart = buffer.readPosition

                            val version = buffer.getUShort()
                            buffer.getUShort() // unused
                            val rawType = buffer.getUShort().toInt()
                            val type = OverlayType.fromValue(rawType)

                            if (type == null) {
                                val remaining = itemSize.toInt() - 6 // already consumed 6 bytes
                                if (remaining > 0) buffer.getBytes(remaining)
                                logger.w { "Unknown overlay type: $rawType, skipping packet $i" }
                                continue
                            }

                            val offsetUTC = buffer.getUInt()
                            val startTime = buffer.getUInt()
                            val duration = buffer.getUInt()

                            var steps = 0
                            var restingKiloCalories = 0
                            var activeKiloCalories = 0
                            var distanceCm = 0

                            if (version < 3.toUShort() ||
                                            (type != OverlayType.Walk && type != OverlayType.Run)
                            ) {
                                if (version == 3.toUShort()) {
                                    // Firmware 3.x includes calorie/distance data even for non-walk/run types
                                    buffer.getBytes(8)
                                }
                            } else {
                                steps = buffer.getUShort().toInt()
                                restingKiloCalories = buffer.getUShort().toInt()
                                activeKiloCalories = buffer.getUShort().toInt()
                                distanceCm = buffer.getUShort().toInt()
                            }

                            records.add(
                                    OverlayDataEntity(
                                            startTime = startTime.toLong(),
                                            duration = duration.toLong(),
                                            type = type.value,
                                            steps = steps,
                                            restingKiloCalories = restingKiloCalories,
                                            activeKiloCalories = activeKiloCalories,
                                            distanceCm = distanceCm,
                                            offsetUTC = offsetUTC.toInt()
                                    )
                            )

                            val consumed = buffer.readPosition - itemStart
                            val expected = itemSize.toInt()
                            if (consumed < expected) {
                                val skip = expected - consumed
                                buffer.getBytes(skip)
                                logger.d { "Skipped $skip padding bytes in overlay packet $i" }
                            } else if (consumed > expected) {
                                logger.w {
                                    "Health overlay item over-read: consumed=$consumed, expected=$expected"
                                }
                            }
                        }

                        logger.i { "Parsed ${records.size} health overlay records" }
                        healthDao.insertOverlayData(records)
                        logger.d { "Inserted ${records.size} overlay records into database" }
                    }
                }
            }
        }
    }

    companion object {
        private val MEMFAULT_CHUNKS_TAG: UInt = 86u
        private val HEALTH_STEPS_TAG: UInt = 81u
        private val HEALTH_SLEEP_TAG: UInt = 83u
        private val HEALTH_OVERLAY_TAG: UInt = 84u
        private val HEALTH_HR_TAG: UInt = 85u

        private val VERSION_FW_3_10_AND_BELOW: UShort = 5u
        private val VERSION_FW_3_11: UShort = 6u
        private val VERSION_FW_4_0: UShort = 7u
        private val VERSION_FW_4_1: UShort = 8u
        private val VERSION_FW_4_3: UShort = 13u
        private val SUPPORTED_STEP_VERSIONS =
                setOf(
                        VERSION_FW_3_10_AND_BELOW,
                        VERSION_FW_3_11,
                        VERSION_FW_4_0,
                        VERSION_FW_4_1,
                        VERSION_FW_4_3
                )
    }
}

class MemfaultChunk : StructMappable() {
    val chunkSize: SUInt = SUInt(m, 0u, Endian.Little)
    val bytes: SBytes = SBytes(m).apply { linkWithSize(chunkSize) }
}
