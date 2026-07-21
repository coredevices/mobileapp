package coredevices.ring.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import co.touchlab.kermit.Logger
import org.koin.mp.KoinPlatform
import kotlin.time.Instant

actual fun openSystemCalendarAt(startTime: Instant) {
    val logger = Logger.withTag("CalendarLinks")
    try {
        val context: Context = KoinPlatform.getKoin().get()
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .let { ContentUris.appendId(it, startTime.toEpochMilliseconds()) }
            .build()
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        logger.w(e) { "Failed to open calendar at $startTime" }
    }
}
