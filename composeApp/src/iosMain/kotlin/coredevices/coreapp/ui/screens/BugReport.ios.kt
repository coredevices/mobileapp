package coredevices.coreapp.ui.screens

import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun isThirdPartyTest(): Boolean = false

actual fun getExperimentalDebugInfoDirectory(): String {
    val fm = NSFileManager.defaultManager
    val path = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first()!! as NSURL
    return Path(path.path!!, "haversine_debug").toString()
}
