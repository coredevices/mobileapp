package coredevices.pebble.actions

import io.rebble.libpebblecommon.connection.LibPebble

/**
 * Android implementation of Pebble actions.
 *
 * For now these are placeholders (no-op) but provide a clear extension point
 * for future integrations (e.g. Tasker plugin, Android Shortcuts, etc.).
 */
class AndroidPebbleAppActions(
    private val libPebble: LibPebble,
) : PebbleAppActions {
    override fun launchApp(uuid: String) {
        // TODO: implement Android-specific app launch actions (Tasker/Intents) if needed
    }
}

class AndroidPebbleNotificationActions(
    private val libPebble: LibPebble,
) : PebbleNotificationActions {
    override fun sendSimpleNotification(title: String, body: String) {
        // TODO: implement Android-specific notification actions if needed
    }

    override fun sendDetailedNotification(
        title: String,
        body: String,
        colorName: String?,
        iconCode: String?,
    ) {
        // TODO: implement Android-specific detailed notification actions if needed
    }

    override fun setAppMuteState(packageName: String, mute: Boolean) {
        // TODO: implement Android-specific app mute actions if needed
    }

    override fun setNotificationBacklight(enabled: Boolean) {
        // TODO: implement Android-specific watch pref actions if needed
    }

    override fun setNotificationFilter(alertMaskName: String) {
        // TODO: implement Android-specific watch pref actions if needed
    }
}

class AndroidPebbleQuietTimeActions(
    private val libPebble: LibPebble,
) : PebbleQuietTimeActions {
    override fun setQuietTimeEnabled(enabled: Boolean) {
        // TODO: implement Android-specific quiet time actions if needed
    }

    override fun setQuietTimeShowNotifications(show: Boolean) {
        // TODO: implement Android-specific quiet time actions if needed
    }

    override fun setQuietTimeInterruptions(alertMaskName: String) {
        // TODO: implement Android-specific quiet time actions if needed
    }
}

