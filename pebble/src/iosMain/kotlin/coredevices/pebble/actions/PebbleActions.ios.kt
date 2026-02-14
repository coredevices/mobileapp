package coredevices.pebble.actions

import coredevices.pebble.actions.watch.launchAppByUuid
import coredevices.pebble.actions.watch.sendDetailedNotification
import coredevices.pebble.actions.watch.sendSimpleNotification
import coredevices.pebble.actions.watch.setNotificationBacklight
import coredevices.pebble.actions.watch.setNotificationFilter
import coredevices.pebble.actions.watch.setQuietTimeEnabled
import coredevices.pebble.actions.watch.setQuietTimeInterruptions
import coredevices.pebble.actions.watch.setQuietTimeShowNotifications
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.database.entity.MuteState

/**
 * iOS implementation of Pebble actions
 */
class IosPebbleAppActions(
    private val libPebble: LibPebble,
) : PebbleAppActions {
    override fun launchApp(uuid: String) {
        launchAppByUuid(libPebble, uuid)
    }
}

class IosPebbleNotificationActions(
    private val libPebble: LibPebble,
    private val notificationApps: NotificationApps,
) : PebbleNotificationActions {
    override fun sendSimpleNotification(title: String, body: String) {
        sendSimpleNotification(libPebble, title, body)
    }

    override fun sendDetailedNotification(
        title: String,
        body: String,
        colorName: String?,
        iconCode: String?,
    ) {
        sendDetailedNotification(libPebble, title, body, colorName, iconCode)
    }

    override fun setAppMuteState(packageName: String, mute: Boolean) {
        notificationApps.updateNotificationAppMuteState(
            packageName = packageName,
            muteState = if (mute) MuteState.Always else MuteState.Never,
        )
    }

    override fun setNotificationBacklight(enabled: Boolean) {
        setNotificationBacklight(libPebble, enabled)
    }

    override fun setNotificationFilter(alertMaskName: String) {
        setNotificationFilter(libPebble, alertMaskName)
    }
}

class IosPebbleQuietTimeActions(
    private val libPebble: LibPebble,
) : PebbleQuietTimeActions {
    override fun setQuietTimeEnabled(enabled: Boolean) {
        setQuietTimeEnabled(libPebble, enabled)
    }

    override fun setQuietTimeShowNotifications(show: Boolean) {
        setQuietTimeShowNotifications(libPebble, show)
    }

    override fun setQuietTimeInterruptions(alertMaskName: String) {
        setQuietTimeInterruptions(libPebble, alertMaskName)
    }
}

