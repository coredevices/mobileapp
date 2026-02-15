package io.rebble.libpebblecommon.calendar

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.cinterop.get
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toNSDate
import platform.CoreGraphics.CGColorGetColorSpace
import platform.CoreGraphics.CGColorGetComponents
import platform.CoreGraphics.CGColorGetNumberOfComponents
import platform.CoreGraphics.CGColorRef
import platform.EventKit.EKAlarm
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventAvailabilityBusy
import platform.EventKit.EKEventAvailabilityFree
import platform.EventKit.EKEventAvailabilityNotSupported
import platform.EventKit.EKEventAvailabilityTentative
import platform.EventKit.EKEventAvailabilityUnavailable
import platform.EventKit.EKEventStatusCanceled
import platform.EventKit.EKEventStatusConfirmed
import platform.EventKit.EKEventStatusNone
import platform.EventKit.EKEventStatusTentative
import platform.EventKit.EKEventStore
import platform.EventKit.EKEventStoreChangedNotification
import platform.EventKit.EKParticipant
import platform.EventKit.EKParticipantStatus.EKParticipantStatusAccepted
import platform.EventKit.EKParticipantStatus.EKParticipantStatusDeclined
import platform.EventKit.EKParticipantStatus.EKParticipantStatusPending
import platform.EventKit.EKParticipantStatus.EKParticipantStatusTentative
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import kotlin.math.abs
import kotlin.time.Instant

class IosSystemCalendar(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
) : SystemCalendar {
    private val logger = Logger.withTag("IosSystemCalendar")
    private var _eventStore: EKEventStore? = null
    private var calendarChangeObserver: Any? = null

    private suspend fun eventStore(): EKEventStore? {
        if (_eventStore != null) {
            return _eventStore
        }
        val es = EKEventStore()
        val deferred = CompletableDeferred<EKEventStore?>()
        val completionHandler: (Boolean, NSError?) -> Unit = { granted: Boolean, error: NSError? ->
            if (granted) {
                _eventStore = es
                deferred.complete(es)
            } else {
                logger.e { "error getting ios calendar access: $error" }
                deferred.complete(null)
            }
        }
        val majorVersion =
            UIDevice.currentDevice.systemVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
        if (majorVersion >= 17) {
            es.requestFullAccessToEventsWithCompletion(completionHandler)
        } else {
            es.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent, completionHandler)
        }
        return deferred.await()
    }

    override suspend fun getCalendars(): List<CalendarEntity> {
        val ek = eventStore() ?: return emptyList()
        val calendars =
            ek.calendarsForEntityType(EKEntityType.EKEntityTypeEvent) as List<EKCalendar>
        return calendars.map {
            CalendarEntity(
                platformId = it.calendarIdentifier,
                name = it.title,
                ownerName = it.source?.title ?: "unknown",
                ownerId = it.source?.sourceIdentifier ?: "unknown",
                color = it.CGColor?.cgColorToInt() ?: 0,
                enabled = true,
            )
        }
    }

    override suspend fun getCalendarEvents(
        calendar: CalendarEntity,
        startDate: Instant,
        endDate: Instant
    ): List<CalendarEvent> {
        val ek = eventStore() ?: return emptyList()
        val cal = ek.calendarWithIdentifier(calendar.platformId)
        if (cal == null) {
            logger.e { "Couldn't find calendar $calendar to query events" }
            return emptyList()
        }
        val predicate = ek.predicateForEventsWithStartDate(
            startDate.toNSDate(),
            endDate.toNSDate(),
            listOf(cal)
        )
        val events = ek.eventsMatchingPredicate(predicate) as List<EKEvent>
        return events.mapNotNull {
            val id = it.eventIdentifier ?: return@mapNotNull null
            val title = it.title ?: return@mapNotNull null
            val description = it.notes ?: ""
            val location = it.location ?: ""
            val start = it.startDate?.toKotlinInstant() ?: return@mapNotNull null
            val end = it.endDate?.toKotlinInstant() ?: return@mapNotNull null
            val attendees = it.attendees as? List<EKParticipant> ?: emptyList()
            val alarms = it.alarms as? List<EKAlarm> ?: emptyList()
            val availability = when (it.availability) {
                EKEventAvailabilityBusy -> CalendarEvent.Availability.Busy
                EKEventAvailabilityFree -> CalendarEvent.Availability.Free
                EKEventAvailabilityNotSupported -> CalendarEvent.Availability.Unavailable
                EKEventAvailabilityTentative -> CalendarEvent.Availability.Tentative
                EKEventAvailabilityUnavailable -> CalendarEvent.Availability.Unavailable
                else -> CalendarEvent.Availability.Unavailable
            }
            val status = when (it.status) {
                EKEventStatusCanceled -> CalendarEvent.Status.Cancelled
                EKEventStatusConfirmed -> CalendarEvent.Status.Confirmed
                EKEventStatusNone -> CalendarEvent.Status.None
                EKEventStatusTentative -> CalendarEvent.Status.Tentative
                else -> CalendarEvent.Status.None
            }
            CalendarEvent(
                id = id,
                calendarId = calendar.platformId,
                title = title,
                description = description,
                location = location,
                startTime = start,
                endTime = end,
                allDay = it.allDay,
                attendees = attendees.map { it.asAttendee() },
                recurs = it.hasRecurrenceRules,
                reminders = alarms.map { it.asReminder() },
                availability = availability,
                status = status,
                baseEventId = id,
            )
        }
    }

    override suspend fun enableSyncForCalendar(calendar: CalendarEntity) {
    }

    override fun registerForCalendarChanges(): Flow<Unit>? {
        val flow = MutableSharedFlow<Unit>(replay = 1) // replay = 1 can be useful if collection starts after an event

        // Remove previous observer if any, to avoid multiple registrations
        calendarChangeObserver?.let {
            NSNotificationCenter.defaultCenter().removeObserver(it)
        }

        calendarChangeObserver = NSNotificationCenter.defaultCenter()
            .addObserverForName(
                name = EKEventStoreChangedNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue()
            ) { notification ->
                logger.d { "EKEventStoreChanged notification received: $notification" }
                libPebbleCoroutineScope.launch {
                    flow.emit(Unit)
                }
            }
        return flow
    }

    override fun hasPermission(): Boolean {
        return EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent) == EKAuthorizationStatusAuthorized
    }
}

