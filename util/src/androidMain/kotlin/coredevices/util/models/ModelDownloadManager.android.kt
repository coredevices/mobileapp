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
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import com.cactus.CactusLM
import com.cactus.CactusSTT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class ModelDownloadManager(
    private val context: Context
) {
    private val serviceComponentName = ComponentName(context, ModelDownloadService::class.java)
    private val jobScheduler: JobScheduler
        get() = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    private val _downloadStatus: MutableStateFlow<ModelDownloadStatus> = MutableStateFlow(
        // Check if there's an existing job for our service, since jobs survive process restarts
        jobScheduler.allPendingJobs.firstOrNull { it.service == serviceComponentName }?.let {
            val modelSlug = it.extras.getString(ModelDownloadService.KEY_MODEL_SLUG) ?: return@let null
            ModelDownloadStatus.Downloading(modelSlug)
        } ?: ModelDownloadStatus.Idle
    )
    actual val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()

    private fun buildNetworkRequest(allowMetered: Boolean): NetworkRequest {
        val builder = NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
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

    actual fun downloadSTTModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean {
        if (jobScheduler.allPendingJobs.any { it.service == serviceComponentName }) {
            Logger.withTag("ModelDownloadManager").i { "A model download job is already scheduled, not scheduling another." }
            return false
        }
        val info = buildJobInfo(
            modelSlug = modelInfo.slug,
            modelSizeMb = modelInfo.sizeInMB,
            stt = true,
            networkRequest = buildNetworkRequest(allowMetered),
            allowMetered = allowMetered
        )
        return jobScheduler.schedule(info) == JobScheduler.RESULT_SUCCESS
    }

    actual fun downloadLanguageModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean {
        val info = buildJobInfo(
            modelSlug = modelInfo.slug,
            modelSizeMb = modelInfo.sizeInMB,
            stt = false,
            networkRequest = buildNetworkRequest(allowMetered),
            allowMetered = allowMetered
        )
        return jobScheduler.schedule(info) == JobScheduler.RESULT_SUCCESS
    }

    actual fun cancelDownload() {
        jobScheduler.allPendingJobs.forEach {
            if (it.service == serviceComponentName) {
                jobScheduler.cancel(it.id)
            }
        }
    }
}

class ModelDownloadService : JobService(), KoinComponent {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val modelDownloadManager: ModelDownloadManager by inject()
    lateinit var modelSlug: String

    companion object {
        const val KEY_MODEL_SLUG = "model_slug"
        const val KEY_IS_STT = "is_stt"
        private const val CHANNEL_ID = "model_download_channel"
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

        logger.i { "Starting download job for model: $modelSlug, stt = $isStt" }
        createChannel()
        val notification = notifBuilder()
            .setContentTitle("Downloading Model")
            .setContentText("Downloading model: $modelSlug.\nThis could take a few minutes...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(modelSlug.hashCode(), notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setNotification(params, modelSlug.hashCode(), notification, JOB_END_NOTIFICATION_POLICY_DETACH)
        }
        scope.launch {
            try {
                modelDownloadManager.updateDownloadStatus(ModelDownloadStatus.Downloading(modelSlug))
                downloadModel(modelSlug, isStt)
                logger.i { "Completed download job for model: $modelSlug" }
                val completedNotification = notifBuilder()
                    .setContentTitle("Model Downloaded")
                    .setContentText("Successfully downloaded model: $modelSlug")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .build()
                notificationManager.notify(modelSlug.hashCode(), completedNotification)
                modelDownloadManager.updateDownloadStatus(ModelDownloadStatus.Idle)
                jobFinished(params, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "Failed download job for model: $modelSlug" }
                val failedNotification = notifBuilder()
                    .setContentTitle("Model Download Failed")
                    .setContentText("Failed to download model: $modelSlug")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setOngoing(false)
                    .build()
                notificationManager.notify(modelSlug.hashCode(), failedNotification)
                modelDownloadManager.updateDownloadStatus(ModelDownloadStatus.Failed(modelSlug, "Download failed"))
                jobFinished(params, false)
                return@launch
            }
        }
        return true
    }

    private suspend fun downloadModel(modelSlug: String, stt: Boolean) {
        if (stt) {
            CactusSTT().downloadModel(modelSlug)
        } else {
            CactusLM().downloadModel(modelSlug)
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        val reason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params?.stopReason
        } else {
            null
        }
        scope.cancel("Job cancelled")
        logger.i { "Job cancelled for model: $modelSlug, reason = $reason" }
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

        notificationManager.notify(modelSlug.hashCode(), cancelledNotification)
        modelDownloadManager.updateDownloadStatus(ModelDownloadStatus.Idle)
        return false
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