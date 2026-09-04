package coredevices.pebble.ui

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun getTempFwPath(appContext: AppContext): Path {
    val fm = NSFileManager.defaultManager
    val nsUrl = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first()!! as NSURL
    val path = Path(nsUrl.path!!, "temp.pbz")
    return path
}

// Documents/sideload.pbz — reachable over AFC house-arrest (`go-ios file push --app`) on a dev build
// without UIFileSharingEnabled, so it never becomes visible to Files/Finder.
actual fun getSideloadDropPath(appContext: AppContext): Path {
    val fm = NSFileManager.defaultManager
    val nsUrl = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first()!! as NSURL
    return Path(nsUrl.path!!, "sideload.pbz")
}