package io.rebble.libpebblecommon.health.parsers

import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthDataParserTest {
    private fun stepsItem(version: Int, sampleSize: Int, extraSampleBytes: Int, itemSize: Int): ByteArray {
        val buffer = DataBuffer(itemSize)
        buffer.setEndian(Endian.Little)
        buffer.putUShort(version.toUShort())
        buffer.putUInt(1600000000u)
        buffer.putUByte(0u)
        buffer.putUByte(sampleSize.toUByte())
        buffer.putUByte(2u)
        for (s in 0 until 2) {
            buffer.putUByte((100 + s).toUByte()) // steps
            buffer.putUByte(1u) // orientation
            buffer.putUShort(500u) // vmc
            buffer.putUByte(10u) // light
            buffer.putUByte(2u) // flags
            buffer.putUShort(10u) // resting cal
            buffer.putUShort(50u) // active cal
            buffer.putUShort(7000u) // distance
            buffer.putUByte((60 + s).toUByte()) // hr
            buffer.putUShort(1u) // hr weight
            buffer.putUByte(3u) // hr zone
            repeat(extraSampleBytes) { buffer.putUByte(0xAAu) }
        }
        return buffer.array().toByteArray()
    }

    @Test
    fun parsesNewerVersionWithLargerSamples() {
        val itemSize = 9 + 15 * 18
        val payload = stepsItem(version = 14, sampleSize = 18, extraSampleBytes = 2, itemSize = itemSize) +
            stepsItem(version = 14, sampleSize = 18, extraSampleBytes = 2, itemSize = itemSize)

        val records = parseStepsData(payload, itemSize.toUShort())

        assertEquals(4, records.size)
        assertEquals(listOf(100, 101, 100, 101), records.map { it.steps })
        assertEquals(listOf(60, 61, 60, 61), records.map { it.heartRate })
        assertTrue(records.all { it.heartRateZone == 3 && it.distanceCm == 7000 })
    }

    @Test
    fun skipsNewerVersionWithTooSmallSamples() {
        val itemSize = 9 + 15 * 16
        val payload = stepsItem(version = 14, sampleSize = 15, extraSampleBytes = 0, itemSize = itemSize)

        assertEquals(0, parseStepsData(payload, itemSize.toUShort()).size)
    }

    private fun overlayItem(version: Int, itemSize: Int): ByteArray {
        val buffer = DataBuffer(itemSize)
        buffer.setEndian(Endian.Little)
        buffer.putUShort(version.toUShort())
        buffer.putUShort(itemSize.toUShort())
        buffer.putUShort(5u) // walk
        buffer.putUInt(3600u) // utc offset
        buffer.putUInt(1600000000u) // start
        buffer.putUInt(900u) // duration
        buffer.putUShort(1200u) // steps
        buffer.putUShort(40u) // active kcal
        buffer.putUShort(15u) // resting kcal
        buffer.putUShort(800u) // distance
        return buffer.array().toByteArray()
    }

    @Test
    fun parsesOverlayWalkSession() {
        val itemSize = 28
        val records = parseOverlayData(overlayItem(version = 3, itemSize = itemSize), itemSize.toUShort())

        assertEquals(1, records.size)
        with(records.single()) {
            assertEquals(1200, steps)
            assertEquals(40, activeKiloCalories)
            assertEquals(15, restingKiloCalories)
            assertEquals(800, distanceCm)
        }
    }

    @Test
    fun parsesNewerCompatibleOverlayVersionWithExtraBytes() {
        val itemSize = 32
        val payload = overlayItem(version = 5, itemSize = itemSize) + overlayItem(version = 5, itemSize = itemSize)

        val records = parseOverlayData(payload, itemSize.toUShort())

        assertEquals(2, records.size)
        assertTrue(records.all { it.steps == 1200 && it.activeKiloCalories == 40 })
    }

    @Test
    fun skipsOverlayVersionWithBit0Cleared() {
        val itemSize = 28
        val payload = overlayItem(version = 4, itemSize = itemSize) + overlayItem(version = 3, itemSize = itemSize)

        val records = parseOverlayData(payload, itemSize.toUShort())

        assertEquals(1, records.size)
        assertEquals(1200, records.single().steps)
    }
}
