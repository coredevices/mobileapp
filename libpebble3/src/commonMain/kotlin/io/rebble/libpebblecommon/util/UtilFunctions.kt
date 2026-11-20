package io.rebble.libpebblecommon.util

internal object KUtil {
    fun byteArrayAsUByteArray(arr: ByteArray): UByteArray = arr.asUByteArray()
    fun uByteArrayAsByteArray(arr: UByteArray): ByteArray = arr.asByteArray()
}

internal infix fun Byte.ushr(bitCount: Int): Byte = ((this.toInt()) ushr bitCount).toByte()
internal infix fun Byte.shl(bitCount: Int): Byte = ((this.toInt()) shl bitCount).toByte()
internal infix fun UByte.shr(bitCount: Int): UByte = ((this.toUInt()) shr bitCount).toUByte()
internal infix fun UByte.shl(bitCount: Int): UByte = ((this.toUInt()) shl bitCount).toUByte()

internal infix fun Short.shl(bitCount: Int): Short = ((this.toInt()) shl bitCount).toShort()
internal infix fun UShort.shl(bitCount: Int): UShort = ((this.toUInt()) shl bitCount).toUShort()

internal fun String.encodeToByteArrayTrimmed(maxBytes: Int): ByteArray {
    check(maxBytes >= 2) {
        "maxBytes must be at least 2 to fit ellipsis character. Got $maxBytes instead"
    }

    val encodedOriginal = encodeToByteArray()
    if (encodedOriginal.size <= maxBytes) {
        return encodedOriginal
    }

    var trimmedString = take(maxBytes - 1)
    var encoded = "$trimmedString…".encodeToByteArray()

    while (encoded.size > maxBytes) {
        trimmedString = trimmedString.take(trimmedString.length - 1)
        encoded = "$trimmedString…".encodeToByteArray()
    }

    return encoded
}

internal fun String.trimWithEllipsis(maxLength: Int): String {
    if (length <= maxLength) {
        return this
    }
    return take(maxLength - 1) + "…"
}
