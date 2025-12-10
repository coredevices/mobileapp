package coredevices.pebble.ui

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun getTempFwPath(appContext: AppContext): Path {
    val fm = NSFileManager.defaultManager
    val nsUrl = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first()!! as NSURL
    val path = Path(nsUrl.path!!, "temp.pbz")
    return path
}