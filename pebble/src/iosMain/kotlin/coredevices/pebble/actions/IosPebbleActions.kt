package coredevices.pebble.actions

import coredevices.pebble.actions.watch.deleteTimelinePin
import coredevices.pebble.actions.watch.getConnectedWatchName
import coredevices.pebble.actions.watch.getWatchBatteryLevel
import coredevices.pebble.actions.watch.getWatchScreenshotBase64
import coredevices.pebble.actions.watch.getWatchScreenshotBytes
import coredevices.pebble.actions.watch.healthDebugStatsToJson
import coredevices.pebble.actions.watch.insertTimelinePinRich
import coredevices.pebble.actions.watch.isWatchConnected
import coredevices.pebble.actions.watch.launchAppByUuid
import coredevices.pebble.actions.watch.sendDetailedNotification
import coredevices.pebble.actions.watch.sendSimpleNotification
import coredevices.pebble.actions.watch.setBacklightMotion
import coredevices.pebble.actions.watch.setNotificationBacklight
import coredevices.pebble.actions.watch.setNotificationFilter
import coredevices.pebble.actions.watch.setQuietTimeEnabled
import coredevices.pebble.actions.watch.setQuietTimeInterruptions
import coredevices.pebble.actions.watch.setQuietTimeShowNotifications
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.js.RemoteTimelineEmulator
import kotlin.uuid.Uuid

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

class IosPebbleTimelineActions(
    private val libPebble: LibPebble,
) : PebbleTimelineActions {
    override fun insertTimelinePinRich(
        appUuid: String,
        title: String,
        body: String,
        subtitle: String?,
        iconCode: String?,
        epochSeconds: Long?,
    ): String = insertTimelinePinRich(
        libPebble = libPebble,
        appUuid = Uuid.parse(appUuid),
        title = title,
        body = body,
        subtitle = subtitle,
        iconCode = iconCode,
        epochSeconds = epochSeconds,
    )

    override fun deleteTimelinePin(appUuid: String, pinId: String) {
        deleteTimelinePin(libPebble, Uuid.parse(appUuid), pinId)
    }
}

class IosPebbleWatchInfoActions(
    private val libPebble: LibPebble,
) : PebbleWatchInfoActions {
    override fun getWatchBatteryLevel(): Int? = getWatchBatteryLevel(libPebble)
    override fun isWatchConnected(): Boolean = isWatchConnected(libPebble)
    override fun getConnectedWatchName(): String? = getConnectedWatchName(libPebble)

    override suspend fun getWatchScreenshotBase64(): String =
        getWatchScreenshotBase64(libPebble)

    override suspend fun getWatchScreenshotBytes(): ByteArray? =
        getWatchScreenshotBytes(libPebble)

    override fun setBacklightMotion(enabled: Boolean) {
        setBacklightMotion(libPebble, enabled)
    }
}

class IosPebbleHealthActions(
    private val libPebble: LibPebble,
) : PebbleHealthActions {
    override suspend fun getHealthStatsJson(): String =
        healthDebugStatsToJson(libPebble.getHealthDebugStats())
}

