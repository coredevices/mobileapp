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

interface PebbleTimelineActions {
    fun insertTimelinePin(pinJson: String, appUuid: String)
    fun deleteTimelinePin(appUuid: String, pinId: String)
}

interface PebbleWatchInfoActions {
    fun getWatchBatteryLevel(): Int?
    fun isWatchConnected(): Boolean
    fun getConnectedWatchName(): String?
    suspend fun getWatchScreenshotBase64(): String
    fun setBacklightMotion(enabled: Boolean)
}

interface PebbleHealthActions {
    suspend fun getHealthStatsJson(): String
}

