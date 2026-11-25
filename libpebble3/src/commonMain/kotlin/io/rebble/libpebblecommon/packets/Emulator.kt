package io.rebble.libpebblecommon.packets

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.DataBuffer

internal const val HEADER_SIGNATURE: UInt = 0xFEEDU
internal const val FOOTER_SIGNATURE: UInt = 0xBEEFU

internal open class QemuPacket(protocol: Protocol) {
    val m: StructMapper = StructMapper()
    val signature: SUShort = SUShort(m, HEADER_SIGNATURE.toUShort())
    val protocol: SUShort = SUShort(m, protocol.value)
    val length: SUShort = SUShort(m)

    enum class Protocol(val value: UShort) {
        SPP(1U),
        Tap(2U),
        BluetoothConnection(3U),
        Compass(4U),
        Battery(5U),
        Accel(6U),
        Vibration(7U),
        Button(8U),
        TimeFormat(9U),
        TimelinePeek(10U),
        ContentSize(11U),
        RebbleTest(100U),
        Invalid(UShort.MAX_VALUE)
    }

    class QemuSPP(data: UByteArray? = null): QemuPacket(Protocol.SPP) {
        val payload: SBytes = SBytes(m, data?.size?:-1, data?: ubyteArrayOf())
        val footer: SUShort = SUShort(m, FOOTER_SIGNATURE.toUShort())

        init {
            if (data == null) payload.linkWithSize(length)
        }
    }

    companion object {
        private val logger = Logger.withTag("Emulator")

        fun deserialize(packet: UByteArray): QemuPacket {
            val buf = DataBuffer(packet)
            val meta = StructMapper()
            val header = SUShort(meta)
            val protocol = SUShort(meta)
            meta.fromBytes(buf)
            buf.rewind()
            return when (protocol.get()) {
                Protocol.SPP.value -> QemuSPP().also { it.m.fromBytes(buf) }
                else -> {
                    logger.w { "QEMU packet left generic" }
                    QemuPacket(Protocol.Invalid).also { it.m.fromBytes(buf) }
                }
            }
        }
    }

    fun serialize(): UByteArray {
        length.set((m.size-(4*UShort.SIZE_BYTES)).toUShort()) //total size - header+footer = payload length
        return m.toBytes()
    }
}