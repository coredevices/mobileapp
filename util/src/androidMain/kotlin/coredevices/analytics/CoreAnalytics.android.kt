package coredevices.analytics

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.mp.KoinPlatform

actual fun createAnalyticsCache(): Settings {
    return SharedPreferencesSettings.Factory(KoinPlatform.getKoin().get())
        .create("core_analytics_cache")
}