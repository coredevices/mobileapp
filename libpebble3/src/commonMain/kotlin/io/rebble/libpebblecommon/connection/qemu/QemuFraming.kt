package io.rebble.libpebblecommon.connection.qemu

/**
 * Framing for the QEMU serial channel: FE ED | protocol | length | payload | BE EF, big-endian.
 * See PebbleOS src/fw/drivers/qemu/qemu_serial_private.h.
 */
internal object QemuFraming {
    const val HEADER_SIGNATURE = 0xFEED
    const val FOOTER_SIGNATURE = 0xBEEF
    const val MAX_DATA_LEN = 2048

    /** Carries raw Pebble Protocol. Other ids carry taps, battery, button presses etc. */
    const val PROTOCOL_SPP = 1
    const val PROTOCOL_BLUETOOTH_CONNECTION = 3

    fun frame(protocol: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(payload.size + 8)
        out.putUShort(0, HEADER_SIGNATURE)
        out.putUShort(2, protocol)
        out.putUShort(4, payload.size)
        payload.copyInto(out, 6)
        out.putUShort(6 + payload.size, FOOTER_SIGNATURE)
        return out
    }

    private fun ByteArray.putUShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }
}

internal data class QemuFrame(val protocol: Int, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is QemuFrame && protocol == other.protocol && payload.contentEquals(other.payload)

    override fun hashCode(): Int = 31 * protocol + payload.contentHashCode()
}

/**
 * Reassembles frames from a byte stream. The emulator splits the Pebble Protocol stream across
 * frames arbitrarily, so a frame is not a message and reads do not align to frame boundaries.
 */
internal class QemuFrameParser {
    private val buffer = mutableListOf<Byte>()

    fun feed(bytes: ByteArray): List<QemuFrame> {
        buffer.addAll(bytes.asList())
        val frames = mutableListOf<QemuFrame>()
        while (true) {
            val start = indexOfHeader() ?: run {
                // A header can straddle two reads, so keep the last byte.
                if (buffer.size > 1) buffer.subList(0, buffer.size - 1).clear()
                return frames
            }
            if (start > 0) buffer.subList(0, start).clear()
            if (buffer.size < 6) return frames
            val protocol = readUShort(2)
            val length = readUShort(4)
            if (length > QemuFraming.MAX_DATA_LEN) {
                buffer.subList(0, 2).clear()
                continue
            }
            val end = 6 + length + 2
            if (buffer.size < end) return frames
            val payload = ByteArray(length) { buffer[6 + it] }
            buffer.subList(0, end).clear()
            frames.add(QemuFrame(protocol, payload))
        }
    }

    private fun indexOfHeader(): Int? {
        for (i in 0 until buffer.size - 1) {
            if (buffer[i] == 0xFE.toByte() && buffer[i + 1] == 0xED.toByte()) return i
        }
        return null
    }

    private fun readUShort(offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
}
