package coredevices.util

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult
import kotlin.coroutines.cancellation.CancellationException

/** Invoking it on the watch runs the handler registered with `Timeline.setActionHandler` for [internalType]. */
class WatchNotificationAction(
    val title: String,
    val internalType: String,
    val data: String? = null,
)

/**
 * Sends the app's own phone notifications to the watch. Only does anything on iOS, where the app's
 * ANCS notifications are muted on the watch so that actions can be handled by the app.
 */
interface WatchNotifier {
    /** [key] identifies the notification: sending the same key again replaces it. */
    fun send(key: String, title: String, body: String?, actions: List<WatchNotificationAction> = emptyList())

    fun remove(key: String)
}

/** The notification listener already forwards the app's own notifications. */
class NoOpWatchNotifier : WatchNotifier {
    override fun send(key: String, title: String, body: String?, actions: List<WatchNotificationAction>) = Unit

    override fun remove(key: String) = Unit
}

/** Registers a handler for [WatchNotificationAction]s with this [internalType]. */
fun LibPebble.setWatchNotificationActionHandler(internalType: String, handler: suspend (data: String?) -> Unit) {
    setActionHandler(internalType) { data, _ ->
        try {
            handler(data)
            TimelineActionResult(success = true, icon = TimelineIcon.GenericConfirmation, title = "Done")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.withTag("WatchNotifications").e(e) { "Watch action $internalType failed" }
            TimelineActionResult(success = false, icon = TimelineIcon.ResultFailed, title = "Failed")
        }
    }
}
