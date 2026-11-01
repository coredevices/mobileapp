package coredevices.coreapp.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.health.HealthStats
import android.os.health.PackageHealthStats
import android.os.health.PidHealthStats
import android.os.health.ProcessHealthStats
import android.os.health.ServiceHealthStats
import android.os.health.SystemHealthManager
import android.os.health.UidHealthStats
import androidx.compose.ui.text.intl.Locale
import coredevices.coreapp.appVersionCode
import coredevices.coreapp.appVersionName
import coredevices.util.isDebugBuild
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.datetime.TimeZone
import org.koin.mp.KoinPlatform
import kotlin.time.Duration.Companion.milliseconds

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
    val powerManager = try {
        context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    } catch (e: Exception) {
        null
    }
    val isLowPowerMode = try {
        powerManager?.isPowerSaveMode ?: false
    } catch (e: Exception) {
        null
    }
    val ignoringBatteryOptimisations = try {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        null
    }
    return buildString {
        appendLine("Version: ${context.appVersionName} (${context.appVersionCode}) ${if (AppContext(context).isDebugBuild()) "debug" else "release"}")
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
        appendLine("Low Power Mode: ${isLowPowerMode ?: "<Failed>"}")
        appendLine("Ignoring Battery Optimisations: ${ignoringBatteryOptimisations ?: "<Failed>"}")
        appendLine()

        appendLine("Power stats (since unplugged):")
        try {
            val healthManager = context.getSystemService(SystemHealthManager::class.java)
            appendHealthStats(healthManager.takeMyUidSnapshot(), "  ")
        } catch (e: Exception) {
            appendLine("  <Failed: $e>")
        }
    }
}

// Each class's keys occupy a distinct numeric range, so one map names keys at every nesting level.
private val healthKeyNames: Map<Int, String> by lazy {
    listOf(
        UidHealthStats::class.java,
        PidHealthStats::class.java,
        ProcessHealthStats::class.java,
        PackageHealthStats::class.java,
        ServiceHealthStats::class.java,
    ).flatMap { it.fields.toList() }.associate { it.getInt(null) to it.name }
}

private fun formatMeasurement(name: String, value: Long): String = when {
    name.endsWith("_MS") -> value.milliseconds.toString()
    name.endsWith("_POWER_MAMS") -> "%.1f mAh".format(value / 3_600_000.0)
    else -> value.toString()
}

private fun formatTimer(count: Int, timeMs: Long) = "count=$count time=${timeMs.milliseconds}"

private fun StringBuilder.appendHealthStats(stats: HealthStats, indent: String) {
    fun name(key: Int) = healthKeyNames[key] ?: key.toString()
    for (i in 0 until stats.measurementKeyCount) {
        val key = stats.getMeasurementKeyAt(i)
        val value = stats.getMeasurement(key)
        if (value != 0L) appendLine("$indent${name(key)}: ${formatMeasurement(name(key), value)}")
    }
    for (i in 0 until stats.timerKeyCount) {
        val key = stats.getTimerKeyAt(i)
        val timer = stats.getTimer(key)
        if (timer.count != 0 || timer.time != 0L) appendLine("$indent${name(key)}: ${formatTimer(timer.count, timer.time)}")
    }
    for (i in 0 until stats.measurementsKeyCount) {
        val key = stats.getMeasurementsKeyAt(i)
        val map = stats.getMeasurements(key).filterValues { it != 0L }
        if (map.isEmpty()) continue
        appendLine("$indent${name(key)}:")
        map.forEach { (k, v) -> appendLine("$indent  $k: $v") }
    }
    for (i in 0 until stats.timersKeyCount) {
        val key = stats.getTimersKeyAt(i)
        val map = stats.getTimers(key).filterValues { it.count != 0 || it.time != 0L }
        if (map.isEmpty()) continue
        appendLine("$indent${name(key)}:")
        map.entries.sortedByDescending { it.value.time }.forEach { (k, v) ->
            appendLine("$indent  $k: ${formatTimer(v.count, v.time)}")
        }
    }
    for (i in 0 until stats.statsKeyCount) {
        val key = stats.getStatsKeyAt(i)
        val children = stats.getStats(key)
            .mapValues { (_, child) -> buildString { appendHealthStats(child, "$indent    ") } }
            .filterValues { it.isNotEmpty() }
        if (children.isEmpty()) continue
        appendLine("$indent${name(key)}:")
        children.forEach { (k, body) ->
            appendLine("$indent  $k:")
            append(body)
        }
    }
}