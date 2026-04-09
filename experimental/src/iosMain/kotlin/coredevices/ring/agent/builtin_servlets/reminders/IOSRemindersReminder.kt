package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.RingDatabase
import kotlinx.datetime.toNSDate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.EventKit.EKAlarm
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
import platform.Foundation.NSDate
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Instant

class IOSRemindersReminder(
    override val time: Instant?,
    override val message: String
) : ListAssignableReminder, KoinComponent {
    private val db: RingDatabase by inject()

    private var _reminderId: Int? = null
    val reminderId: Int? get() = _reminderId
    private var _listTitle: String? = null
    override val listTitle: String?
        get() = _listTitle

    private constructor(time: Instant?, message: String, reminderId: Int) : this(time, message) {
        _reminderId = reminderId
    }

    private suspend fun requestAccess(eventStore: EKEventStore): Boolean {
        return suspendCoroutine { continuation ->
            eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeReminder) { granted, error ->
                if (error != null) {
                    logger.e { "Error requesting reminder permissions: $error" }
                }
                continuation.resume(granted)
            }
        }
    }

    private fun scheduleForCalendar(eventStore: EKEventStore, calendar: EKCalendar, title: String, date: NSDate?): String {
        val ekReminder = EKReminder.reminderWithEventStore(eventStore)
        ekReminder.title = message
        ekReminder.calendar = calendar

        date?.let {
            ekReminder.dueDateComponents = NSCalendar.currentCalendar.components(
                (NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay
                        or NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond),
                fromDate = date
            )
            ekReminder.addAlarm(EKAlarm.alarmWithAbsoluteDate(date))
        }

        check(eventStore.saveReminder(ekReminder, commit = true, error = null)) {
            "Failed to save reminder to Reminders app"
        }
        return ekReminder.calendarItemIdentifier
    }

    override suspend fun schedule(): String {
        val eventStore = EKEventStore()
        check(requestAccess(eventStore)) { "Reminder permission not granted" }
        val calendar = eventStore.defaultCalendarForNewReminders()
            ?: throw Exception("No default calendar found for reminders")
        return scheduleForCalendar(eventStore, calendar, message, time?.toNSDate())
    }

    override suspend fun cancel() {

    }

    override suspend fun scheduleToList(listName: String): String {
        val eventStore = EKEventStore()
        check(requestAccess(eventStore)) { "Reminder permission not granted" }
        @Suppress("UNCHECKED_CAST")
        val calendar = (eventStore.calendarsForEntityType(EKEntityType.EKEntityTypeReminder) as List<EKCalendar>)
            .firstOrNull { it.title.contains(listName, ignoreCase = true) }
            ?: throw Exception("No reminder list found with name $listName")
        _listTitle = calendar.title
        return scheduleForCalendar(eventStore, calendar, message, time?.toNSDate())
    }

    companion object {
        private const val REMINDER_TAG_PREFIX = "coredevices-reminder-"
        private val logger = Logger.withTag("IOSRemindersReminder")

        fun fromData(data: LocalReminderData): IOSRemindersReminder {
            return IOSRemindersReminder(data.time, data.message, data.id)
        }
    }
}
