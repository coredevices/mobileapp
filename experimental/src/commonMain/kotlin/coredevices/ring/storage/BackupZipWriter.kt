package coredevices.ring.storage

import kotlinx.io.files.Path

expect class BackupZipWriter(outputPath: Path) {
    fun addEntry(name: String, data: ByteArray)
    fun close()
}
