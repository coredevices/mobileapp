package coredevices.coreapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val commonAppDelegate: CommonAppDelegate = get()

    override suspend fun doWork(): Result {
        commonAppDelegate.doBackgroundSync(force = false)
        return Result.success()
    }
}