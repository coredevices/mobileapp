package io.rebble.libpebblecommon.structmapper

import io.rebble.libpebblecommon.exceptions.PacketDecodeException
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian

/**
 * Maps class properties to a struct equivalent
 */
public class StructMapper internal constructor(endianness: Endian = Endian.Unspecified, private val debugTag: String? = null): Mappable(endianness) {
    private var struct: MutableList<Mappable> = mutableListOf()

    /**
     * Register a mappable object with the StructMapper (tracks index + auto serialization based on declaration order)
     * Ideally only use this within mappable type definitions
     */
    public fun register(type: Mappable): Int {
        struct.add(type)
        return struct.size - 1
    }

    /**
     * Get the struct as a list of Mappables
     * @return The list of mappables
     */
    public fun getStruct(): List<Mappable> {
        return struct.toList()
    }

    override fun toBytes(): UByteArray {
        var bytes = ubyteArrayOf()
        getStruct().forEach {
            bytes += it.toBytes()
        }
        return bytes
    }

    override fun fromBytes(bytes: DataBuffer) {
        getStruct().forEachIndexed { i: Int, mappable: Mappable ->
            try {
                mappable.fromBytes(bytes)
            }catch (e: Exception) {
                throw PacketDecodeException("Unable to deserialize mappable ${mappable::class.simpleName} at index $i (${mappable}) ($debugTag)\n${bytes.array().toHexString()}", e)
            }

        }
    }

    override val size: Int
        get() = getStruct().fold(0, {t,el -> t+el.size})
}