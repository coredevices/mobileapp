package coredevices.ring.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

actual class ReminderScheduler(private val context: Context) {
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val geofencingClient get() = LocationServices.getGeofencingClient(context)

    actual suspend fun scheduleReminder(
        itemId: String,
        message: String,
        time: Instant?,
        notifyBefore: Duration?
    ) {
        val fireTime = time ?: return
        if (fireTime <= Clock.System.now()) return
        scheduleAlarm(itemId, fireTime, isPreNotification = false)
        notifyBefore?.let { lead ->
            val preTime = fireTime - lead
            if (preTime > Clock.System.now()) {
                scheduleAlarm(itemId, preTime, isPreNotification = true)
            }
        }
    }

    actual suspend fun cancelReminder(itemId: String) {
        cancelAlarm(itemId, isPreNotification = false)
        cancelAlarm(itemId, isPreNotification = true)
        cancelGeofence(itemId)
        notificationManager.cancel(itemId, ReminderReceiver.NOTIF_ID_DUE)
        notificationManager.cancel(itemId, ReminderReceiver.NOTIF_ID_PRE)
    }

    actual suspend fun cancelExtraNotification(itemId: String) {
        cancelAlarm(itemId, isPreNotification = true)
        notificationManager.cancel(itemId, ReminderReceiver.NOTIF_ID_PRE)
    }

    private fun scheduleAlarm(itemId: String, triggerTime: Instant, isPreNotification: Boolean) {
        val pendingIntent = alarmPendingIntent(itemId, isPreNotification)
        val info = AlarmManager.AlarmClockInfo(triggerTime.toEpochMilliseconds(), pendingIntent)
        alarmManager.setAlarmClock(info, pendingIntent)
    }

    private fun cancelAlarm(itemId: String, isPreNotification: Boolean) =
        alarmManager.cancel(alarmPendingIntent(itemId, isPreNotification))

    private fun alarmPendingIntent(itemId: String, isPreNotification: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            if (isPreNotification) action = ReminderReceiver.ACTION_PRE_NOTIFICATION
            // Distinct data per (item, kind) so colliding hashCodes never share a PendingIntent.
            data = ReminderReceiver.reminderUri(itemId, if (isPreNotification) "pre" else "due")
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, itemId)
        }
        return PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Fires on each entry into the region until cancelled. Play Services drops geofences on reboot. */
    @SuppressLint("MissingPermission")
    actual suspend fun scheduleGeofenceReminder(
        itemId: String,
        message: String,
        placeName: String,
        lat: Double,
        lng: Double,
        radiusMeters: Float
    ) {
        val geofence = Geofence.Builder()
            .setRequestId(itemId)
            .setCircularRegion(lat, lng, radiusMeters)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        geofencingClient.addGeofences(request, geofencePendingIntent(itemId)).await()
    }

    private suspend fun cancelGeofence(itemId: String) {
        try {
            geofencingClient.removeGeofences(listOf(itemId)).await()
        } catch (e: ApiException) {
            logger.w(e) { "Failed to remove geofence for reminder $itemId" }
        }
    }

    private fun geofencePendingIntent(itemId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = ReminderReceiver.reminderUri(itemId, "geo")
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, itemId)
        }
        // Mutable so Play Services can attach the geofencing event.
        return PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        private val logger = Logger.withTag("ReminderScheduler")
    }
}
