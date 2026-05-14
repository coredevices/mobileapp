package io.rebble.libpebblecommon.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.BaseAction
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult

class AndroidCalendarActionHandler(
    private val contentResolver: ContentResolver,
) : PlatformCalendarActionHandler {
    private val logger = Logger.withTag("AndroidCalendarActionHandler")

    override suspend fun invoke(pin: TimelinePin, action: BaseAction): TimelineActionResult {
        val internalType = action.internalType
            ?: return failed("No internal type on calendar action ${action.actionID}")
        val backing = pin.backingId?.let { parseBackingId(it) }
            ?: return failed("No backing id on pin ${pin.itemId}")

        return when (internalType) {
            CalendarPinInternalType.ACCEPT -> rsvp(backing.eventId, backing.calendarId, CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED, "Accepted")
            CalendarPinInternalType.MAYBE -> rsvp(backing.eventId, backing.calendarId, CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE, "Maybe")
            CalendarPinInternalType.DECLINE -> rsvp(backing.eventId, backing.calendarId, CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED, "Declined")
            CalendarPinInternalType.CANCEL -> cancel(backing.eventId, backing.startMillis)
            else -> failed("Unknown calendar internal type: $internalType")
        }
    }

    private fun rsvp(eventId: Long, calendarId: Long, status: Int, resultTitle: String): TimelineActionResult {
        val owner = lookupOwnerEmail(calendarId) ?: return failed("No owner email for calendar $calendarId")
        val values = ContentValues().apply {
            put(CalendarContract.Attendees.ATTENDEE_STATUS, status)
        }
        val rows = try {
            contentResolver.update(
                CalendarContract.Attendees.CONTENT_URI,
                values,
                "${CalendarContract.Attendees.EVENT_ID} = ? AND ${CalendarContract.Attendees.ATTENDEE_EMAIL} = ?",
                arrayOf(eventId.toString(), owner),
            )
        } catch (e: SecurityException) {
            logger.w(e) { "RSVP denied: missing WRITE_CALENDAR?" }
            return failed("Permission denied")
        }
        return if (rows > 0) {
            TimelineActionResult(success = true, icon = TimelineIcon.ResultSent, title = resultTitle)
        } else {
            failed("No attendee row for $owner on event $eventId")
        }
    }

    private fun cancel(eventId: Long, startMillis: Long): TimelineActionResult {
        val recurring = isRecurring(eventId)
        val rows = try {
            if (recurring) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId)
                val values = ContentValues().apply {
                    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, startMillis)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                }
                if (contentResolver.insert(uri, values) != null) 1 else 0
            } else {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                val values = ContentValues().apply {
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                }
                contentResolver.update(uri, values, null, null)
            }
        } catch (e: SecurityException) {
            logger.w(e) { "Cancel denied: missing WRITE_CALENDAR?" }
            return failed("Permission denied")
        }
        return if (rows > 0) {
            TimelineActionResult(success = true, icon = TimelineIcon.ResultSent, title = "Canceled")
        } else {
            failed("Failed to cancel event $eventId")
        }
    }

    private fun lookupOwnerEmail(calendarId: Long): String? {
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        contentResolver.query(
            uri,
            arrayOf(CalendarContract.Calendars.OWNER_ACCOUNT),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    private fun isRecurring(eventId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        contentResolver.query(
            uri,
            arrayOf(CalendarContract.Events.RRULE, CalendarContract.Events.RDATE),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val rrule = c.getString(0)
                val rdate = c.getString(1)
                return !rrule.isNullOrEmpty() || !rdate.isNullOrEmpty()
            }
        }
        return false
    }

    private fun failed(reason: String): TimelineActionResult {
        logger.w { reason }
        return TimelineActionResult(success = false, icon = TimelineIcon.ResultFailed, title = "Failed")
    }

    private data class ParsedBackingId(val calendarId: Long, val eventId: Long, val startMillis: Long)

    private fun parseBackingId(backingId: String): ParsedBackingId? {
        // Format: "<calendarId>T<baseEventId>T<Instant string>" (see generateCompositeBackingId).
        val parts = backingId.split("T", limit = 3)
        if (parts.size != 3) return null
        val calendarId = parts[0].toLongOrNull() ?: return null
        val eventId = parts[1].toLongOrNull() ?: return null
        val startMillis = kotlin.time.Instant.parse(parts[2]).toEpochMilliseconds()
        return ParsedBackingId(calendarId, eventId, startMillis)
    }
}
