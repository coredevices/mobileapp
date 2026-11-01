package coredevices.ring.backup

import com.oldguy.common.io.File
import com.oldguy.common.io.FileMode
import com.oldguy.common.io.ZipEntry
import com.oldguy.common.io.ZipFile
import kotlinx.io.Source
import kotlinx.io.files.Path

/**
 * Writes zip entries straight to disk; a streamed entry only ever holds one chunk in memory.
 */
class BackupZipWriter(outputPath: Path) {
    private val zip = ZipFile(File(outputPath.toString()), FileMode.Write)

    suspend fun open() = zip.open()

    suspend fun addEntry(name: String, data: ByteArray) {
        var pending: ByteArray? = data
        zip.addEntry(ZipEntry(name)) {
            pending.also { pending = null } ?: EMPTY
        }
    }

    suspend fun addEntry(name: String, source: Source) {
        val buffer = ByteArray(CHUNK_SIZE)
        zip.addEntry(ZipEntry(name)) {
            val read = source.readAtMostTo(buffer)
            if (read <= 0) EMPTY else buffer.copyOf(read)
        }
    }

    suspend fun close() = zip.close()

    private companion object {
        const val CHUNK_SIZE = 64 * 1024
        val EMPTY = ByteArray(0)
    }
}
