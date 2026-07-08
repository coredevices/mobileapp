package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.reminders.ReminderDeepLinkResolver
import kotlinx.datetime.toNSDate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * iOS counterpart to [AndroidBuiltInReminderIntegration]: schedules local notifications via
 * [UNUserNotificationCenter] and records each reminder in [LocalReminderData] so it shows up
 * in the in-app reminders list and can be cancelled.
 */
class IOSBuiltInReminderIntegration : BuiltInReminderIntegration, KoinComponent {
    private val db: RingDatabase by inject()
    private val feedItems: BuiltInReminderFeedItems by inject()
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

    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String {
        require(deadline == null || deadline > Clock.System.now()) { "Time must be in the future" }
        check(requestAuthorization()) { "Notification permission not granted" }

        val id = db.localReminderDao().insertReminder(
            LocalReminderData(
                0,
                deadline,
                title,
                recordingId = source?.recordingFirestoreId,
                notifyBeforeMillis = notifyBefore?.inWholeMilliseconds)
        ).toInt()

        deadline?.let { scheduledTime ->
            scheduleNotification(id, title, scheduledTime, notificationId(id), notificationTitle = "Reminder")
            // The early heads-up notification is only scheduled when its trigger time is still ahead.
            notifyBefore?.let { lead ->
                val preTime = scheduledTime - lead
                if (preTime > Clock.System.now()) {
                    scheduleNotification(id, title, preTime, preNotificationId(id), notificationTitle = "Upcoming reminder")
                }
            }
        }
        try {
            feedItems.createFeedItem(id, title, deadline, listId, notifyBefore, source)
        } catch (e: Exception) {
            // If the feed item creation fails, cancel the scheduled notifications and delete the reminder.
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(notificationId(id), preNotificationId(id)))
            notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(notificationId(id), preNotificationId(id)))
            db.localReminderDao().deleteReminder(id)
            throw e
        }
        return id.toString()
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        feedItems.searchForList(listName)

    private suspend fun scheduleNotification(
        reminderId: Int,
        message: String,
        triggerTime: Instant,
        identifier: String,
        notificationTitle: String,
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(notificationTitle)
            setBody(message)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(mapOf<Any?, Any?>(ReminderDeepLinkResolver.USERINFO_REMINDER_ID to reminderId.toString()))
        }
        val components = NSCalendar.currentCalendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay
                    or NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
            fromDate = triggerTime.toNSDate()
        )
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)
        val error = suspendCoroutine { continuation ->
            notificationCenter.addNotificationRequest(request) { error -> continuation.resume(error) }
        }
        check(error == null) { "Failed to schedule reminder notification: ${error?.localizedDescription}" }
    }

    override suspend fun cancelReminder(reminderId: Int) {
        db.localReminderDao().getReminder(reminderId) ?: return
        val identifiers = listOf(notificationId(reminderId), preNotificationId(reminderId))
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)
        db.localReminderDao().deleteReminder(reminderId)
    }

    override suspend fun cancelExtraNotification(reminderId: Int) {
        db.localReminderDao().getReminder(reminderId) ?: return
        val identifiers = listOf(preNotificationId(reminderId))
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)
        db.localReminderDao().clearNotifyBefore(reminderId)
    }

    // Built-in reminders need no account; notification permission is requested when scheduling.
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = true
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = true

    companion object {
        private val logger = Logger.withTag("IOSBuiltInReminderIntegration")

        private fun notificationId(reminderId: Int) = "ring-reminder-$reminderId"
        private fun preNotificationId(reminderId: Int) = "ring-reminder-pre-$reminderId"
    }
}
