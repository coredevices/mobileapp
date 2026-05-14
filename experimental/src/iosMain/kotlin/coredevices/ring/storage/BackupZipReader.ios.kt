package coredevices.ring.storage

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

actual class BackupZipReader actual constructor(private val inputPath: Path) {
    // TODO: iOS currently reads from directory structure only (matching BackupZipWriter output).
    // Cross-platform import of Android-created .zip files requires a proper zip library (e.g., minizip via cinterop).
    actual fun readAllEntries(): List<ZipFileEntry> {
        val dirPath = inputPath.toString().removeSuffix(".zip")
        val entries = mutableListOf<ZipFileEntry>()
        collectFiles(dirPath, "", entries)
        return entries
    }

    private fun collectFiles(basePath: String, relativePath: String, entries: MutableList<ZipFileEntry>) {
        val fm = platform.Foundation.NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(
            if (relativePath.isEmpty()) basePath else "$basePath/$relativePath",
            error = null
        ) ?: return
        for (item in contents) {
            val name = item as? String ?: continue
            val fullRelative = if (relativePath.isEmpty()) name else "$relativePath/$name"
            val fullPath = "$basePath/$fullRelative"
            var isDir = false
            @Suppress("CAST_NEVER_SUCCEEDS")
            fm.fileExistsAtPath(fullPath)
            // Check if directory by trying to list contents
            val subContents = fm.contentsOfDirectoryAtPath(fullPath, error = null)
            if (subContents != null && subContents.isNotEmpty()) {
                collectFiles(basePath, fullRelative, entries)
            } else {
                val path = Path(fullPath)
                if (SystemFileSystem.exists(path)) {
                    val source = SystemFileSystem.source(path)
                    val data = source.buffered().readByteArray()
                    source.close()
                    entries.add(ZipFileEntry(fullRelative, data))
                }
            }
        }
    }

    actual fun close() {}
}
