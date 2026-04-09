package coredevices.ring.ui

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

actual fun isLocale24HourFormat(): Boolean {
    val hourFormat = NSDateFormatter.dateFormatFromTemplate("j", options = 0u, locale = NSLocale.currentLocale)!!
    return !hourFormat.contains("a")
}