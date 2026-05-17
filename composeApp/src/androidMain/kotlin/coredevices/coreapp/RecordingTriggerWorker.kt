package coredevices.coreapp

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import coredevices.util.R

class RecordingTriggerWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        setForeground(createForegroundInfo())
        context.startForegroundService(
            Intent(context, PebbleService::class.java).apply { this.action = action }
        )
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                PebbleService.NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_MIN
            ).setName(PebbleService.NOTIFICATION_CHANNEL_NAME).build()
        )
        val notification = NotificationCompat.Builder(context, PebbleService.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Starting recording…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        // No service type here — the worker just needs to promote the app to a foreground
        // state so it can start PebbleService. PebbleService handles the microphone type itself.
        return ForegroundInfo(99, notification)
    }

    companion object {
        const val KEY_ACTION = "action"
    }
}
