package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IOSBuiltInReminderIntegration : BuiltInReminderIntegration() {
    private val notificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    private suspend fun requestAuthorization(): Boolean = suspendCoroutine { continuation ->
        notificationCenter.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, error ->
            if (error != null) {
                logger.e { "Error requesting notification permission: $error" }
            }
            continuation.resume(granted)
        }
    }

    override suspend fun schedulingBlocker(isGeofence: Boolean): String? = when {
        !requestAuthorization() -> "Notification permission not granted"
        isGeofence && !hasLocationAccess() -> "Location permission not granted"
        else -> null
    }

    // Location notification triggers are delivered by the system, so When In Use is enough.
    private fun hasLocationAccess(): Boolean {
        val status = CLLocationManager().authorizationStatus
        return status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse
    }

    override suspend fun purgeLegacySchedules() {
        val pending = pendingIdentifiers().filter { isLegacyReminderId(it) }
        if (pending.isNotEmpty()) notificationCenter.removePendingNotificationRequestsWithIdentifiers(pending)
        val delivered = deliveredIdentifiers().filter { isLegacyReminderId(it) }
        if (delivered.isNotEmpty()) notificationCenter.removeDeliveredNotificationsWithIdentifiers(delivered)
        if (pending.isNotEmpty() || delivered.isNotEmpty()) {
            logger.i { "Purged ${pending.size + delivered.size} legacy reminder schedule(s)" }
        }
    }

    private suspend fun pendingIdentifiers(): List<String> = suspendCoroutine { cont ->
        notificationCenter.getPendingNotificationRequestsWithCompletionHandler { requests ->
            cont.resume(requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier })
        }
    }

    private suspend fun deliveredIdentifiers(): List<String> = suspendCoroutine { cont ->
        notificationCenter.getDeliveredNotificationsWithCompletionHandler { notifications ->
            cont.resume(notifications.orEmpty().mapNotNull { (it as? UNNotification)?.request?.identifier })
        }
    }

    companion object {
        private val logger = Logger.withTag("IOSBuiltInReminderIntegration")

        /** A reminder notification whose id ends in the old device-local int (all digits) rather
         *  than a global feed-item id. A global id that happened to be all digits would be a false
         *  positive, but the reconciler re-arms it on its next pass, so purging it self-heals. */
        private fun isLegacyReminderId(id: String): Boolean {
            val body = id.removePrefix("ring-reminder-")
            if (body.length == id.length) return false
            val suffix = body.removePrefix("pre-")
            return suffix.isNotEmpty() && suffix.all { it in '0'..'9' }
        }
    }
}
