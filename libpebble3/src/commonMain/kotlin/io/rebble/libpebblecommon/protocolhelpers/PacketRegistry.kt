package io.rebble.libpebblecommon.protocolhelpers

import io.rebble.libpebblecommon.exceptions.PacketDecodeException
import io.rebble.libpebblecommon.packets.appFetchIncomingPacketsRegister
import io.rebble.libpebblecommon.packets.appLogPacketsRegister
import io.rebble.libpebblecommon.packets.appReorderIncomingRegister
import io.rebble.libpebblecommon.packets.appRunStatePacketsRegister
import io.rebble.libpebblecommon.packets.appmessagePacketsRegister
import io.rebble.libpebblecommon.packets.audioStreamPacketsRegister
import io.rebble.libpebblecommon.packets.blobdb.blobDB2PacketsRegister
import io.rebble.libpebblecommon.packets.blobdb.blobDBPacketsRegister
import io.rebble.libpebblecommon.packets.blobdb.timelinePacketsRegister
import io.rebble.libpebblecommon.packets.dataLoggingPacketsRegister
import io.rebble.libpebblecommon.packets.getBytesIncomingPacketsRegister
import io.rebble.libpebblecommon.packets.healthSyncPacketsRegister
import io.rebble.libpebblecommon.packets.logDumpPacketsRegister
import io.rebble.libpebblecommon.packets.musicPacketsRegister
import io.rebble.libpebblecommon.packets.phoneControlPacketsRegister
import io.rebble.libpebblecommon.packets.putBytesIncomingPacketsRegister
import io.rebble.libpebblecommon.packets.screenshotPacketsRegister
import io.rebble.libpebblecommon.packets.systemPacketsRegister
import io.rebble.libpebblecommon.packets.timePacketsRegister
import io.rebble.libpebblecommon.packets.voicePacketsRegister

/**
 * Singleton to track endpoint / type discriminators for deserialization
 */
object PacketRegistry {
    private var typeOffsets: MutableMap<ProtocolEndpoint, Int> = mutableMapOf()
    private var typedDecoders: MutableMap<ProtocolEndpoint, MutableMap<UByte, (UByteArray) -> PebblePacket>> =
        mutableMapOf()
    private var universalDecoders: MutableMap<ProtocolEndpoint, (UByteArray) -> PebblePacket> =
        mutableMapOf()

    init {
        systemPacketsRegister()
        timePacketsRegister()
        timelinePacketsRegister()
        blobDBPacketsRegister()
        blobDB2PacketsRegister()
        appmessagePacketsRegister()
        appRunStatePacketsRegister()
        musicPacketsRegister()
        appFetchIncomingPacketsRegister()
        putBytesIncomingPacketsRegister()
        appReorderIncomingRegister()
        screenshotPacketsRegister()
        appLogPacketsRegister()
        phoneControlPacketsRegister()
        logDumpPacketsRegister()
        voicePacketsRegister()
        audioStreamPacketsRegister()
        dataLoggingPacketsRegister()
        getBytesIncomingPacketsRegister()
        healthSyncPacketsRegister()
    }

    /**
     * Register a custom offset for the type discriminator (e.g. if the first byte after frame is not the command)
     * @param endpoint the endpoint to register the new offset to
     * @param offset the new offset, including frame offset (4)
     */
    fun registerCustomTypeOffset(endpoint: ProtocolEndpoint, offset: Int) {
        typeOffsets[endpoint] = offset
    }

    fun register(endpoint: ProtocolEndpoint, decoder: (UByteArray) -> PebblePacket) {
        universalDecoders[endpoint] = decoder
    }

    fun register(endpoint: ProtocolEndpoint, type: UByte, decoder: (UByteArray) -> PebblePacket) {
        if (typedDecoders[endpoint] == null) {
            typedDecoders[endpoint] = mutableMapOf()
        }
        typedDecoders[endpoint]!![type] = decoder
    }

    fun get(endpoint: ProtocolEndpoint, packet: UByteArray): PebblePacket {
        universalDecoders[endpoint]?.let { return it(packet) }

        val epdecoders = typedDecoders[endpoint]
            ?: throw PacketDecodeException("No packet class registered for endpoint $endpoint")

        val typeOffset = if (typeOffsets[endpoint] != null) typeOffsets[endpoint]!! else 4
        val decoder = epdecoders[packet[typeOffset]]
            ?: throw PacketDecodeException("No packet class registered for type ${packet[typeOffset]} of $endpoint")
        return decoder(packet)
    }
}
