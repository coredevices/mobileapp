package coredevices.util

import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import java.util.Locale

actual fun deviceDefaultWeatherUnit(): WeatherUnit {
    val locale = Locale.getDefault()
    // ICU infers a region for region-less locales, and "und"/"en" both maximise to en-Latn-US,
    // so an unresolved locale would otherwise read as Imperial.
    if (locale.country.isEmpty()) return WeatherUnit.Metric
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return when (LocaleData.getMeasurementSystem(ULocale.forLocale(locale))) {
            LocaleData.MeasurementSystem.US -> WeatherUnit.Imperial
            LocaleData.MeasurementSystem.UK -> WeatherUnit.UkHybrid
            else -> WeatherUnit.Metric
        }
    }
    return when (locale.country) {
        "US", "LR", "MM" -> WeatherUnit.Imperial
        "GB" -> WeatherUnit.UkHybrid
        else -> WeatherUnit.Metric
    }
}
