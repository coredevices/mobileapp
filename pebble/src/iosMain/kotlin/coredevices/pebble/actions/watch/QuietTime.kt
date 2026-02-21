package coredevices.pebble.actions.watch

import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.AlertMask
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.QuietTimeShowNotificationsMode

/** Enables or disables Quiet Time (Do Not Disturb) on the watch. */
fun setQuietTimeEnabled(libPebble: LibPebble, enabled: Boolean) {
    libPebble.setWatchPref(WatchPreference(BoolWatchPref.QuietTimeManuallyEnabled, enabled))
}

/**
 * Sets whether notifications are shown during Quiet Time on the watch.
 * @param show true = Show, false = Hide
 */
fun setQuietTimeShowNotifications(libPebble: LibPebble, show: Boolean) {
    val mode = if (show) QuietTimeShowNotificationsMode.Show else QuietTimeShowNotificationsMode.Hide
    libPebble.setWatchPref(WatchPreference(EnumWatchPref.QuietTimeShowNotifications, mode))
}

/**
 * Sets which interruptions are allowed during Quiet Time on the watch.
 * @param alertMaskName one of: AllOff, PhoneCalls
 */
fun setQuietTimeInterruptions(libPebble: LibPebble, alertMaskName: String) {
    val mask = EnumWatchPref.QuietTimeInterruptions.options
        .filterIsInstance<AlertMask>()
        .firstOrNull { it.name == alertMaskName }
        ?: AlertMask.AllOff
    libPebble.setWatchPref(WatchPreference(EnumWatchPref.QuietTimeInterruptions, mask))
}
