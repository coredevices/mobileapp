package coredevices.util.storage

import io.rebble.libpebblecommon.connection.AppContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

actual fun AppContext.appStorageBreakdown(): List<StorageUsage> {
    val fm = NSFileManager.defaultManager
    fun systemDir(directory: ULong): String? =
        NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).firstOrNull() as String?

    val dirs = buildList {
        systemDir(NSDocumentDirectory)?.let { add("Documents" to it) }
        systemDir(NSCachesDirectory)?.let { add("Caches" to it) }
        systemDir(NSApplicationSupportDirectory)?.let { add("Application Support" to it) }
        add("tmp" to NSTemporaryDirectory())
    }
    return dirs.map { (label, path) -> StorageUsage(label, directorySize(fm, path)) }
}

private fun directorySize(fm: NSFileManager, path: String): Long {
    if (!fm.fileExistsAtPath(path)) return 0L
    val enumerator = fm.enumeratorAtPath(path) ?: return 0L
    var total = 0L
    while (true) {
        val name = enumerator.nextObject() as? String ?: break
        val attrs = fm.attributesOfItemAtPath("$path/$name", null)
        total += (attrs?.get(NSFileSize) as? Long) ?: 0L
    }
    return total
}
