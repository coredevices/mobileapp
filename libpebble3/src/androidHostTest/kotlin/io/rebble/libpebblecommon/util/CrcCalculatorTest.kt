package io.rebble.libpebblecommon.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CrcCalculatorTest {
    @Test
    fun assertEmpty() {
        assertEquals(
            0xffffffffu,
            calculateCrcOfBuffer(*ubyteArrayOf())
        )
    }

    @Test
    fun assertOneByte() {
        assertEquals(
            0x1d604014u,
            calculateCrcOfBuffer(0xABu)
        )
    }

    @Test
    fun assertFourBytes() {
        assertEquals(
            0x1dabe74fu,
            calculateCrcOfBuffer(0x01u, 0x02u, 0x03u, 0x04u)
        )
    }

    @Test
    fun assertSixBytes() {
        assertEquals(
            0x205dbd4fu,
            calculateCrcOfBuffer(0x01u, 0x02u, 0x03u, 0x04u, 0x05u, 0x06u)
        )
    }

    @Test
    fun assertEightBytesAtOnce() {
        assertEquals(
            0x99f9e573u,
            calculateCrcOfBuffer(0x01u, 0x02u, 0x03u, 0x04u, 0x50u, 0x06u, 0x70u, 0x08u)
        )
    }

    @Test
    fun assertEightBytesInChunks() {
        val calculator = Crc32Calculator()
        calculator.addBytes(ubyteArrayOf(0x01u))
        calculator.addBytes(ubyteArrayOf(0x02u))
        calculator.addBytes(ubyteArrayOf(0x03u, 0x04u, 0x50u, 0x06u))
        calculator.addBytes(ubyteArrayOf(0x70u, 0x08u))

        assertEquals(
            0x99f9e573u,
            calculator.finalize()
        )

    }

    private fun calculateCrcOfBuffer(vararg buffer: UByte): UInt {
        val calculator = Crc32Calculator()

        calculator.addBytes(buffer)

        return calculator.finalize()
    }
}