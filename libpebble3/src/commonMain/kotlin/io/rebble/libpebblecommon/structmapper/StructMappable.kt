package io.rebble.libpebblecommon.structmapper

import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian

public abstract class StructMappable internal constructor(endianness: Endian = Endian.Unspecified) : Mappable(endianness) {
    public val m: StructMapper = StructMapper(endianness = endianness, debugTag = this::class.simpleName)

    override fun toBytes(): UByteArray {
        return m.toBytes()
    }

    override fun fromBytes(bytes: DataBuffer) {
        m.fromBytes(bytes)
    }

    override val size: Int get() = m.size
}