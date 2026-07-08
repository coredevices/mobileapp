package io.rebble.libpebblecommon.calendar

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.CALENDAR_APP_UUID
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.Calendar
import io.rebble.libpebblecommon.connection.endpointmanager.blobdb.TimeProvider
import io.rebble.libpebblecommon.database.dao.CalendarDao
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
import io.rebble.libpebblecommon.database.dao.TimelineReminderRealDao
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.TimelineReminder
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class PhoneCalendarSyncer(
    private val timelinePinDao: TimelinePinRealDao,
    private val calendarDao: CalendarDao,
    private val timeProvider: TimeProvider,
    private val systemCalendar: SystemCalendar,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val timelineReminderDao: TimelineReminderRealDao,
    private val watchConfig: WatchConfigFlow,
) : Calendar {
    private val logger = Logger.withTag("PhoneCalendarSyncer")
    private val syncTrigger = MutableSharedFlow<Unit>()
    private var changesFlow: Flow<Unit>? = null
    private val changesJobMutex = Mutex()

    fun init() {
        logger.v { "init()" }
        libPebbleCoroutineScope.launch {
            libPebbleCoroutineScope.launch {
                syncTrigger.conflate().collect {
                    syncDeviceCalendarsToDb()
                }
            }
            // Make sure the above is collecting already
            delay(1.seconds)
            requestSync()
            tryStartListeningForChanges()
            libPebbleCoroutineScope.launch {
                var previousPinsEnabled = watchConfig.value.calendarPins
                var previousRemindersEnabled = watchConfig.value.calendarReminders
                var previousShowDeclinedEvents = watchConfig.value.calendarShowDeclinedEvents
                watchConfig.flow.collect {
                    if (it.watchConfig.calendarPins != previousPinsEnabled ||
                        it.watchConfig.calendarShowDeclinedEvents != previousShowDeclinedEvents ||
                        it.watchConfig.calendarReminders != previousRemindersEnabled
                    ) {
                        requestSync()
                        previousPinsEnabled = it.watchConfig.calendarPins
                        previousRemindersEnabled = it.watchConfig.calendarReminders
                        previousShowDeclinedEvents = it.watchConfig.calendarShowDeclinedEvents
                    }
                }
            }
        }
    }

    private suspend fun tryStartListeningForChanges() = changesJobMutex.withLock {
        if (changesFlow != null) {
            return@withLock
        }
        changesFlow = systemCalendar.registerForCalendarChanges()?.sample(5.seconds)
        changesFlow?.let {
            libPebbleCoroutineScope.launch {
                it.collect {
                    requestSync()
                }
            }
        }
    }

    fun handlePermissionsGranted() {
        libPebbleCoroutineScope.launch {
            tryStartListeningForChanges()
            requestSync()
        }
    }

    private suspend fun requestSync() {
        syncTrigger.emit(Unit)
    }

    private suspend fun syncDeviceCalendarsToDb() {
        val pinsEnabled = watchConfig.value.calendarPins
        val remindersEnabled = watchConfig.value.calendarReminders
        val showDeclinedEvents = watchConfig.value.calendarShowDeclinedEvents
        logger.d("syncDeviceCalendarsToDb remindersEnabled=$remindersEnabled showDeclinedEvents=$showDeclinedEvents")
        val existingCalendars = calendarDao.getAll()
        val calendars = systemCalendar.getCalendars()
        logger.d("Got ${calendars.size} calendars from device, syncing... (${existingCalendars.size} existing)")
        existingCalendars.forEach { existingCalendar ->
            val matchingCalendar = calendars.find { it.platformId == existingCalendar.platformId }
            if (matchingCalendar != null) {
                val updateCal = existingCalendar.copy(
                    platformId = matchingCalendar.platformId,
                    name = matchingCalendar.name,
                    ownerName = matchingCalendar.ownerName,
                    ownerId = matchingCalendar.ownerId,
                    color = matchingCalendar.color,
                    syncEvents = matchingCalendar.syncEvents,
                    visible = matchingCalendar.visible,
                )
                calendarDao.update(updateCal)
            } else {
                calendarDao.delete(existingCalendar)
            }
        }
        calendars.forEach { newCalendar ->
            if (existingCalendars.none { it.platformId == newCalendar.platformId }) {
                calendarDao.insertOrReplace(newCalendar)
            }
        }

        val allCalendars = calendarDao.getAll()
        val existingPins = timelinePinDao.getPinsForWatchapp(CALENDAR_APP_UUID)
        val startDate = timeProvider.now() - 1.days
        val endDate = (startDate + 7.days)
        val newPins = allCalendars.flatMap { calendar ->
            if (!calendar.enabled || !pinsEnabled) {
                return@flatMap emptyList()
            }
            val events = systemCalendar.getCalendarEvents(calendar, startDate, endDate)
                .filter { event ->
                    if (showDeclinedEvents) return@filter true
                    val currentUserAttendee = event.attendees.find { it.isCurrentUser }
                    currentUserAttendee?.attendanceStatus != EventAttendee.AttendanceStatus.Declined
                }
            val supportsRsvp = systemCalendar.supportsPinActions()
            events.map { event ->
                EventAndPin(event, event.toTimelinePin(calendar, supportsRsvp))
            }
        }
        val remindersToInsert = mutableListOf<TimelineReminder>()
        val remindersToDelete = mutableListOf<Uuid>()
        val toInsert = newPins.mapNotNull { new ->
            val newPin = new.pin
            val existingPin = existingPins.find { it.backingId == newPin.backingId }
            syncReminders(
                remindersEnabled = remindersEnabled,
                event = new.event,
                pinId = existingPin?.itemId ?: newPin.itemId,
                remindersToInsert = remindersToInsert,
                remindersToDelete = remindersToDelete,
            )

            if (existingPin?.recordHashCode() == newPin.recordHashCode()) {
                return@mapNotNull null
            }
            val pin = existingPin?.let {
                newPin.copy(itemId = it.itemId)
            } ?: newPin
            logger.d("New Pin: ${newPin.itemId} (existed: ${existingPin != null})")
            return@mapNotNull pin
        }
        if (toInsert.isNotEmpty()) {
            timelinePinDao.insertOrReplace(toInsert)
        }

        val pinsToDelete = existingPins.filter { pin ->
            if (newPins.none { it.pin.backingId == pin.backingId }) {
                logger.d("Deleting pin ${pin.itemId} (backingId: ${pin.backingId}) as no longer exists in calendar")
                true
            } else {
                false
            }
        }
        if (pinsToDelete.isNotEmpty()) {
            timelinePinDao.markAllForDeletionWithReminders(
                pinsToDelete.map { it.itemId },
                timelineReminderDao
            )
        }
        if (remindersToInsert.isNotEmpty()) {
            timelineReminderDao.insertOrReplace(remindersToInsert)
        }
        if (remindersToDelete.isNotEmpty()) {
            timelineReminderDao.markAllForDeletion(remindersToDelete)
        }
        logger.d("Synced ${allCalendars.size} calendars to DB")
    }

    private suspend fun syncReminders(
        remindersEnabled: Boolean,
        event: CalendarEvent,
        pinId: Uuid,
        remindersToInsert: MutableList<TimelineReminder>,
        remindersToDelete: MutableList<Uuid>,
    ) {
        val existingReminders = timelineReminderDao.getRemindersForPin(pinId)
        val desiredReminders = if (remindersEnabled) {
            event.reminders.map { reminder ->
                event.toTimelineReminder(event.startTime - reminder.minutesBefore.minutes, pinId)
            }
        } else {
            emptyList()
        }

        remindersToDelete += existingReminders.filter { er ->
            desiredReminders.none { dr -> dr.content.timestamp.instant == er.content.timestamp.instant }
        }.map { it.itemId }

        remindersToInsert += desiredReminders.mapNotNull { dr ->
            val existing = existingReminders.find {
                it.content.timestamp.instant == dr.content.timestamp.instant
            }
            when {
                existing == null -> dr
                existing.recordHashCode() != dr.recordHashCode() -> dr.copy(itemId = existing.itemId)
                else -> null
            }
        }
    }

    override fun calendars(): Flow<List<CalendarEntity>> = calendarDao.getFlow()

    override suspend fun createEvent(event: NewCalendarEvent): String? =
        systemCalendar.createEvent(event)

    override fun updateCalendarEnabled(calendarId: Int, enabled: Boolean) {
        libPebbleCoroutineScope.launch {
            calendarDao.setEnabled(calendarId, enabled)
            val calendar = calendarDao.getAll().find { it.id == calendarId }
            // A calendar only actually downloads events to the provider when it's both syncing and
            // visible; repair either flag if missing (enableSyncForCalendar sets both).
            if (calendar != null && (!calendar.syncEvents || !calendar.visible)) {
                systemCalendar.enableSyncForCalendar(calendar)
            }
            requestSync()
        }
    }
}

data class EventAndPin(val event: CalendarEvent, val pin: TimelinePin)
