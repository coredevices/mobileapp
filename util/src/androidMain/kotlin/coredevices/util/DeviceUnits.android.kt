package coredevices.util

import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import java.util.Locale

actual fun deviceDefaultWeatherUnit(): WeatherUnit {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return when (LocaleData.getMeasurementSystem(ULocale.getDefault())) {
            LocaleData.MeasurementSystem.US -> WeatherUnit.Imperial
            LocaleData.MeasurementSystem.UK -> WeatherUnit.UkHybrid
            else -> WeatherUnit.Metric
        }
    }
    return when (Locale.getDefault().country) {
        "US", "LR", "MM" -> WeatherUnit.Imperial
        "GB" -> WeatherUnit.UkHybrid
        else -> WeatherUnit.Metric
    }
}
