package coredevices.ring.storage

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Local PCM copies of recordings. A recording lives in [dataDir] until it has a cloud copy,
 * after which it only needs the OS-purgeable [cacheDir].
 */
internal class RecordingFiles(
    private val cacheDir: Path,
    private val dataDir: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
) {
    fun cachePath(id: String) = Path(cacheDir, id)

    fun persistentPath(id: String) = Path(dataDir, id)

    fun localPath(id: String): Path? =
        listOf(cachePath(id), persistentPath(id)).firstOrNull { fileSystem.exists(it) }

    fun moveToCache(id: String) {
        val persistent = persistentPath(id)
        if (fileSystem.exists(persistent)) {
            fileSystem.atomicMove(persistent, cachePath(id))
        }
    }

    fun deletePersistent(id: String) {
        fileSystem.delete(persistentPath(id), mustExist = false)
    }
}
