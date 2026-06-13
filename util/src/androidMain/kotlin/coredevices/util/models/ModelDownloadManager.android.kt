package coredevices.util.models

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import coredevices.util.transcription.CactusModelPathProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persists a per-model heartbeat so a download job that is abandoned (process
 * death, hung blocking I/O) can be detected as stale across process restarts.
 */
internal class DownloadHeartbeatStore(context: Context) {
    private val prefs = context.getSharedPreferences("model_download_heartbeat", Context.MODE_PRIVATE)

    fun record(slug: String) {
        prefs.edit().putLong(key(slug), System.currentTimeMillis()).apply()
    }

    fun clear(slug: String) {
        prefs.edit().remove(key(slug)).apply()
    }

    fun lastHeartbeat(slug: String): Long? =
        prefs.getLong(key(slug), -1L).takeIf { it > 0L }

    private fun key(slug: String) = "hb_$slug"
}

actual class ModelDownloadManager(
    private val context: Context
) {
    private val serviceComponentName = ComponentName(context, ModelDownloadService::class.java)
    private val jobScheduler: JobScheduler
        get() = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    internal val heartbeatStore = DownloadHeartbeatStore(context)

    private val _downloadStatus: MutableStateFlow<ModelDownloadStatus> = MutableStateFlow(
        // Jobs survive process restarts; only report Downloading if the job is
        // still alive (recent heartbeat), otherwise it was abandoned.
        pendingServiceJobs().firstOrNull()?.let { job ->
            val slug = job.extras.getString(ModelDownloadService.KEY_MODEL_SLUG) ?: return@let null
            if (isJobStale(slug)) null else ModelDownloadStatus.Downloading(slug)
        } ?: ModelDownloadStatus.Idle
    )
    actual val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()

    init {
        // Evict any abandoned jobs on startup so the guard can't be blocked by them.
        reconcileStaleJobs()
    }

    private fun pendingServiceJobs() =
        jobScheduler.allPendingJobs.filter { it.service == serviceComponentName }

    private fun isJobStale(slug: String): Boolean =
        DownloadJobLiveness.isStale(heartbeatStore.lastHeartbeat(slug), System.currentTimeMillis())

    private fun reconcileStaleJobs() {
        pendingServiceJobs().forEach { job ->
            val slug = job.extras.getString(ModelDownloadService.KEY_MODEL_SLUG)
            if (slug == null || isJobStale(slug)) {
                Logger.withTag("ModelDownloadManager").w {
                    "Cancelling stale/abandoned download job for $slug (id=${job.id})."
                }
                jobScheduler.cancel(job.id)
                slug?.let { heartbeatStore.clear(it) }
            }
        }
    }

    private fun buildNetworkRequest(allowMetered: Boolean): NetworkRequest {
        val builder = NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .removeCapability(NET_CAPABILITY_NOT_VPN)
        if (!allowMetered) {
            builder.addCapability(NET_CAPABILITY_NOT_METERED)
        }
        return builder.build()
    }

    fun updateDownloadStatus(status: ModelDownloadStatus) {
        _downloadStatus.value = status
    }

    private fun slugToJobId(modelSlug: String): Int {
        return "modelJob-$modelSlug".hashCode()
    }

    @RequiresPermission(Manifest.permission.RUN_USER_INITIATED_JOBS)
    private fun buildJobInfo(modelSlug: String, modelSizeMb: Int, stt: Boolean, networkRequest: NetworkRequest, allowMetered: Boolean): JobInfo {
        val builder = JobInfo.Builder(slugToJobId(modelSlug), serviceComponentName)
            .setExtras(
                android.os.PersistableBundle().apply {
                    putString(ModelDownloadService.KEY_MODEL_SLUG, modelSlug)
                    putBoolean(ModelDownloadService.KEY_IS_STT, stt)
                }
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder
                .setRequiredNetwork(networkRequest)
                .setEstimatedNetworkBytes(modelSizeMb * 1024L * 1024L, 1 * 1024L * 1024L)
        } else {
            builder.setRequiredNetworkType(
                if (!allowMetered) {
                    JobInfo.NETWORK_TYPE_UNMETERED
                } else {
                    JobInfo.NETWORK_TYPE_ANY
                }
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setUserInitiated(true)
        }
        return builder.build()
    }

    /**
     * Schedules a download, replacing any existing job for our service. A job
     * for the same model that is still alive is left running; stale jobs and
     * jobs for other models are cancelled first so a hung job can never
     * permanently block a new download.
     */
    private fun scheduleDownload(modelInfo: ModelInfo, stt: Boolean, allowMetered: Boolean): Boolean {
        val existingJobs = pendingServiceJobs()
        val aliveSameModelJob = existingJobs.firstOrNull {
            it.extras.getString(ModelDownloadService.KEY_MODEL_SLUG) == modelInfo.slug &&
                !isJobStale(modelInfo.slug)
        }
        if (aliveSameModelJob != null && existingJobs.size == 1) {
            Logger.withTag("ModelDownloadManager").i {
                "Download already in progress for ${modelInfo.slug}, skipping."
            }
            return true
        }
        existingJobs.forEach { job ->
            val slug = job.extras.getString(ModelDownloadService.KEY_MODEL_SLUG)
            Logger.withTag("ModelDownloadManager").w {
                "Cancelling existing download job for $slug to download ${modelInfo.slug}."
            }
            jobScheduler.cancel(job.id)
            slug?.let { heartbeatStore.clear(it) }
        }
        heartbeatStore.clear(modelInfo.slug)
        val info = buildJobInfo(
            modelSlug = modelInfo.slug,
            modelSizeMb = modelInfo.sizeInMB,
            stt = stt,
            networkRequest = buildNetworkRequest(allowMetered),
            allowMetered = allowMetered
        )
        return jobScheduler.schedule(info) == JobScheduler.RESULT_SUCCESS
    }

    actual fun downloadSTTModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean =
        scheduleDownload(modelInfo, stt = true, allowMetered = allowMetered)

    actual fun downloadLanguageModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean =
        scheduleDownload(modelInfo, stt = false, allowMetered = allowMetered)

    actual fun cancelDownload() {
        pendingServiceJobs().forEach { job ->
            val slug = job.extras.getString(ModelDownloadService.KEY_MODEL_SLUG)
            jobScheduler.cancel(job.id)
            slug?.let { heartbeatStore.clear(it) }
        }
    }
}

class ModelDownloadService : JobService(), KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelDownloadManager: ModelDownloadManager by inject()
    private val heartbeatStore: DownloadHeartbeatStore get() = modelDownloadManager.heartbeatStore
    private var currentJob: Job? = null
    private val stoppedBySystem = AtomicBoolean(false)
    lateinit var modelSlug: String

    companion object {
        const val KEY_MODEL_SLUG = "model_slug"
        const val KEY_IS_STT = "is_stt"
        private const val CHANNEL_ID = "model_download_channel"
        // Generous upper bound so a wedged download can't run forever.
        private const val DOWNLOAD_TIMEOUT_MILLIS = 45L * 60L * 1000L
        private val logger = Logger.withTag("ModelDownloadService")
    }

    private fun notifBuilder() = NotificationCompat.Builder(
        applicationContext,
        CHANNEL_ID
    ).setLocalOnly(true)

    override fun onStartJob(params: JobParameters?): Boolean {
        val modelSlug = params?.extras?.getString(KEY_MODEL_SLUG) ?: return false
        this.modelSlug = modelSlug
        if (!params.extras.containsKey(KEY_IS_STT)) return false
        val isStt = params.extras.getBoolean(KEY_IS_STT)
        stoppedBySystem.set(false)

        logger.i { "Starting download job for model: $modelSlug, stt = $isStt" }
        createChannel()
        val notification = notifBuilder()
            .setContentTitle("Downloading Model")
            .setContentText("Downloading model: $modelSlug.\nThis could take a few minutes...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setGroup("model_downloads")
            .build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(modelSlug.hashCode(), notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setNotification(params, modelSlug.hashCode(), notification, JOB_END_NOTIFICATION_POLICY_DETACH)
        }
        currentJob = scope.launch {
            val heartbeat = launch {
                while (isActive) {
                    heartbeatStore.record(modelSlug)
                    delay(DownloadJobLiveness.DEFAULT_HEARTBEAT_INTERVAL_MILLIS)
                }
            }
            try {
                heartbeatStore.record(modelSlug)
                modelDownloadManager.updateDownloadStatus(ModelDownloadStatus.Downloading(modelSlug))
                withTimeout(DOWNLOAD_TIMEOUT_MILLIS) {
                    downloadModel(modelSlug, isStt)
                }
                logger.i { "Completed download job for model: $modelSlug" }
                notificationManager.notify(
                    modelSlug.hashCode(),
                    notifBuilder()
                        .setContentTitle("Model Downloaded")
                        .setContentText("Successfully downloaded model: $modelSlug")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setOngoing(false)
                        .build()
                )
                modelDownloadManager.updateDownloadStatus(ModelDownloadStatus.Idle)
            } catch (e: TimeoutCancellationException) {
                logger.e(e) { "Timed out downloading model: $modelSlug" }
                notificationManager.notify(
                    modelSlug.hashCode(),
                    notifBuilder()
                        .setContentTitle("Model Download Failed")
                        .setContentText("Download timed out: $modelSlug")
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setOngoing(false)
                        .build()
                )
                modelDownloadManager.updateDownloadStatus(
                    ModelDownloadStatus.Failed(modelSlug, "Download timed out")
                )
            } catch (e: CancellationException) {
                // Stopped by the system via onStopJob; it owns the reschedule decision.
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "Failed download job for model: $modelSlug" }
                notificationManager.notify(
                    modelSlug.hashCode(),
                    notifBuilder()
                        .setContentTitle("Model Download Failed")
                        .setContentText("Failed to download model: $modelSlug")
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setOngoing(false)
                        .build()
                )
                modelDownloadManager.updateDownloadStatus(
                    ModelDownloadStatus.Failed(modelSlug, "Download failed")
                )
            } finally {
                heartbeat.cancel()
                heartbeatStore.clear(modelSlug)
                // If the system stopped us, it reschedules based on onStopJob's
                // return value; calling jobFinished here would be a no-op anyway.
                if (!stoppedBySystem.get()) {
                    jobFinished(params, false)
                }
            }
        }
        return true
    }

    private suspend fun downloadModel(modelSlug: String, stt: Boolean) {
        val modelProvider: CactusModelPathProvider by inject()
        if (stt) {
            modelProvider.getSTTModelPath()
        } else {
            modelProvider.getLMModelPath()
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        stoppedBySystem.set(true)
        val reason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params?.stopReason
        } else {
            null
        }
        currentJob?.cancel(CancellationException("Job stopped by system, reason=$reason"))
        logger.i { "Job stopped for model: $modelSlug, reason = $reason" }
        if (::modelSlug.isInitialized) {
            heartbeatStore.clear(modelSlug)
        }
        val title = when (reason) {
            JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "Model Download Paused"
            JobParameters.STOP_REASON_TIMEOUT -> "Model Download Error"
            else -> "Model Download Cancelled"
        }
        val text = when (reason) {
            JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "Download paused due to network conditions."
            JobParameters.STOP_REASON_TIMEOUT -> "Timed out trying to download model: $modelSlug."
            else -> "Cancelled download."
        }
        val icon = when (reason) {
            JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY -> android.R.drawable.stat_sys_warning
            JobParameters.STOP_REASON_TIMEOUT -> android.R.drawable.stat_notify_error
            else -> android.R.drawable.stat_sys_download_done
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val cancelledNotification = notifBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setOngoing(false)
            .build()

        if (::modelSlug.isInitialized) {
            notificationManager.notify(modelSlug.hashCode(), cancelledNotification)
        }
        modelDownloadManager.updateDownloadStatus(ModelDownloadStatus.Idle)
        // Let the system retry transient stops; don't retry app/user-driven ones.
        return shouldReschedule(reason)
    }

    private fun shouldReschedule(stopReason: Int?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || stopReason == null) return true
        return when (stopReason) {
            JobParameters.STOP_REASON_CANCELLED_BY_APP,
            JobParameters.STOP_REASON_USER -> false
            else -> true
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Speech and language model download progress"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

}