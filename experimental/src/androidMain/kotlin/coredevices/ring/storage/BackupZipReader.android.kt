package coredevices.ring.storage

import kotlinx.io.files.Path
import java.io.FileInputStream
import java.util.zip.ZipInputStream

actual class BackupZipReader actual constructor(inputPath: Path) {
    private val file = java.io.File(inputPath.toString())

    actual fun readAllEntries(): List<ZipFileEntry> {
        val entries = mutableListOf<ZipFileEntry>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries.add(ZipFileEntry(entry.name, zis.readBytes()))
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    actual fun close() {}
}
