package io.rebble.libpebblecommon.connection.endpointmanager

import io.rebble.libpebblecommon.util.Crc32Calculator
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class FirmwareUpdaterResumeTest {
    private val data = UByteArray(100) { it.toUByte() }

    private fun source(bytes: UByteArray = data) = Buffer().apply { write(bytes.asByteArray()) }

    private fun crcOfPrefix(length: Int): UInt =
        Crc32Calculator().apply { addBytes(data.copyOfRange(0, length)) }.finalize()

    @Test
    fun matchingCrcResumes() {
        assertEquals(60u, validatedResumeOffset(60u, crcOfPrefix(60), 100u) { source() })
    }

    @Test
    fun mismatchedCrcRestarts() {
        assertEquals(0u, validatedResumeOffset(60u, crcOfPrefix(60) + 1u, 100u) { source() })
    }

    @Test
    fun zeroBytesWrittenRestarts() {
        assertEquals(0u, validatedResumeOffset(0u, 0u, 100u) { source() })
    }

    @Test
    fun bytesWrittenBeyondObjectRestarts() {
        assertEquals(0u, validatedResumeOffset(100u, crcOfPrefix(100), 100u) { source() })
    }

    @Test
    fun fullyWrittenObjectRestarts() {
        assertEquals(0u, validatedResumeOffset(99u, crcOfPrefix(99), 100u) { source() })
    }

    @Test
    fun localObjectShorterThanOffsetRestarts() {
        assertEquals(
            0u,
            validatedResumeOffset(60u, crcOfPrefix(60), 100u) {
                source(data.copyOfRange(0, 50))
            },
        )
    }
}
