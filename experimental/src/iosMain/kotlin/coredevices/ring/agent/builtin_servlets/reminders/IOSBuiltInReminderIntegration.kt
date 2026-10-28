package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.reminders.ReminderDeepLinkResolver
import kotlinx.coroutines.flow.first
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
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
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
    private val listRepository: ListRepository by inject()
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

        val itemId = feedItems.newItemId()
        deadline?.let { scheduledTime ->
            scheduleNotification(itemId, title, scheduledTime, notificationId(itemId), notificationTitle = "Reminder")
            // The early heads-up notification is only scheduled when its trigger time is still ahead.
            notifyBefore?.let { lead ->
                val preTime = scheduledTime - lead
                if (preTime > Clock.System.now()) {
                    scheduleNotification(itemId, title, preTime, preNotificationId(itemId), notificationTitle = "Upcoming reminder")
                }
            }
        }
        db.localReminderDao().upsert(LocalReminderData(itemId, deadline, title, notifyBefore?.inWholeMilliseconds))

        try {
            feedItems.createFeedItem(itemId, title, deadline, listId, notifyBefore, source)
        } catch (e: Exception) {
            // If the feed item creation fails, cancel the scheduled notifications and delete the reminder.
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(notificationId(itemId), preNotificationId(itemId)))
            notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(notificationId(itemId), preNotificationId(itemId)))
            db.localReminderDao().deleteReminder(itemId)
            throw e
        }
        return itemId
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        feedItems.searchForList(listName)

    private suspend fun scheduleNotification(
        itemId: String,
        message: String,
        triggerTime: Instant,
        identifier: String,
        notificationTitle: String,
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(notificationTitle)
            setBody(message)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(mapOf<Any?, Any?>(ReminderDeepLinkResolver.USERINFO_REMINDER_ID to itemId))
            setCategoryIdentifier(REMINDER_CATEGORY_ID)
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

    override suspend fun cancelReminder(itemId: String) {
        val identifiers = listOf(notificationId(itemId), preNotificationId(itemId))
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)
        db.localReminderDao().deleteReminder(itemId)
    }

    override suspend fun syncReminder(itemId: String, message: String, time: Instant?, notifyBefore: Duration?) {
        if (!requestAuthorization()) {
            logger.w { "Skipping reminder sync: notification permission not granted" }
            return
        }
        val identifiers = listOf(notificationId(itemId), preNotificationId(itemId))
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)

        // Schedule before recording the row, so a scheduling failure leaves the ledger stale and
        // the next reconcile retries rather than believing this reminder is armed.
        if (time != null && time > Clock.System.now()) {
            scheduleNotification(itemId, message, time, notificationId(itemId), notificationTitle = "Reminder")
            notifyBefore?.let { lead ->
                val preTime = time - lead
                if (preTime > Clock.System.now()) {
                    scheduleNotification(itemId, message, preTime, preNotificationId(itemId), notificationTitle = "Upcoming reminder")
                }
            }
        }
        db.localReminderDao().upsert(LocalReminderData(itemId, time, message, notifyBefore?.inWholeMilliseconds))
    }

    override suspend fun cancelExtraNotification(itemId: String) {
        val identifiers = listOf(preNotificationId(itemId))
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)
        db.localReminderDao().clearNotifyBefore(itemId)
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

    override suspend fun getAllLists(): List<ReminderListEntry> {
        val lists = listRepository.getAllFlow().first()
        return lists.map { ReminderListEntry(it.firestoreId, it.title) }
    }

    // Built-in reminders need no account; notification permission is requested when scheduling.
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = true
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = true

    companion object {
        private val logger = Logger.withTag("IOSBuiltInReminderIntegration")

        /** Notification category id whose actions include the "Done" button (MOB-8439). */
        const val REMINDER_CATEGORY_ID = "ring-reminder-actions"

        /** Action id for the "Done" button; matched in the notification-response handler. */
        const val REMINDER_ACTION_DONE = "ring-reminder-done"

        private fun notificationId(itemId: String) = "ring-reminder-$itemId"
        private fun preNotificationId(itemId: String) = "ring-reminder-pre-$itemId"

        /** A reminder notification whose id ends in the old device-local int (all digits) rather
         *  than a global feed-item id. A global id that happened to be all digits would be a false
         *  positive, but the reconciler re-arms it on its next pass, so purging it self-heals. */
        private fun isLegacyReminderId(id: String): Boolean {
            val body = id.removePrefix("ring-reminder-")
            if (body.length == id.length) return false
            val suffix = body.removePrefix("pre-")
            return suffix.isNotEmpty() && suffix.all { it in '0'..'9' }
        }

        /** Registers the reminder notification category carrying the "Done" action (MOB-8439), once.
         *  Merges with any already-registered categories so we don't clobber other notification
         *  categories (e.g. the Index agent-complete actions). */
        suspend fun ensureCategoryRegistered() {
            val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
            val existing: Set<*> = suspendCoroutine { continuation ->
                notificationCenter.getNotificationCategoriesWithCompletionHandler { categories ->
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(categories as Set<*>? ?: emptySet<Any>())
                }
            }
            val alreadyRegistered = existing.any {
                (it as? UNNotificationCategory)?.identifier == REMINDER_CATEGORY_ID
            }
            if (alreadyRegistered) return
            val doneAction = UNNotificationAction.actionWithIdentifier(
                identifier = REMINDER_ACTION_DONE,
                title = "Done",
                options = 0u, // background action: marks done without bringing the app to the foreground
            )
            val category = UNNotificationCategory.categoryWithIdentifier(
                identifier = REMINDER_CATEGORY_ID,
                actions = listOf(doneAction),
                intentIdentifiers = emptyList<String>(),
                options = 0u,
            )
            notificationCenter.setNotificationCategories(existing + setOf(category))
        }
    }
}
