package io.rebble.libpebblecommon.util

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableData
import platform.Foundation.appendBytes
import platform.Foundation.create
import platform.Foundation.dataWithCapacity
import platform.Foundation.getBytes
import platform.Foundation.setData

actual class DataBuffer {
    private val actualBuf: NSMutableData
    private var littleEndian = false

    /**
     * Total length of the buffer
     */
    actual val length: Int
        get() = actualBuf.length.toInt()

    actual val remaining: Int
        get() = actualBuf.length().toInt()-_readPosition

    private var _readPosition: Int = 0

    /**
     * Current position in the buffer
     */
    actual val readPosition: Int
        get() = _readPosition

    actual constructor(size: Int) {
        actualBuf = NSMutableData.dataWithCapacity(castToNativeSize(size))!!
    }

    actual constructor(bytes: UByteArray) {
        actualBuf = NSMutableData()
        memScoped {
            actualBuf.setData(
                NSData.create(bytes = allocArrayOf(bytes.asByteArray()), length = castToNativeSize(bytes.size))
            )
        }
    }

    private fun checkRemaining(size: Int) {
        if (_readPosition + size > actualBuf.length.toInt()) {
            throw IndexOutOfBoundsException("DataBuffer underflow: need $size bytes at position $_readPosition but only ${actualBuf.length.toInt() - _readPosition} remaining")
        }
    }

    private fun shouldReverse(): Boolean {
        return if (isPlatformBigEndian() && !littleEndian) {
            false
        }else if (isPlatformBigEndian() && littleEndian) {
            true
        }else !isPlatformBigEndian() && !littleEndian
    }

    actual fun putUShort(short: UShort) {
        memScoped {
            val pShort = alloc<UShortVar>()
            pShort.value = if (shouldReverse())  reverseOrd(short) else short
            actualBuf.appendBytes(pShort.ptr, castToNativeSize(UShort.SIZE_BYTES))
        }
    }
    actual fun getUShort(): UShort {
        checkRemaining(UShort.SIZE_BYTES)
        memScoped {
            val pShort = alloc<UShortVar>()
            actualBuf.getBytes(pShort.ptr, NSMakeRange(castToNativeSize(_readPosition), castToNativeSize(UShort.SIZE_BYTES)))
            _readPosition += UShort.SIZE_BYTES
            return if (shouldReverse()) reverseOrd(pShort.value) else pShort.value
        }
    }

    actual fun putShort(short: Short) {
        memScoped {
            val pShort = alloc<ShortVar>()
            pShort.value = if (shouldReverse()) reverseOrd(short.toUShort()).toShort() else short
            actualBuf.appendBytes(pShort.ptr, castToNativeSize(Short.SIZE_BYTES))
        }
    }
    actual fun getShort(): Short {
        checkRemaining(Short.SIZE_BYTES)
        memScoped {
            val pShort = alloc<ShortVar>()
            actualBuf.getBytes(pShort.ptr, NSMakeRange(castToNativeSize(_readPosition), castToNativeSize(Short.SIZE_BYTES)))
            _readPosition += Short.SIZE_BYTES
            return if (shouldReverse()) reverseOrd(pShort.value.toUShort()).toShort() else pShort.value
        }
    }

    actual fun putUByte(byte: UByte) {
        memScoped {
            val pByte = alloc<UByteVar>()
            pByte.value = byte
            actualBuf.appendBytes(pByte.ptr, castToNativeSize(UByte.SIZE_BYTES))
        }
    }
    actual fun getUByte(): UByte {
        checkRemaining(UByte.SIZE_BYTES)
        memScoped {
            val pByte = alloc<UByteVar>()
            actualBuf.getBytes(pByte.ptr, NSMakeRange(castToNativeSize(_readPosition), castToNativeSize(UByte.SIZE_BYTES)))
            _readPosition += UByte.SIZE_BYTES
            return pByte.value
        }
    }

    actual fun putByte(byte: Byte) {
        memScoped {
            val pByte = alloc<ByteVar>()
            pByte.value = byte
            actualBuf.appendBytes(pByte.ptr, castToNativeSize(Byte.SIZE_BYTES))
        }
    }
    actual fun getByte(): Byte {
        checkRemaining(Byte.SIZE_BYTES)
        memScoped {
            val pByte = alloc<ByteVar>()
            actualBuf.getBytes(pByte.ptr, NSMakeRange(castToNativeSize(_readPosition), castToNativeSize(Byte.SIZE_BYTES)))
            _readPosition += Byte.SIZE_BYTES
            return pByte.value
        }
    }

    actual fun putBytes(bytes: UByteArray) {
        memScoped {
            val pBytes = allocArrayOf(bytes.toByteArray())
            actualBuf.appendBytes(pBytes, castToNativeSize(bytes.size))
        }
    }
    actual fun getBytes(count: Int): UByteArray {
        checkRemaining(count)
        memScoped {
            val pBytes = allocArray<UByteVar>(count)
            actualBuf.getBytes(pBytes.getPointer(this), NSMakeRange(castToNativeSize(_readPosition), castToNativeSize(count)))
            _readPosition += count
            return pBytes.readBytes(count).toUByteArray()
        }
    }

    actual fun array(): UByteArray = getBytes(actualBuf.length.toInt())

    actual fun setEndian(endian: Endian) {
        littleEndian = endian == Endian.Little
    }

    actual fun putUInt(uint: UInt) {
        memScoped {
            val pUInt = alloc<UIntVar>()
            pUInt.value = if (shouldReverse()) reverseOrd(uint) else uint
            actualBuf.appendBytes(pUInt.ptr, castToNativeSize(UInt.SIZE_BYTES))
        }
    }
    actual fun getUInt(): UInt {
        checkRemaining(UInt.SIZE_BYTES)
        memScoped {
            val pUInt = alloc<UIntVar>()
            actualBuf.getBytes(pUInt.ptr, NSMakeRange(castToNativeSize(_readPosition), castToNativeSize(UInt.SIZE_BYTES)))
            _readPosition += UInt.SIZE_BYTES
            return if (shouldReverse()) reverseOrd(pUInt.value) else pUInt.value
        }
    }

    actual fun putInt(int: Int) {
        memScoped {
            val pInt = alloc<IntVar>()
            pInt.value = if (shouldReverse()) reverseOrd(int.toUInt()).toInt() else int
            actualBuf.appendBytes(pInt.ptr, castToNativeSize(Int.SIZE_BYTES))
        }
    }
    actual fun getInt(): Int {
        checkRemaining(Int.SIZE_BYTES)
        memScoped {
            val pInt = alloc<IntVar>()
            actualBuf.getBytes(pInt.ptr, NSMakeRange(castToNativeSize(_readPosition), castToNativeSize(Int.SIZE_BYTES)))
            _readPosition += Int.SIZE_BYTES
            return if (shouldReverse()) reverseOrd(pInt.value.toUInt()).toInt() else pInt.value
        }
    }

    actual fun putULong(ulong: ULong) {
        memScoped {
            val pULong = alloc<ULongVar>()
            pULong.value = if (shouldReverse()) reverseOrd(ulong) else ulong
            actualBuf.appendBytes(pULong.ptr, castToNativeSize(ULong.SIZE_BYTES))
        }
    }
    actual fun getULong(): ULong {
        checkRemaining(ULong.SIZE_BYTES)
        memScoped {
            val pULong = alloc<ULongVar>()
            actualBuf.getBytes(pULong.ptr, NSMakeRange(castToNativeSize(_readPosition), castToNativeSize(ULong.SIZE_BYTES)))
            _readPosition += ULong.SIZE_BYTES
            return if (shouldReverse()) reverseOrd(pULong.value) else pULong.value
        }
    }

    actual fun rewind() {
        _readPosition = 0
    }
}
