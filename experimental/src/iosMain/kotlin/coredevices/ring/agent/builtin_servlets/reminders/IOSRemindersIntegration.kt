package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import kotlinx.datetime.toNSDate
import platform.EventKit.EKAlarm
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEventStore
import platform.EventKit.EKReminder
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSError
import platform.UIKit.UIDevice
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration
import kotlin.time.Instant

/** Schedules reminders into the native iOS Reminders app (EventKit). */
class IOSRemindersIntegration : ReminderIntegration {

    private suspend fun requestAccess(eventStore: EKEventStore): Boolean {
        return suspendCoroutine { continuation ->
            val majorVersion =
                UIDevice.currentDevice.systemVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
            val completionHandler = { granted: Boolean, error: NSError? ->
                if (error != null) {
                    logger.e { "Error requesting reminder permissions: $error" }
                }
                continuation.resume(granted)
            }
            if (majorVersion >= 17) {
                eventStore.requestFullAccessToRemindersWithCompletion(completionHandler)
            } else {
                eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeReminder, completionHandler)
            }
        }
    }

    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String {
        val eventStore = EKEventStore()
        check(requestAccess(eventStore)) { "Reminder permission not granted" }
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeReminder)
        if (status != EKAuthorizationStatusAuthorized) {
            throw Exception("Reminders full access not granted. Please enable full access in Settings → Privacy & Security → Reminders → Pebble.")
        }
        val calendar = if (listId != null) {
            eventStore.calendarWithIdentifier(listId)
                ?: throw Exception("Reminders list not found")
        } else {
            eventStore.defaultCalendarForNewReminders()
                ?: throw Exception("No default calendar found for reminders")
        }

        val ekReminder = EKReminder.reminderWithEventStore(eventStore)
        ekReminder.title = title
        ekReminder.calendar = calendar

        deadline?.toNSDate()?.let { date ->
            ekReminder.dueDateComponents = NSCalendar.currentCalendar.components(
                (NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay
                        or NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond),
                fromDate = date
            )
            ekReminder.addAlarm(EKAlarm.alarmWithAbsoluteDate(date))
            // Extra early heads-up alarm, expressed as a negative offset from the due date.
            notifyBefore?.let { lead ->
                ekReminder.addAlarm(EKAlarm.alarmWithRelativeOffset(-lead.inWholeSeconds.toDouble()))
            }
        }

        check(eventStore.saveReminder(ekReminder, commit = true, error = null)) {
            "Failed to save reminder to Reminders app"
        }
        return ekReminder.calendarItemIdentifier
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> {
        val eventStore = EKEventStore()
        check(requestAccess(eventStore)) { "Reminder permission not granted" }
        @Suppress("UNCHECKED_CAST")
        return (eventStore.calendarsForEntityType(EKEntityType.EKEntityTypeReminder) as List<EKCalendar>)
            .filter { it.title.contains(listName, ignoreCase = true) }
            .map { ReminderListEntry(id = it.calendarIdentifier, title = it.title) }
    }

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = requestAccess(EKEventStore())
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean =
        EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeReminder) == EKAuthorizationStatusAuthorized

    companion object {
        private val logger = Logger.withTag("IOSRemindersIntegration")
    }
}
