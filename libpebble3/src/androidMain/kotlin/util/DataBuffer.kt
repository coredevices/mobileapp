package io.rebble.libpebblecommon.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

public actual class DataBuffer {
    private val actualBuf: ByteBuffer

    public actual constructor(size: Int) {
        actualBuf = ByteBuffer.allocate(size)
    }

    public actual constructor(bytes: UByteArray) {
        actualBuf = ByteBuffer.wrap(bytes.toByteArray())
    }

    /**
     * Total length of the buffer
     */
    public actual val length: Int
        get() = actualBuf.capacity()

    /**
     * Current position in the buffer
     */
    public actual val readPosition: Int
        get() = actualBuf.position()

    public actual val remaining: Int
        get() = actualBuf.remaining()

    public actual fun putUShort(short: UShort) {
        actualBuf.putShort(short.toShort())
    }

    public actual fun getUShort(): UShort = actualBuf.short.toUShort()

    public actual fun putShort(short: Short) {
        actualBuf.putShort(short)
    }

    public actual fun getShort(): Short = actualBuf.short

    public actual fun putUByte(byte: UByte) {
        actualBuf.put(byte.toByte())
    }
    public actual fun getUByte(): UByte = actualBuf.get().toUByte()

    public actual fun putByte(byte: Byte) {
        actualBuf.put(byte)
    }
    public actual fun getByte(): Byte = actualBuf.get()

    public actual fun putBytes(bytes: UByteArray) {
        actualBuf.put(bytes.toByteArray())
    }
    public actual fun getBytes(count: Int): UByteArray {
        val tBuf = ByteArray(count)
        actualBuf.get(tBuf)
        return tBuf.toUByteArray()
    }

    public actual fun array(): UByteArray = actualBuf.array().toUByteArray()

    public actual fun setEndian(endian: Endian) {
        when (endian) {
            Endian.Big -> actualBuf.order(ByteOrder.BIG_ENDIAN)
            Endian.Little -> actualBuf.order(ByteOrder.LITTLE_ENDIAN)
            else -> {}
        }
    }

    public actual fun putInt(int: Int) {
        actualBuf.putInt(int)
    }
    public actual fun getInt(): Int = actualBuf.int

    public actual fun putUInt(uint: UInt) {
        actualBuf.putInt(uint.toInt())
    }
    public actual fun getUInt(): UInt = actualBuf.int.toUInt()

    public actual fun putULong(ulong: ULong) {
        actualBuf.putLong(ulong.toLong())
    }
    public actual fun getULong(): ULong = actualBuf.long.toULong()

    public actual fun rewind() {
        actualBuf.rewind()
    }
}