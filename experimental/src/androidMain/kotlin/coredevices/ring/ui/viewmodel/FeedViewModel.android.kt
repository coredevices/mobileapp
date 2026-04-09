package coredevices.ring.ui.viewmodel

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.compose.ui.platform.ClipEntry
import co.touchlab.kermit.Logger
import kotlinx.datetime.TimeZone
import org.koin.mp.KoinPlatform
import kotlin.time.Instant

actual suspend fun makeTextClipEntry(text: String): ClipEntry {
    return ClipEntry(ClipData.newPlainText("Note contents", text))
}

actual suspend fun addCalendarEvent(
    title: String,
    startTime: Instant,
    endTime: Instant,
    allDay: Boolean
) {
    val context: Context = KoinPlatform.getKoin().get()
    val contentResolver = context.contentResolver
    val eventUri = CalendarContract.Events.CONTENT_URI
    val logger = Logger.withTag("FeedViewModel")
    val values = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, 0)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DTSTART, startTime.toEpochMilliseconds())
        put(CalendarContract.Events.DTEND, endTime.toEpochMilliseconds())
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.currentSystemDefault().id)
        put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
    }
    try {
        contentResolver.insert(eventUri, values)
        logger.d { "Successfully added calendar event" }
    } catch (e: Exception) {
        logger.w(e) { "Failed to add calendar event" }
    }
}