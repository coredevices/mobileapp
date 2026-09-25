package coredevices.pebble

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private fun documentsDirectory(): Path {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).first() as String
    return Path(documents, WATCH_LOGS_FOLDER_NAME)
}

actual fun saveWatchLogCopy(appContext: AppContext, source: Path, fileName: String): String? {
    val directory = documentsDirectory()
    SystemFileSystem.createDirectories(directory)
    val target = Path(directory, fileName)
    SystemFileSystem.source(source).buffered().use { input ->
        SystemFileSystem.sink(target).buffered().use { output -> output.transferFrom(input) }
    }
    return "$WATCH_LOGS_FOLDER_NAME/$fileName"
}

actual fun pruneWatchLogCopies(appContext: AppContext, keep: Int) {
    val directory = documentsDirectory()
    if (SystemFileSystem.metadataOrNull(directory) == null) return
    SystemFileSystem.list(directory)
        .filter { it.name.startsWith(WATCH_LOGS_FILE_PREFIX) }
        .sortedBy { it.name }
        .dropLast(keep)
        .forEach { SystemFileSystem.delete(it, mustExist = false) }
}
