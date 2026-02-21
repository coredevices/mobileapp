package coredevices.coreapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val commonAppDelegate: CommonAppDelegate = get()
    private val logger = Logger.withTag("SyncWorker")

    override suspend fun doWork(): Result = try {
        coroutineScope {
            commonAppDelegate.doBackgroundSync(this, force = false)
            Result.success()
        }
    } catch (e: CancellationException) {
        logger.i { "SyncWorker cancelled" }
        throw e
    } catch (e: Exception) {
        logger.e(e) { "SyncWorker failed" }
        Result.failure()
    }
}
