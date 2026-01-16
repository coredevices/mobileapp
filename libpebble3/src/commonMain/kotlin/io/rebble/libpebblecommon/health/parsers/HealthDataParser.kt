package io.rebble.libpebblecommon.health.parsers

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.OverlayType
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian

private val logger = Logger.withTag("HealthDataParser")

/**
 * Parses step/movement data from the watch's health payload.
 *
 * Supports multiple firmware versions:
 * - Firmware 3.10 and below (version 5)
 * - Firmware 3.11 (version 6) - adds calorie and distance data
 * - Firmware 4.0 (version 7) - adds heart rate data
 * - Firmware 4.1 (version 8) - adds heart rate weight
 * - Firmware 4.3 (version 13) - adds heart rate zone
 *
 * @param payload Raw byte array from the watch
 * @param itemSize Size of each data item in bytes
 * @return List of parsed health data entities ready for database insertion
 */
fun parseStepsData(payload: ByteArray, itemSize: UShort): List<HealthDataEntity> {
    if (payload.isEmpty() || itemSize.toInt() == 0) {
        logger.w { "Cannot parse steps data: empty payload or zero item size" }
        return emptyList()
    }

    val buffer = DataBuffer(payload.toUByteArray())
    val records = mutableListOf<HealthDataEntity>()

    if (payload.size % itemSize.toInt() != 0) {
        logger.w {
            "Steps payload size (${payload.size}) is not a multiple of item size ($itemSize); parsing what we can"
        }
    }

    val packetCount = payload.size / itemSize.toInt()
    logger.d { "Parsing $packetCount steps packets" }

    for (i in 0 until packetCount) {
        val itemStart = buffer.readPosition
        buffer.setEndian(Endian.Little)

        val version = buffer.getUShort()
        val timestamp = buffer.getUInt()
        buffer.getByte() // unused
        buffer.getUByte() // recordLength
        val recordNum = buffer.getUByte()

        if (!SUPPORTED_STEP_VERSIONS.contains(version)) {
            logger.w {
                "Unsupported health steps record version=$version, skipping packet $i of $packetCount"
            }
            // Skip to next packet instead of aborting entire payload
            val consumed = buffer.readPosition - itemStart
            val remaining = itemSize.toInt() - consumed
            if (remaining > 0) buffer.getBytes(remaining)
            continue
        }

        var currentTimestamp = timestamp

        repeat(recordNum.toInt()) {
            val steps = buffer.getUByte().toInt()
            val orientation = buffer.getUByte().toInt()
            val intensity = buffer.getUShort().toInt()
            val lightIntensity = buffer.getUByte().toInt()

            val flags = if (version >= VERSION_FW_3_10_AND_BELOW) {
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
                    activeMinutes = if ((flags and 2) > 0) 1 else 0,
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
            buffer.getBytes(expected - consumed)
        } else if (consumed > expected) {
            logger.w { "Health steps item over-read: consumed=$consumed, expected=$expected" }
        }
    }

    return records
}

/**
 * Parses overlay data (sleep, activities) from the watch's health payload.
 *
 * Overlay types include:
 * - Sleep and nap sessions (deep sleep, light sleep, naps)
 * - Walk and run activities with step counts and distance
 *
 * @param payload Raw byte array from the watch
 * @param itemSize Size of each data item in bytes
 * @return List of parsed overlay data entities ready for database insertion
 */
fun parseOverlayData(payload: ByteArray, itemSize: UShort): List<OverlayDataEntity> {
    if (payload.isEmpty() || itemSize.toInt() == 0) {
        logger.w { "Cannot parse overlay data: empty payload or zero item size" }
        return emptyList()
    }

    val buffer = DataBuffer(payload.toUByteArray())
    buffer.setEndian(Endian.Little)
    val records = mutableListOf<OverlayDataEntity>()

    if (payload.size % itemSize.toInt() != 0) {
        logger.w {
            "Overlay payload size (${payload.size}) is not a multiple of item size ($itemSize); parsing what we can"
        }
    }

    val packetCount = payload.size / itemSize.toInt()
    logger.d { "Parsing $packetCount overlay packets" }

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

        if (version < 3.toUShort() || (type != OverlayType.Walk && type != OverlayType.Run)) {
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
            buffer.getBytes(expected - consumed)
        } else if (consumed > expected) {
            logger.w { "Health overlay item over-read: consumed=$consumed, expected=$expected" }
        }
    }

    return records
}

// Firmware version constants for health data parsing
private val VERSION_FW_3_10_AND_BELOW: UShort = 5u
private val VERSION_FW_3_11: UShort = 6u
private val VERSION_FW_4_0: UShort = 7u
private val VERSION_FW_4_1: UShort = 8u
private val VERSION_FW_4_3: UShort = 13u
private val SUPPORTED_STEP_VERSIONS = setOf(
    VERSION_FW_3_10_AND_BELOW,
    VERSION_FW_3_11,
    VERSION_FW_4_0,
    VERSION_FW_4_1,
    VERSION_FW_4_3
)
