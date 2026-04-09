package io.rebble.libpebblecommon.connection.bt.ble.pebble

import com.oldguy.common.io.BitSet
import com.oldguy.common.io.Buffer
import com.oldguy.common.io.ByteBuffer
import io.rebble.libpebblecommon.connection.bt.ble.pebble.ExtendedPebbleScanRecord.Companion.decodeExtendedPebbleScanRecord

data class PebbleLeScanRecord(
    val payloadType: Byte,
    val serialNumber: String,
    val extendedInfo: ExtendedPebbleScanRecord?,
) {
    companion object {
        fun ByteArray.decodePebbleScanRecord(): PebbleLeScanRecord {
            val buffer = ByteBuffer(this, Buffer.ByteOrder.LittleEndian)
            return PebbleLeScanRecord(
                payloadType = buffer.byte,
                serialNumber = buffer.getBytes(12).decodeToString(),
                extendedInfo = buffer.decodeExtendedPebbleScanRecord(),
            )
        }
    }
}

data class ExtendedPebbleScanRecord(
    val hardwarePlatform: Int,
    val color: Byte,
    val major: Int,
    val minor: Int,
    val patch: Int,
    val runningPrf: Boolean,
    val firstUse: Boolean,
) {
    companion object {
        fun ByteBuffer.decodeExtendedPebbleScanRecord(): ExtendedPebbleScanRecord? {
            val buffer = this
            // Only added in v3.0
            if (!buffer.hasRemaining) {
                return null
            }
            val hardwarePlatform = buffer.byte.toInt()
            val color = buffer.byte
            val major = buffer.byte.toUByte().toInt()
            val minor = buffer.byte.toUByte().toInt()
            val patch = buffer.byte.toUByte().toInt()
            val flags = BitSet(buffer.getBytes(1))
            val runningPrf = flags[0]
            val firstUse = flags[1]
            return ExtendedPebbleScanRecord(
                hardwarePlatform = hardwarePlatform,
                color = color,
                major = major,
                minor = minor,
                patch = patch,
                runningPrf = runningPrf,
                firstUse = firstUse,
            )
        }
    }
}