package coredevices.ring.storage

import kotlinx.io.files.Path
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual class BackupZipWriter actual constructor(outputPath: Path) {
    private val zos = ZipOutputStream(FileOutputStream(outputPath.toString()))

    actual fun addEntry(name: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(data)
        zos.closeEntry()
    }

    actual fun close() {
        zos.close()
    }
}
