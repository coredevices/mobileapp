package coredevices.util

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleUsesMetricSystem
import platform.Foundation.currentLocale

actual fun deviceDefaultWeatherUnit(): WeatherUnit {
    val usesMetric = NSLocale.currentLocale.objectForKey(NSLocaleUsesMetricSystem) as? Boolean ?: true
    if (!usesMetric) return WeatherUnit.Imperial
    val country = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String
    if (country == "GB") return WeatherUnit.UkHybrid
    return WeatherUnit.Metric
}
