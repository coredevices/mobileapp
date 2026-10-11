package coredevices.util.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.TimeUnit

private val logger = Logger.withTag("AndroidSyncScheduler")
private const val WORK_NAME = "sync_tasks"

/**
 * WorkManager runs [SyncTaskWorker] whenever the network is connected, including after the app is
 * killed or the device reboots. New work is appended behind an in-flight run so nothing enqueued
 * during a run is dropped.
 */
class AndroidSyncScheduler(
    private val context: Context,
    private val syncManager: Lazy<SyncManager>,
) : SyncScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncTaskWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        try {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        } catch (e: Throwable) {
            // Broken OEM builds throw NoSuchMethodError from WorkManager init; try once in-process.
            logger.e(e) { "Failed to schedule sync work, running in-process" }
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    syncManager.value.runPending()
                } catch (e: Exception) {
                    logger.e(e) { "In-process sync run failed" }
                }
            }
        }
    }
}

class SyncTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    override suspend fun doWork(): Result {
        val syncManager: SyncManager = get()
        return try {
            if (syncManager.runPending()) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Sync run failed" }
            Result.retry()
        }
    }
}