fun EKAlarm.asReminder(): EventReminder = EventReminder(
    minutesBefore = abs(relativeOffset.toInt() / 60),
)

fun EKParticipant.asAttendee(): EventAttendee {
    val status = when (participantStatus) {
        EKParticipantStatusAccepted -> EventAttendee.AttendanceStatus.Accepted
        EKParticipantStatusDeclined -> EventAttendee.AttendanceStatus.Declined
        EKParticipantStatusTentative -> EventAttendee.AttendanceStatus.Tentative
        EKParticipantStatusPending -> EventAttendee.AttendanceStatus.Invited
        else -> EventAttendee.AttendanceStatus.None
    }
    
    return EventAttendee(
        name = name,
        email = null,
        role = null, // TODO
        isOrganizer = false, // TODO
        isCurrentUser = isCurrentUser(),
        attendanceStatus = status,
    )
}

fun CGColorRef?.cgColorToInt(): Int {
    if (this == null) return 0 // or handle null case as needed

    val colorSpace = CGColorGetColorSpace(this)
    val numberOfComponents = CGColorGetNumberOfComponents(this).toInt()

    if (colorSpace == null || numberOfComponents < 3) {
        return 0 // or handle the case where it's not an RGB color
    }

    val components = CGColorGetComponents(this)

    val red = (components?.get(0)?.times(255.0f)?.toInt() ?: 0)
    val green = (components?.get(1)?.times(255.0f)?.toInt() ?: 0)
    val blue = (components?.get(2)?.times(255.0f)?.toInt() ?: 0)
    val alpha = if (numberOfComponents > 3) {
        (components?.get(3)?.times(255.0f)?.toInt() ?: 255)
    } else {
        255
    }

    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}