package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.util.Bitmap
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian

internal class AppCustomizationSetStockAppTitleMessage(
    appType: AppType,
    newName: String
) : PebblePacket(ProtocolEndpoint.APP_CUSTOMIZE) {
    val appType: SUByte = SUByte(m, appType.value)
    val name: SFixedString = SFixedString(m, 30, newName)
}

internal class AppCustomizationSetStockAppIconMessage(
    appType: AppType,
    icon: Bitmap
) : PebblePacket(ProtocolEndpoint.APP_CUSTOMIZE) {
    // First bit being set signifies that this is icon packet instead of name packet
    val appType: SUByte = SUByte(m, appType.value or 0b10000000u)
    val bytesPerLine: SUShort = SUShort(m, endianness = Endian.Little)

    /**
     * No idea what flags are possible. Stock app always sends 4096 here.
     */
    val flags: SUShort = SUShort(m, 4096u, endianness = Endian.Little)

    /**
     * Offset is not supported by app. Always 0.
     */
    val originY: SUShort = SUShort(m, 0u, endianness = Endian.Little)
    val originX: SUShort = SUShort(m, 0u, endianness = Endian.Little)

    val width: SUShort = SUShort(m, endianness = Endian.Little)
    val height: SUShort = SUShort(m, endianness = Endian.Little)

    val imageData: SBytes = SBytes(m)

    init {
        val width = icon.width
        val height = icon.height

        this.width.set(width.toUShort())
        this.height.set(height.toUShort())

        val bytesPerLine = ((width + 31) / 32) * 4
        val totalBytes = bytesPerLine * height

        val dataBuffer = DataBuffer(totalBytes)

        for (y in 0 until height) {
            for (lineIntIndex in 0 until bytesPerLine / 4) {
                var currentInt = 0
                val startX = lineIntIndex * 32
                val pixelsToTraverse = 32.coerceAtMost(width - startX)

                for (innerX in 0 until pixelsToTraverse) {
                    val x = startX + innerX

                    val pixelValue = icon.getPixel(x, y)
                    val valueWithoutAlpha = pixelValue and 0x00FFFFFF

                    val pixelBit = if (valueWithoutAlpha > 0) {
                        1
                    } else {
                        0
                    }

                    currentInt = currentInt or (pixelBit shl innerX)
                }

                dataBuffer.putInt(currentInt)
            }
        }

        val imageBytes = dataBuffer.array()
        imageData.set(imageBytes, imageBytes.size)

        this.bytesPerLine.set(bytesPerLine.toUShort())
    }
}

internal enum class AppType(val value: UByte) {
    SPORTS(0x00u),
    GOLF(0x01u),
}
