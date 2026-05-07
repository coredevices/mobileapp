package io.rebble.libpebblecommon.disk.pbw

/**
 * Reader for Pebble's `.pbpack` resource pack format.
 *
 * A `.pbpack` is a flat binary file shipped at `<platform>/app_resources.pbpack`
 * inside every `.pbw`. It bundles the watchapp's compiled resources (PNGs,
 * fonts, raw blobs) into a single file that the watch firmware mounts and
 * indexes by integer resource id.
 *
 * Format (all little-endian):
 *
 * ```
 * [0..12)   manifest:   uint32 numFiles, uint32 contentCrc, uint32 timestamp
 * [12..4108) table:     256 × { uint32 fileId, uint32 offset, uint32 length, uint32 crc }
 *                       fileId=0 marks unused slots after numFiles real entries
 * [4108..)  contents:   resource bytes, packed back-to-back; entry.offset is
 *                       relative to this contents region, NOT the file start
 * ```
 *
 * The fileId field counts up from 1 for the first valid entry. Some
 * historical `.pbpack`s reuse content (multiple table entries pointing at
 * the same offset), so the table is the authoritative ordering — content
 * itself is just a byte pool.
 *
 * Format reference: https://github.com/Snack-X/pebble-language-pack/blob/master/sdk/pbpack.py
 *
 * This reader is **best-effort and read-only**: it skips the per-entry CRC
 * validation (the watch firmware verifies on load; we just want the bytes
 * for display purposes) and returns null on any parse error rather than
 * throwing. Callers that need integrity guarantees should layer that on
 * separately.
 */
internal object PbwResourcePack {
    private const val MANIFEST_SIZE = 12
    private const val TABLE_ENTRY_SIZE = 16
    private const val MAX_NUM_FILES = 256
    private const val CONTENT_START = MANIFEST_SIZE + (MAX_NUM_FILES * TABLE_ENTRY_SIZE) // 4108

    /**
     * Returns the raw bytes of the resource at [resourceIndex] (0-based,
     * matching the order resources are declared in `appinfo.json`'s
     * `resources.media` array). Returns null when:
     *  - the pack is too small / truncated
     *  - the manifest's numFiles is zero or out of range
     *  - the requested index is past the end of the resource list
     *  - the entry's offset/length lies outside the file
     *
     * Does NOT verify the per-entry CRC — see class doc.
     */
    fun extractResource(packBytes: ByteArray, resourceIndex: Int): ByteArray? {
        if (resourceIndex < 0) return null
        if (packBytes.size < CONTENT_START) return null

        val numFiles = readU32LE(packBytes, 0).toInt()
        if (numFiles <= 0 || numFiles > MAX_NUM_FILES) return null
        if (resourceIndex >= numFiles) return null

        val tableEntryStart = MANIFEST_SIZE + (resourceIndex * TABLE_ENTRY_SIZE)
        // Bounds check: the table entry itself must be within the table region.
        if (tableEntryStart + TABLE_ENTRY_SIZE > CONTENT_START) return null

        val fileId = readU32LE(packBytes, tableEntryStart).toInt()
        // fileId == 0 marks the end of the populated table; treat as missing.
        if (fileId == 0) return null

        val relativeOffset = readU32LE(packBytes, tableEntryStart + 4).toInt()
        val length = readU32LE(packBytes, tableEntryStart + 8).toInt()
        if (length < 0 || relativeOffset < 0) return null

        val absoluteOffset = CONTENT_START + relativeOffset
        if (absoluteOffset + length > packBytes.size) return null

        return packBytes.copyOfRange(absoluteOffset, absoluteOffset + length)
    }

    private fun readU32LE(bytes: ByteArray, offset: Int): UInt {
        // Manual little-endian decode keeps this kotlinx-io / okio independent
        // and works identically across all KMP targets.
        val b0 = bytes[offset].toUByte().toUInt()
        val b1 = bytes[offset + 1].toUByte().toUInt()
        val b2 = bytes[offset + 2].toUByte().toUInt()
        val b3 = bytes[offset + 3].toUByte().toUInt()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
