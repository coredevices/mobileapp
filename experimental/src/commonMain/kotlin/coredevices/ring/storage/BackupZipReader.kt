package coredevices.ring.storage

import kotlinx.io.files.Path

data class ZipFileEntry(
    val name: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZipFileEntry) return false
        return name == other.name && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = 31 * name.hashCode() + data.contentHashCode()
}

expect class BackupZipReader(inputPath: Path) {
    fun readAllEntries(): List<ZipFileEntry>
    fun close()
}
