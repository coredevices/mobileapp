package coredevices.util.sync

import co.touchlab.kermit.Logger
import coredevices.util.Platform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSNotificationCenter
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.darwin.dispatch_get_main_queue

private val logger = Logger.withTag("IosSyncScheduler")

/**
 * Runs pending sync in-process while the app is alive (on enqueue, when the network comes back and
 * when the app becomes active), holding a background task assertion so a run started in the
 * foreground can finish after the user leaves. A BGProcessingTask requiring connectivity covers
 * the suspended/killed case.
 */
class IosSyncScheduler(
    private val syncManager: Lazy<SyncManager>,
    private val platform: Platform,
) : SyncScheduler {
    companion object {
        const val TASK_IDENTIFIER = "coredevices.coreapp.sync-tasks"
    }

    // Main-confined: all mutable state below is only touched from this scope or the main queue.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var networkAvailable = true
    private var inProcessRun: Job? = null
    private var rerunRequested = false

    /** Must be called before the app finishes launching, since BGTaskScheduler requires it. */
    fun install() {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = TASK_IDENTIFIER,
            usingQueue = null,
        ) { task -> handleBackgroundTask(task) }
        startNetworkMonitor()
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ -> runInProcess() }
    }

    override fun schedule() {
        submitBackgroundTask()
        runInProcess()
    }

    private fun runInProcess() {
        scope.launch {
            if (!networkAvailable) return@launch
            if (inProcessRun?.isActive == true) {
                rerunRequested = true
                return@launch
            }
            inProcessRun = launch {
                do {
                    rerunRequested = false
                    platform.runWithBgTask("sync-tasks") { runPendingSafely() }
                } while (rerunRequested)
            }
        }
    }

    private suspend fun runPendingSafely(): Boolean = try {
        withContext(Dispatchers.Default) { syncManager.value.runPending() }
    } catch (e: Exception) {
        logger.e(e) { "Sync run failed" }
        true
    }

    private fun handleBackgroundTask(task: BGTask?) {
        if (task == null) return
        logger.d { "Background sync task started" }
        val job = scope.launch {
            val retryLater = runPendingSafely()
            if (retryLater) submitBackgroundTask()
            task.setTaskCompletedWithSuccess(!retryLater)
        }
        task.expirationHandler = {
            logger.w { "Background sync task expired" }
            job.cancel()
            submitBackgroundTask()
            task.setTaskCompletedWithSuccess(false)
        }
    }

    private fun submitBackgroundTask() {
        val request = BGProcessingTaskRequest(TASK_IDENTIFIER).apply {
            requiresNetworkConnectivity = true
            requiresExternalPower = false
        }
        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        } catch (e: Exception) {
            logger.w(e) { "Failed to submit background sync task" }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun startNetworkMonitor() {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            val satisfied = nw_path_get_status(path) == nw_path_status_satisfied
            val regained = satisfied && !networkAvailable
            networkAvailable = satisfied
            if (regained) {
                logger.d { "Network regained, running pending sync" }
                runInProcess()
            }
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}
