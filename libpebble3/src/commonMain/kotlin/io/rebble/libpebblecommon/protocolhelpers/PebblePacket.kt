package io.rebble.libpebblecommon.protocolhelpers

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.exceptions.PacketDecodeException
import io.rebble.libpebblecommon.exceptions.PacketEncodeException
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.DataBuffer

/**
 * Represents a pebble protocol packet
 */
public open class PebblePacket{
    public val endpoint: ProtocolEndpoint
    public val m: StructMapper = StructMapper()
    public var type: UByte? = null
    public val importedLength: UShort?

    internal constructor(endpoint: ProtocolEndpoint) { //TODO: Packet-level endianness?
        this.endpoint = endpoint
        importedLength = null
    }

    internal constructor(packet: UByteArray) {
        val meta = StructMapper()
        val length = SUShort(meta)
        val ep = SUShort(meta)
        meta.fromBytes(DataBuffer(packet))
        if (length.get() != (packet.size - (UShort.SIZE_BYTES * 2)).toUShort())
            throw IllegalArgumentException("Length in packet does not match packet actual size, likely malformed")

        Logger.v { "Importing packet: Len $length | EP $ep" }
        importedLength = length.get()

        this.endpoint =
            ProtocolEndpoint.getByValue(ep.get())
    }

    /**
     * Serializes a framed pebble protocol packet into a byte array
     * @return The serialized packet
     */
    public fun serialize(): UByteArray {
        val content = m.toBytes()
        if (content.isEmpty()) throw PacketEncodeException("Malformed packet: contents empty")
        val meta = StructMapper()
        val length = SUShort(meta, content.size.toUShort())
        val ep = SUShort(meta, endpoint.value)

        return meta.toBytes() + content // Whole packet (meta + content)
    }

    public companion object {
        /**
         * Deserializes a framed pebble protocol packet into a PebblePacket class
         * @param packet the packet to deserialize
         * @return The deserialized packet
         */
        public fun deserialize(packet: UByteArray): PebblePacket {
            val buf = DataBuffer(packet)

            val meta = StructMapper()
            val length = SUShort(meta)
            val ep = SUShort(meta)
            meta.fromBytes(buf)
            if (packet.size <= (2 * UShort.SIZE_BYTES))
                throw PacketDecodeException("Malformed packet: contents empty")
            val lengthVal = length.get().toInt()
            if (lengthVal != (packet.size - (2 * UShort.SIZE_BYTES)))
                throw PacketDecodeException("Malformed packet: bad length (expected ${length.get()}, got ${packet.size - (2 * UShort.SIZE_BYTES)})")
            val ret = PacketRegistry.get(
                ProtocolEndpoint.getByValue(ep.get()),
                packet
            )
            deserialize(buf, ret)
            return ret
        }

        public fun deserialize(data: UByteArray, packet: PebblePacket) {
            return deserialize(DataBuffer(data), packet)
        }

        private fun deserialize(buf: DataBuffer, packet: PebblePacket) {
            try {
                packet.m.fromBytes(buf)
            } catch (e: Exception) {
                throw PacketDecodeException("Failed to decode packet $packet", e)
            }
        }
    }
}