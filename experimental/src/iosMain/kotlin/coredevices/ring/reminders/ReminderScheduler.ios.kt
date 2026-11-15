package coredevices.ring.reminders

import coredevices.util.setWatchNotificationActionHandler
import coredevices.util.sha256
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.buildTimelineReminder
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.toNSDate
import objcnames.classes.CLRegion
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNLocationNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

actual class ReminderScheduler(
    private val libPebble: LibPebble,
    private val completer: ReminderCompleter,
) {
    private val notificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    actual suspend fun scheduleReminder(
        itemId: String,
        message: String,
        time: Instant?,
        notifyBefore: Duration?
    ) {
        val fireTime = time ?: return
        if (fireTime <= Clock.System.now()) return
        scheduleNotification(itemId, message, fireTime, isPreNotification = false)
        notifyBefore?.let { lead ->
            val preTime = fireTime - lead
            if (preTime > Clock.System.now()) {
                scheduleNotification(itemId, message, preTime, isPreNotification = true)
            }
        }
    }

    /** Fires on each entry into the region until cancelled. Never fires without location access. */
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun scheduleGeofenceReminder(
        itemId: String,
        message: String,
        placeName: String,
        lat: Double,
        lng: Double,
        radiusMeters: Float
    ) {
        val identifier = notificationId(itemId)
        val region = CLCircularRegion(
            center = CLLocationCoordinate2DMake(lat, lng),
            radius = radiusMeters.toDouble(),
            identifier = identifier,
        ).apply {
            notifyOnEntry = true
            notifyOnExit = false
        }
        // UserNotifications only forward-declares CLRegion, so cinterop sees a different type.
        @Suppress("UNCHECKED_CAST_TO_FORWARD_DECLARATION")
        val trigger = UNLocationNotificationTrigger.triggerWithRegion(region as CLRegion, repeats = true)
        addNotification(itemId, identifier, title = "Reminder: $placeName", message, trigger)
    }

    actual suspend fun cancelReminder(itemId: String) {
        removeScheduled(listOf(notificationId(itemId), preNotificationId(itemId)))
    }

    actual suspend fun cancelExtraNotification(itemId: String) {
        removeScheduled(listOf(preNotificationId(itemId)))
    }

    fun registerWatchActions() {
        libPebble.setWatchNotificationActionHandler(WATCH_ACTION_DONE) { itemId ->
            completer.markDone(requireNotNull(itemId))
        }
    }

    /** Registers the reminder notification category carrying the "Done" action, once.
     *  Merges with any already-registered categories so we don't clobber other notification
     *  categories (e.g. the Index agent-complete actions). */
    suspend fun ensureCategoryRegistered() {
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

    private suspend fun scheduleNotification(
        itemId: String,
        message: String,
        triggerTime: Instant,
        isPreNotification: Boolean,
    ) {
        val identifier = if (isPreNotification) preNotificationId(itemId) else notificationId(itemId)
        val components = NSCalendar.currentCalendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay
                    or NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
            fromDate = triggerTime.toNSDate()
        )
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        addNotification(
            itemId,
            identifier,
            title = if (isPreNotification) "Upcoming reminder" else "Reminder",
            message,
            trigger,
        )
        libPebble.insertOrReplace(
            buildTimelineReminder(parentId = Uuid.NIL, timestamp = triggerTime) {
                itemID = watchReminderId(identifier)
                attributes {
                    title { message }
                    if (isPreNotification) body { "Upcoming" }
                    tinyIcon { TimelineIcon.NotificationReminder }
                }
                actions {
                    action(TimelineItem.Action.Type.Generic, internalType = WATCH_ACTION_DONE) {
                        attributes { title { "Done" } }
                        internalData = itemId
                    }
                    action(TimelineItem.Action.Type.Dismiss) {
                        attributes { title { "Dismiss" } }
                    }
                }
            }
        )
    }

    private suspend fun addNotification(
        itemId: String,
        identifier: String,
        title: String,
        message: String,
        trigger: UNNotificationTrigger,
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(message)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(mapOf<Any?, Any?>(ReminderDeepLinkResolver.USERINFO_REMINDER_ID to itemId))
            setCategoryIdentifier(REMINDER_CATEGORY_ID)
        }
        val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)
        val error = suspendCoroutine { continuation ->
            notificationCenter.addNotificationRequest(request) { error -> continuation.resume(error) }
        }
        check(error == null) { "Failed to schedule reminder notification: ${error?.localizedDescription}" }
    }

    private suspend fun removeScheduled(identifiers: List<String>) {
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)
        identifiers.forEach { libPebble.deleteReminder(watchReminderId(it)) }
    }

    companion object {
        /** Action id for the "Done" button; matched in the notification-response handler. */
        const val REMINDER_ACTION_DONE = "ring-reminder-done"

        private const val REMINDER_CATEGORY_ID = "ring-reminder-actions"
        private const val WATCH_ACTION_DONE = "ring_reminder_done"

        private fun notificationId(itemId: String) = "ring-reminder-$itemId"
        private fun preNotificationId(itemId: String) = "ring-reminder-pre-$itemId"
        private fun watchReminderId(identifier: String) =
            Uuid.fromByteArray(sha256(identifier.encodeToByteArray()).copyOf(Uuid.SIZE_BYTES))
    }
}
