package coredevices.pebble.actions.watch

import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.AlertMask
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.buildTimelineNotification
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.timeline.TimelineColor
import io.rebble.libpebblecommon.timeline.toPebbleColor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Sends a simple notification (title + body). */
fun sendSimpleNotification(libPebble: LibPebble, title: String, body: String) {
    GlobalScope.launch {
        val titleText = title.ifEmpty { "Notification" }
        val bodyText = body.ifEmpty { " " }
        val notif = buildTimelineNotification(
            parentId = Uuid.NIL,
            timestamp = Clock.System.now(),
        ) {
            layout = TimelineItem.Layout.GenericNotification
            attributes {
                title { titleText }
                body { bodyText }
            }
        }
        libPebble.sendNotification(notif, null)
    }
}

/**
 * Sends a notification with custom title, body, color and icon.
 * [colorName] and [iconCode] can be null or empty for default (no color / no icon).
 */
fun sendDetailedNotification(
    libPebble: LibPebble,
    title: String,
    body: String,
    colorName: String?,
    iconCode: String?,
) {
    GlobalScope.launch {
        val notif = buildTimelineNotification(
            parentId = Uuid.NIL,
            timestamp = Clock.System.now(),
        ) {
            layout = TimelineItem.Layout.GenericNotification
            attributes {
                title { title.ifEmpty { "Notification" } }
                body { body.ifEmpty { " " } }
                val color = colorName?.takeIf { it.isNotEmpty() }?.let { TimelineColor.findByName(it) }
                color?.let { backgroundColor { it.toPebbleColor() } }
                val icon = iconCode?.takeIf { it.isNotEmpty() }?.let { TimelineIcon.fromCode(it) }
                icon?.let { tinyIcon { it } }
            }
        }
        libPebble.sendNotification(notif, null)
    }
}

/** Enables or disables notification backlight on the watch. */
fun setNotificationBacklight(libPebble: LibPebble, enabled: Boolean) {
    libPebble.setWatchPref(WatchPreference(BoolWatchPref.NotificationBacklight, enabled))
}

/**
 * Sets the notification filter on the watch.
 * @param alertMaskName one of: AllOn, PhoneCalls, AllOff
 */
fun setNotificationFilter(libPebble: LibPebble, alertMaskName: String) {
    val mask = EnumWatchPref.NotificationFilter.options
        .filterIsInstance<AlertMask>()
        .firstOrNull { it.name == alertMaskName }
        ?: AlertMask.AllOn
    libPebble.setWatchPref(WatchPreference(EnumWatchPref.NotificationFilter, mask))
}

