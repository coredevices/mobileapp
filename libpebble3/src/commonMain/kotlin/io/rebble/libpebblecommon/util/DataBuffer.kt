package io.rebble.libpebblecommon.util

/**
 * Common DataBuffer with bindings for each platform
 */
public expect class DataBuffer {
    internal constructor(size: Int)
    internal constructor(bytes: UByteArray)

    public fun putUShort(short: UShort)
    public fun getUShort(): UShort

    public fun putShort(short: Short)
    public fun getShort(): Short

    public fun putUByte(byte: UByte)
    public fun getUByte(): UByte

    public fun putByte(byte: Byte)
    public fun getByte(): Byte

    public fun putBytes(bytes: UByteArray)
    public fun getBytes(count: Int): UByteArray

    public fun putUInt(uint: UInt)
    public fun getUInt(): UInt

    public fun putInt(int: Int)
    public fun getInt(): Int

    public fun putULong(ulong: ULong)
    public fun getULong(): ULong

    public fun array(): UByteArray

    public fun setEndian(endian: Endian)

    public fun rewind()

    /**
     * Total length of the buffer
     */
    public val length: Int

    /**
     * Current position in the buffer
     */
    public val readPosition: Int

    /**
     * Remaining bytes in the buffer
     */
    public val remaining: Int
}

internal fun DataBuffer.putBytes(bytes: ByteArray): Unit = putBytes(bytes.asUByteArray())
