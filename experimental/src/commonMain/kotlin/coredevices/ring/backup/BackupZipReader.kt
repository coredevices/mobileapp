package coredevices.ring.backup

import com.oldguy.common.io.File
import com.oldguy.common.io.FileMode
import com.oldguy.common.io.ZipFile
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.files.Path
import kotlinx.io.readByteArray

/**
 * Reads zip entries on demand. Not safe for concurrent reads; serialise callers.
 */
class BackupZipReader(inputPath: Path) : BackupEntrySource {
    private val zip = ZipFile(File(inputPath.toString()), FileMode.Read)

    suspend fun open() = zip.open()

    override val entryNames: Set<String>
        get() = zip.entries.mapTo(LinkedHashSet()) { it.name }

    suspend fun readEntry(name: String, sink: Sink) {
        zip.readEntry(name) { _, content, count, _ ->
            sink.write(content, 0, count.toInt())
        }
    }

    override suspend fun readBytes(name: String): ByteArray {
        val buffer = Buffer()
        readEntry(name, buffer)
        return buffer.readByteArray()
    }

    suspend fun close() = zip.close()
}
