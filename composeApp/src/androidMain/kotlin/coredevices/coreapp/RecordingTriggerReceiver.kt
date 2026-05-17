package coredevices.coreapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

class RecordingTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != PebbleService.ACTION_START_RECORDING && action != PebbleService.ACTION_STOP_RECORDING) return

        val serviceIntent = Intent(context, PebbleService::class.java).apply { this.action = action }
        try {
            context.startForegroundService(serviceIntent)
        } catch (e: IllegalStateException) {
            // On Android 12+, startForegroundService is blocked from background when no
            // foreground service is already running. Use an expedited WorkManager job instead:
            // the worker calls setForeground() to promote itself, then starts PebbleService.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "recording_trigger",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RecordingTriggerWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(workDataOf(RecordingTriggerWorker.KEY_ACTION to action))
                    .build()
            )
        }
    }
}
