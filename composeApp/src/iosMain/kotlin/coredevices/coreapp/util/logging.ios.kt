package coredevices.coreapp.util

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUserDomainMask
import platform.Foundation.currentLocale
import platform.Foundation.defaultTimeZone
import platform.Foundation.localeIdentifier
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

actual fun getLogsCacheDir(): String {
    val cacheDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    return requireNotNull(cacheDirectory?.path) + "/logs"
}

private data class UnameResult(
    val sysname: String,
    val nodename: String,
    val release: String,
    val version: String,
    val machine: String
)

actual fun generateDeviceSummaryPlatformDetails(): String {
    val appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "Unknown"
    val appVersionShort = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "Unknown"
    val uname = memScoped {
        val utsname = alloc<utsname>()
        uname(utsname.ptr)
        UnameResult(
            sysname = utsname.sysname.toKString(),
            nodename = utsname.nodename.toKString(),
            release = utsname.release.toKString(),
            version = utsname.version.toKString(),
            machine = utsname.machine.toKString()
        )
    }
    val device = UIDevice.currentDevice
    val systemVersion = device.systemVersion
    val systemName = device.systemName
    val model = uname.machine
    val name = device.name
    val locale = NSLocale.currentLocale().localeIdentifier
    val timezone = NSTimeZone.defaultTimeZone.name

    return buildString {
        appendLine("Version: $appVersion")
        appendLine("Short Version: $appVersionShort")
        appendLine("Device Summary")
        appendLine("Name: $name")
        appendLine("Model: $model")
        appendLine("System Name: $systemName")
        appendLine("System Version: $systemVersion")
        appendLine("Locale: $locale")
        appendLine("Timezone: $timezone")
    }
}