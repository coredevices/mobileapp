package coredevices.coreapp.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.text.intl.Locale
import coredevices.coreapp.BuildConfig
import kotlinx.datetime.TimeZone
import org.koin.mp.KoinPlatform

actual fun getLogsCacheDir(): String {
    val context = KoinPlatform.getKoin().get<Context>().applicationContext
    return context.cacheDir.resolve("logs/").absolutePath
}

actual fun generateDeviceSummaryPlatformDetails(): String {
    val context = KoinPlatform.getKoin().get<Context>().applicationContext
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = try {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        memInfo
    } catch (e: Exception) {
        null
    }
    return buildString {
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
        appendLine("Device Summary")
        appendLine("Model: ${Build.MODEL}")
        appendLine("Brand: ${Build.BRAND}")
        appendLine("Product: ${Build.PRODUCT}")
        appendLine("Device: ${Build.DEVICE}")
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Board: ${Build.BOARD}")
        appendLine("Hardware: ${Build.HARDWARE}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appendLine("SOC Model: ${Build.SOC_MODEL}")
            appendLine("SOC Manufacturer: ${Build.SOC_MANUFACTURER}")
        }
        appendLine("Locale: ${Locale.current.toLanguageTag()}")
        appendLine("Timezone: ${TimeZone.currentSystemDefault().id}")
        appendLine()

        appendLine("SDK: ${Build.VERSION.SDK_INT}")
        appendLine("Release: ${Build.VERSION.RELEASE}")
        appendLine("Incremental: ${Build.VERSION.INCREMENTAL}")
        appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
        appendLine()

        memoryInfo?.let {
            appendLine("Total Memory: ${memoryInfo.totalMem} (${memoryInfo.totalMem / (1024 * 1024 * 1024)} GB)")
            appendLine("Low Memory: ${memoryInfo.lowMemory}")
            appendLine("Threshold: ${memoryInfo.threshold}")
        } ?: appendLine("Memory Info: <Failed>")
    }
}