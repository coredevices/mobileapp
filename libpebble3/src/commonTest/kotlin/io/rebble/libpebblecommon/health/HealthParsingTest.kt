package io.rebble.libpebblecommon.health

import io.rebble.libpebblecommon.health.parsers.parseStepsData
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthParsingTest {
    @Test
    fun testSpo2V14Parsing() {
        // Header (9 bytes): Version(2), Timestamp(4), Unused(1), RecordLength(1), RecordNum(1)
        // Sample (18 bytes v14): Steps(1), Orientation(1), VMC(2), Light(1), Flags(1),
        // RestingCal(2), ActiveCal(2), Distance(2), HR(1), HRWeight(2), HRZone(1),
        // SpO2%(1), SpO2Quality(1)
        val baseTime = 1600000000u
        val buffer = DataBuffer(UByteArray(45))
        buffer.setEndian(Endian.Little)

        // Header
        buffer.putUShort(14u)          // Version 14
        buffer.putUInt(baseTime)       // Timestamp
        buffer.putUByte(0u)            // Unused (time_local_offset placeholder)
        buffer.putUByte(18u)           // RecordLength / sample_size
        buffer.putUByte(2u)            // 2 samples

        // Sample 1: real SpO2 reading
        buffer.putUByte(100u)          // Steps
        buffer.putUByte(1u)            // Orientation
        buffer.putUShort(500u)         // VMC
        buffer.putUByte(10u)           // Light
        buffer.putUByte(2u)            // Flags
        buffer.putUShort(10u)          // RestingCal
        buffer.putUShort(50u)          // ActiveCal
        buffer.putUShort(7000u)        // Distance
        buffer.putUByte(60u)           // HR
        buffer.putUShort(1u)           // HRWeight
        buffer.putUByte(1u)            // HRZone
        buffer.putUByte(97u)           // SpO2%
        buffer.putUByte(6u)            // SpO2 quality

        // Sample 2: no SpO2 reading (0 = none)
        buffer.putUByte(150u)          // Steps
        buffer.putUByte(2u)            // Orientation
        buffer.putUShort(600u)         // VMC
        buffer.putUByte(20u)           // Light
        buffer.putUByte(0u)            // Flags
        buffer.putUShort(12u)          // RestingCal
        buffer.putUShort(60u)          // ActiveCal
        buffer.putUShort(8000u)        // Distance
        buffer.putUByte(65u)           // HR
        buffer.putUShort(1u)           // HRWeight
        buffer.putUByte(1u)            // HRZone
        buffer.putUByte(0u)            // SpO2% (no reading)
        buffer.putUByte(0u)            // SpO2 quality

        val parsed = parseStepsData(buffer.array().toByteArray(), itemSize = 45u)

        assertEquals(2, parsed.healthData.size)
        assertEquals(1, parsed.spo2Readings.size)

        val healthData = parsed.healthData
        assertEquals(100, healthData[0].steps)
        assertEquals(60, healthData[0].heartRate)
        assertEquals(150, healthData[1].steps)
        assertEquals(65, healthData[1].heartRate)

        val spo2 = parsed.spo2Readings.first()
        assertEquals(baseTime.toLong(), spo2.timestamp)
        assertEquals(97, spo2.spo2Percent)
        assertEquals(6, spo2.quality)
    }

    @Test
    fun testV13BackwardCompatibility() {
        // v13 sample size is 16 bytes (no SpO2 fields). Ensure HR/steps still parse.
        val baseTime = 1600001000u
        val buffer = DataBuffer(UByteArray(25))
        buffer.setEndian(Endian.Little)

        buffer.putUShort(13u)          // Version 13
        buffer.putUInt(baseTime)
        buffer.putUByte(0u)
        buffer.putUByte(16u)           // sample_size / recordLength
        buffer.putUByte(1u)            // 1 sample

        buffer.putUByte(42u)           // Steps
        buffer.putUByte(3u)            // Orientation
        buffer.putUShort(250u)         // VMC
        buffer.putUByte(5u)            // Light
        buffer.putUByte(0u)            // Flags
        buffer.putUShort(8u)           // RestingCal
        buffer.putUShort(30u)          // ActiveCal
        buffer.putUShort(4000u)        // Distance
        buffer.putUByte(72u)           // HR
        buffer.putUShort(2u)           // HRWeight
        buffer.putUByte(2u)            // HRZone

        val parsed = parseStepsData(buffer.array().toByteArray(), itemSize = 25u)

        assertEquals(1, parsed.healthData.size)
        assertTrue(parsed.spo2Readings.isEmpty())

        val sample = parsed.healthData.first()
        assertEquals(42, sample.steps)
        assertEquals(72, sample.heartRate)
        assertEquals(baseTime.toLong(), sample.timestamp)
    }

    @Test
    fun testStepsParsing() {
        // Simulate a raw steps record buffer
        // Structure:
        // Header: Version(2), Timestamp(4), Unused(1), RecordLength(1), RecordNum(1)
        // Record: Steps(1), Orientation(1), Intensity(2), Light(1), Flags(1), RestingCal(2),
        // ActiveCal(2), Distance(2), HR(1), HRWeight(2), HRZone(1)

        val buffer = DataBuffer(UByteArray(100))
        buffer.setEndian(Endian.Little)

        // Header
        buffer.putUShort(1u) // Version
        buffer.putUInt(1600000000u) // Timestamp
        buffer.putUByte(0u) // Unused
        buffer.putUByte(16u) // RecordLength (approx)
        buffer.putUByte(2u) // RecordNum (2 records)

        // Record 1
        buffer.putUByte(100u) // Steps
        buffer.putUByte(1u) // Orientation
        buffer.putUShort(500u) // Intensity
        buffer.putUByte(10u) // Light
        buffer.putUByte(2u) // Flags (Active)
        buffer.putUShort(10u) // RestingCal
        buffer.putUShort(50u) // ActiveCal
        buffer.putUShort(7000u) // Distance
        buffer.putUByte(60u) // HR
        buffer.putUShort(1u) // HRWeight
        buffer.putUByte(1u) // HRZone

        // Record 2
        buffer.putUByte(150u) // Steps
        buffer.putUByte(2u) // Orientation
        buffer.putUShort(600u) // Intensity
        buffer.putUByte(20u) // Light
        buffer.putUByte(0u) // Flags
        buffer.putUShort(12u) // RestingCal
        buffer.putUShort(60u) // ActiveCal
        buffer.putUShort(8000u) // Distance
        buffer.putUByte(65u) // HR
        buffer.putUShort(1u) // HRWeight
        buffer.putUByte(1u) // HRZone

        val data = buffer.array()

        // Now verify parsing logic (mimicking Datalogging.kt)
        val readBuffer = DataBuffer(data.toUByteArray())
        readBuffer.setEndian(Endian.Little)

        val version = readBuffer.getUShort()
        val timestamp = readBuffer.getUInt()
        readBuffer.getByte()
        val recordLength = readBuffer.getByte()
        val recordNum = readBuffer.getByte()

        assertEquals(1u, version)
        assertEquals(1600000000u, timestamp)
        assertEquals(2, recordNum)

        var currentTimestamp = timestamp

        for (i in 0 until recordNum.toInt()) {
            val rawRecord = RawStepsRecord()
            rawRecord.fromBytes(readBuffer)

            if (i == 0) {
                assertEquals(100u, rawRecord.steps.get())
                assertEquals(500u, rawRecord.intensity.get())
                assertEquals(1600000000u, currentTimestamp)
            } else {
                assertEquals(150u, rawRecord.steps.get())
                assertEquals(1600000060u, currentTimestamp)
            }
            currentTimestamp += 60u
        }
    }
}
