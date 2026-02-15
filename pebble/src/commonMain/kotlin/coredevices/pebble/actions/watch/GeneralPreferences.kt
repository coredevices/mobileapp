package coredevices.pebble.actions.watch

import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.AlertMask
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref

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
