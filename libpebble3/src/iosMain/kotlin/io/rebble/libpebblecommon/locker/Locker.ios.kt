package io.rebble.libpebblecommon.locker

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun getLockerPBWCacheDirectory(context: AppContext): Path {
    val fileManager = NSFileManager.defaultManager
    val supportDir = fileManager.URLsForDirectory(NSApplicationSupportDirectory, inDomains = NSUserDomainMask).firstOrNull()
        as? NSURL
        ?: throw IllegalStateException("Unable to get application support directory")
    val path = Path(supportDir.path!!, "pbw")
    SystemFileSystem.createDirectories(path, false)
    return path
}

actual fun getLockerPBWCacheLegacyDirectory(context: AppContext): Path? {
    val fileManager = NSFileManager.defaultManager
    val cachesDir = fileManager.URLsForDirectory(NSCachesDirectory, inDomains = NSUserDomainMask).firstOrNull()
        as? NSURL ?: return null
    val legacyPath = Path(cachesDir.path!!, "pbw")
    return if (SystemFileSystem.exists(legacyPath)) legacyPath else null
}