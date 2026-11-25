package io.rebble.libpebblecommon.disk

import io.rebble.libpebblecommon.packets.blobdb.AppMetadata
import io.rebble.libpebblecommon.structmapper.*
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian

internal class PbwBinHeader internal constructor() : StructMappable() {
    /**
     * Major header version.
     */
    internal val headerVersionMajor: SUByte = SUByte(m)

    /**
     * Minor header version.
     */
    internal val headerVersionMinor: SUByte = SUByte(m)

    /**
     * Major sdk version.
     */
    internal val sdkVersionMajor: SUByte = SUByte(m)

    /**
     * Minor sdk version.
     */
    internal val sdkVersionMinor: SUByte = SUByte(m)

    /**
     * Major app version.
     */
    internal val appVersionMajor: SUByte = SUByte(m)

    /**
     * Minor app version.
     */
    internal val appVersionMinor: SUByte = SUByte(m)

    /**
     * Size of the app payload in bytes
     */
    internal val appSize: SUShort = SUShort(m)

    /**
     * ??? (Presumably offset where app payload starts?)
     */
    internal val appOffset: SUInt = SUInt(m)

    /**
     * CRC checksum of the app payload
     */
    internal val crc: SUInt = SUInt(m)

    /**
     * Name of the app
     */
    internal val appName: SFixedString = SFixedString(m, 32)

    /**
     * Name of the company that made the app
     */
    internal val companyName: SFixedString = SFixedString(m, 32)

    /**
     * Resource ID of the primary icon.
     */
    internal val icon: SUInt = SUInt(m, endianness = Endian.Little)

    /**
     * ???
     */
    internal val symbolTableAddress: SUInt = SUInt(m)

    /**
     * List of app install flags. Should be forwarded to the watch when inserting into BlobDB.
     */
    internal val flags: SUInt = SUInt(m, endianness = Endian.Little)

    /**
     * ???
     */
    internal val numRelocationListEntries: SUInt = SUInt(m)

    /**
     * UUID of the app
     */
    internal val uuid: SUUID = SUUID(m)

    companion object {
        internal const val SIZE: Int = 8 + 2 + 2 + 2 + 2 + 4 + 4 + 32 + 32 + 4 + 4 + 4 + 4 + 16

        /**
         * Parse existing Pbw binary payload header. You should read [SIZE] bytes from the binary
         * payload and pass it into this method.
         *
         * @throws IllegalArgumentException if header is not valid pebble app header
         */
        internal fun parseFileHeader(data: UByteArray): PbwBinHeader {
            if (data.size != SIZE) {
                throw IllegalArgumentException(
                    "Read data from the file should be exactly $SIZE bytes"
                )
            }

            val buffer = DataBuffer(data)

            val sentinel = buffer.getBytes(8)
            if (!sentinel.contentEquals(EXPECTED_SENTINEL)) {
                throw IllegalArgumentException("Sentinel does not match")
            }

            return PbwBinHeader().also {
                it.fromBytes(buffer)
            }
        }

        /**
         * First 8 bytes of the header, spelling the word "PBLAPP" in ASCII,
         * followed by two zeros.
         */
        private val EXPECTED_SENTINEL = ubyteArrayOf(
            0x50u, 0x42u, 0x4Cu, 0x41u, 0x50u, 0x50u, 0x00u, 0x00u
        )
    }
}