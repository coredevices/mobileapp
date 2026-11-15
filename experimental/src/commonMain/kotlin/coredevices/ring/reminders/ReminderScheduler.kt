package coredevices.ring.reminders

import kotlin.time.Duration
import kotlin.time.Instant

/** Arms and cancels the platform alarms/notifications behind a built-in reminder. */
expect class ReminderScheduler {
    /** No-op for a null or past [time]; throws if the platform refuses the schedule. */
    suspend fun scheduleReminder(itemId: String, message: String, time: Instant?, notifyBefore: Duration?)
    suspend fun scheduleGeofenceReminder(itemId: String, message: String, placeName: String, lat: Double, lng: Double, radiusMeters: Float)
    suspend fun cancelReminder(itemId: String)
    suspend fun cancelExtraNotification(itemId: String)
}
