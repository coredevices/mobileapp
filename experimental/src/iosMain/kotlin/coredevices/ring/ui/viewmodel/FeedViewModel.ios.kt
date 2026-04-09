package coredevices.ring.ui.viewmodel

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import co.touchlab.kermit.Logger
import kotlinx.datetime.toNSDate
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKSpan
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Instant

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun makeTextClipEntry(text: String): ClipEntry {
    return ClipEntry.withPlainText(text)
}

actual suspend fun addCalendarEvent(
    title: String,
    startTime: Instant,
    endTime: Instant,
    allDay: Boolean
) {
    val ek = EKEventStore()
    val logger = Logger.withTag("FeedViewModel")
    val grant = suspendCoroutine {
        ek.requestFullAccessToEventsWithCompletion { granted, error ->
            if (error != null) {
                logger.e { "Error requesting calendar permissions: $error" }
            }
            it.resume(granted)
        }
    }
    if (!grant) {
        logger.w { "Calendar permission not granted, cannot add event" }
        error("Calendar permission not granted")
    }
    val newEvent = EKEvent.eventWithEventStore(ek)
    newEvent.title = title
    newEvent.startDate = startTime.toNSDate()
    newEvent.endDate = endTime.toNSDate()
    newEvent.allDay = allDay
    newEvent.calendar = ek.defaultCalendarForNewEvents

    try {
        ek.saveEvent(
            newEvent,
            span = EKSpan.EKSpanThisEvent,
            commit = true,
            error = null
        )
        logger.d { "Successfully added calendar event: $title at $startTime" }
    } catch (e: Exception) {
        logger.e(e) { "Failed to add calendar event: $title at $startTime" }
        throw e
    }
}