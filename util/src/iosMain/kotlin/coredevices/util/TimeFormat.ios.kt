package coredevices.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages

actual fun isLocale24HourFormat(): Boolean {
    val hourFormat = NSDateFormatter.dateFormatFromTemplate("j", options = 0u, locale = NSLocale.currentLocale)!!
    return !hourFormat.contains("a")
}

actual fun formatDate(date: LocalDate, skeleton: String): String {
    val locale = (NSLocale.preferredLanguages.firstOrNull() as? String)?.let { NSLocale(localeIdentifier = it) } ?: NSLocale.currentLocale
    val formatter = NSDateFormatter()
    formatter.locale = locale
    formatter.dateFormat = NSDateFormatter.dateFormatFromTemplate(skeleton, options = 0u, locale = locale) ?: skeleton
    return formatter.stringFromDate(date.atStartOfDayIn(TimeZone.currentSystemDefault()).toNSDate())
}
