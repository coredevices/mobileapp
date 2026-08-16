package io.rebble.libpebblecommon.util

import kotlinx.io.Source

/**
 * CRC32 hash Calculator that is compatible with hardware CRC32 on the STM chips.
 */
class Crc32Calculator {
    private var finalized = false
    private var value: UInt = 0xFFFFFFFFu

    private var leftoverBytes = UByteArray(0)

    fun addBytes(bytes: UByteArray) {
        if (finalized) {
            throw IllegalStateException("Cannot add more bytes to finalized CRC calculation")
        }

        val mergedArray = leftoverBytes + bytes
        val buffer = DataBuffer(mergedArray)
        buffer.setEndian(Endian.Little)

        val finalPosition = mergedArray.size - mergedArray.size % 4
        while (buffer.readPosition < finalPosition) {
            addInt(buffer.getUInt())
        }

        leftoverBytes = mergedArray.copyOfRange(finalPosition, mergedArray.size)
    }

    /**
     * Finalizes the calculation and returns the CRC32 result
     */
    fun finalize(): UInt {
        if (finalized) {
            return value
        }

        if (leftoverBytes.isNotEmpty()) {
            leftoverBytes = leftoverBytes.padZerosLeft(4 - leftoverBytes.size).reversedArray()
            addInt(DataBuffer(leftoverBytes).apply { setEndian(Endian.Little) }.getUInt())
        }

        finalized = true
        return value
    }

    private fun addInt(valueToAdd: UInt) {
        this.value = this.value xor valueToAdd

        for (i in 0 until 32) {
            if ((this.value and 0x80000000u) != 0u) {
                this.value = (this.value shl 1) xor 0x04C11DB7u
            } else {
                this.value = this.value shl 1
            }
        }

        this.value = this.value and 0xFFFFFFFFu
    }
}

private fun UByteArray.padZerosLeft(amount: Int): UByteArray {
    return UByteArray(amount) { 0u } + this
}

/**
 * CRC of the first [length] bytes of this source, or null if it has fewer than [length] bytes.
 */
fun Source.crc32(length: Long): UInt? {
    val calculator = Crc32Calculator()
    val buffer = ByteArray(8192)
    var remaining = length
    while (remaining > 0) {
        val read = readAtMostTo(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
        if (read <= 0) return null
        calculator.addBytes(buffer.asUByteArray().copyOfRange(0, read))
        remaining -= read
    }
    return calculator.finalize()
}