package coredevices.ring.ui

import android.content.Context
import android.text.format.DateFormat
import org.koin.mp.KoinPlatform

actual fun isLocale24HourFormat(): Boolean {
    val context = KoinPlatform.getKoin().get<Context>()
    return DateFormat.is24HourFormat(context)
}