package io.rebble.libpebblecommon.web

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal actual fun getFirmwareDownloadDirectory(context: AppContext): Path {
    val fileManager = NSFileManager.defaultManager
    val cachesDirectory = fileManager.URLsForDirectory(NSCachesDirectory, inDomains = NSUserDomainMask).firstOrNull()
            as? NSURL
        ?: throw IllegalStateException("Unable to get caches directory")
    val path = Path(cachesDirectory.path!!, "fw")
    SystemFileSystem.createDirectories(path, false)
    return path
}