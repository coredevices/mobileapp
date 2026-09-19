package coredevices.ring.service

import kotlinx.io.files.Path
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun getCachedFirmwareDirectory(): Path {
    val fileManager = NSFileManager.defaultManager
    val appSupport = fileManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
    val dir = appSupport.URLByAppendingPathComponent("ring_firmware", true)!!
    fileManager.createDirectoryAtURL(dir, true, null, null)
    return Path(dir.path!!)
}