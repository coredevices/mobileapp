package io.rebble.libpebblecommon.health

import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthParsingTest {
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
