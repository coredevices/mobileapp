package io.rebble.libpebblecommon.util

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun getTempFilePath(
    appContext: AppContext,
    name: String,
    subdir: String?,
): Path {
    val fm = NSFileManager.defaultManager
    val nsUrl = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first()!! as NSURL
    val path = Path(nsUrl.path!!, name)
    return path
}