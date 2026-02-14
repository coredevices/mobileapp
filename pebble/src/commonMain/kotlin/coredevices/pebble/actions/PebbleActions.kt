package coredevices.pebble.actions

/**
 * High-level Pebble actions that can be implemented per platform (iOS, Android, etc.).
 */
interface PebbleAppActions {
    fun launchApp(uuid: String)
}

interface PebbleNotificationActions {
    fun sendSimpleNotification(title: String, body: String)
    fun sendDetailedNotification(
        title: String,
        body: String,
        colorName: String?,
        iconCode: String?,
    )

    fun setAppMuteState(packageName: String, mute: Boolean)
    fun setNotificationBacklight(enabled: Boolean)
    fun setNotificationFilter(alertMaskName: String)
}

interface PebbleQuietTimeActions {
    fun setQuietTimeEnabled(enabled: Boolean)
    fun setQuietTimeShowNotifications(show: Boolean)
    fun setQuietTimeInterruptions(alertMaskName: String)
}

