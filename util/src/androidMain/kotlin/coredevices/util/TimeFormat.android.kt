package coredevices.util

import android.content.Context
import android.text.format.DateFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.koin.mp.KoinPlatform
import java.time.format.DateTimeFormatter
import java.util.Locale

actual fun isLocale24HourFormat(): Boolean {
    val context = KoinPlatform.getKoin().get<Context>()
    return DateFormat.is24HourFormat(context)
}

actual fun formatDate(date: LocalDate, skeleton: String): String {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date.toJavaLocalDate())
}
