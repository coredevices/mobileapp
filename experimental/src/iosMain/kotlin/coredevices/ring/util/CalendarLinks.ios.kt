package coredevices.ring.util

import co.touchlab.kermit.Logger
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.time.Instant

/** Seconds between the Unix epoch and Apple's reference date (2001-01-01), which the
 *  `calshow:` URL scheme counts from. */
private const val APPLE_REFERENCE_EPOCH_SECONDS = 978_307_200L

actual fun openSystemCalendarAt(startTime: Instant) {
    val logger = Logger.withTag("CalendarLinks")
    val seconds = startTime.epochSeconds - APPLE_REFERENCE_EPOCH_SECONDS
    val url = NSURL.URLWithString("calshow:$seconds")
    if (url == null) {
        logger.w { "Failed to build calshow URL for $startTime" }
        return
    }
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>()) { success ->
        if (!success) logger.w { "Failed to open calendar at $startTime" }
    }
}
