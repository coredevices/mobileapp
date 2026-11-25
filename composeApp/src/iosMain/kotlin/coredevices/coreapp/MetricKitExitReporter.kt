package coredevices.coreapp

import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import platform.MetricKit.MXMetricManager
import platform.MetricKit.MXMetricManagerSubscriberProtocol
import platform.MetricKit.MXMetricPayload
import platform.darwin.NSObject

/** MetricKit delivers daily aggregated exit counts (iOS has no per-exit reason API). */
class MetricKitExitReporter(
    private val coreAnalytics: CoreAnalytics,
) : NSObject(), MXMetricManagerSubscriberProtocol {
    private val logger = Logger.withTag("MetricKitExitReporter")

    fun start() {
        MXMetricManager.sharedManager.addSubscriber(this)
    }

    override fun didReceiveMetricPayloads(payloads: List<*>) {
        payloads.filterIsInstance<MXMetricPayload>().forEach { payload ->
            val exits = payload.applicationExitMetrics ?: return@forEach
            val fg = exits.foregroundExitData
            val bg = exits.backgroundExitData
            val params = mapOf<String, Any>(
                "app_version" to (payload.latestApplicationVersion ?: ""),
                "fg_normal" to fg.cumulativeNormalAppExitCount.toLong(),
                "fg_memory_limit" to fg.cumulativeMemoryResourceLimitExitCount.toLong(),
                "fg_watchdog" to fg.cumulativeAppWatchdogExitCount.toLong(),
                "fg_bad_access" to fg.cumulativeBadAccessExitCount.toLong(),
                "fg_illegal_instruction" to fg.cumulativeIllegalInstructionExitCount.toLong(),
                "fg_abnormal" to fg.cumulativeAbnormalExitCount.toLong(),
                "bg_normal" to bg.cumulativeNormalAppExitCount.toLong(),
                "bg_memory_limit" to bg.cumulativeMemoryResourceLimitExitCount.toLong(),
                "bg_memory_pressure" to bg.cumulativeMemoryPressureExitCount.toLong(),
                "bg_cpu_limit" to bg.cumulativeCPUResourceLimitExitCount.toLong(),
                "bg_watchdog" to bg.cumulativeAppWatchdogExitCount.toLong(),
                "bg_task_timeout" to bg.cumulativeBackgroundTaskAssertionTimeoutExitCount.toLong(),
                "bg_locked_file" to bg.cumulativeSuspendedWithLockedFileExitCount.toLong(),
                "bg_bad_access" to bg.cumulativeBadAccessExitCount.toLong(),
                "bg_illegal_instruction" to bg.cumulativeIllegalInstructionExitCount.toLong(),
                "bg_abnormal" to bg.cumulativeAbnormalExitCount.toLong(),
            )
            logger.i { "App exit counts: $params" }
            coreAnalytics.logEvent("app.exit_counts", params)
        }
    }
}
