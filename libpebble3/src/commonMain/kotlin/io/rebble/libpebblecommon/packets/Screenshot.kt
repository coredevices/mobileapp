package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUnboundBytes
import io.rebble.libpebblecommon.structmapper.StructMappable

internal class ScreenshotRequest : PebblePacket(ProtocolEndpoint.SCREENSHOT) {
    /**
     * Command. Only one command (take screenshot, value 0) is supported for now
     */
    val command: SUByte = SUByte(m, 0u)
}

internal sealed class ScreenshotResponse : PebblePacket(ProtocolEndpoint.SCREENSHOT)

internal class ScreenshotData : ScreenshotResponse() {
    val data: SUnboundBytes = SUnboundBytes(m)
}

internal class ScreenshotHeader : ScreenshotResponse() {
    /**
     * @see ScreenshotResponseCode
     */
    val responseCode: SUByte = SUByte(m)

    /**
     * @see ScreenshotVersion
     */
    val version: SUInt = SUInt(m)

    val width: SUInt = SUInt(m)
    val height: SUInt = SUInt(m)
    val data: SUnboundBytes = SUnboundBytes(m)
}

internal enum class ScreenshotResponseCode(val rawCode: UByte) {
    OK(0u),
    MalformedCommand(1u),
    OutOfMemory(2u),
    AlreadyInProgress(3u);

    companion object {
        fun fromRawCode(rawCode: UByte): ScreenshotResponseCode {
            return values().firstOrNull { it.rawCode == rawCode }
                ?: error("Unknown screenshot response code: $rawCode")
        }
    }
}

internal enum class ScreenshotVersion(val rawCode: UInt, val bitsPerPixel: Int) {
    BLACK_WHITE_1_BIT(1u, 1),
    COLOR_8_BIT(2u, 8);

    companion object {
        fun fromRawCode(rawCode: UInt): ScreenshotVersion {
            return values().firstOrNull { it.rawCode == rawCode }
                ?: error("Unknown screenshots version: $rawCode")
        }
    }
}

internal fun screenshotPacketsRegister() {
    PacketRegistry.register(ProtocolEndpoint.SCREENSHOT) {
        // The services needs to decide whether each message is a header or not (but this
        // needs to create some kind of Screenshot packet).
        ScreenshotData()
    }
}