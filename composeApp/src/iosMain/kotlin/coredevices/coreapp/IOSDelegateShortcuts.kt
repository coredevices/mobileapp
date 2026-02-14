package coredevices.coreapp

import co.touchlab.kermit.Logger
import coredevices.coreapp.ui.navigation.CoreDeepLinkHandler
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.actions.PebbleAppActions
import coredevices.pebble.actions.PebbleHealthActions
import coredevices.pebble.actions.PebbleNotificationActions
import coredevices.pebble.actions.PebbleQuietTimeActions
import coredevices.pebble.actions.PebbleTimelineActions
import coredevices.pebble.actions.PebbleWatchInfoActions
import coredevices.pebble.actions.watch.getWatchScreenshotBase64
import io.rebble.libpebblecommon.SystemAppIDs
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.timeline.TimelineColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

private val shortcutsLogger = Logger.withTag("IOSDelegateShortcuts")

/**
 * Kotlin-side bridge for iOS Shortcuts / AppIntents.
 *
 * All methods here are exposed to Swift via @ObjCName and are called from PebbleShortcuts.swift.
 * This keeps shortcut-related glue separated from the core iOS app delegate logic.
 */
object IOSDelegateShortcuts : KoinComponent {
    private val appActions: PebbleAppActions by inject()
    private val notificationActions: PebbleNotificationActions by inject()
    private val quietTimeActions: PebbleQuietTimeActions by inject()
    private val timelineActions: PebbleTimelineActions by inject()

    /**
     * Called from the iOS Shortcut to send a simple notification (title + body) to the watch.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("sendSimpleNotificationToWatchWithTitleBody")
    fun sendSimpleNotificationToWatch(title: String, body: String) {
        notificationActions.sendSimpleNotification(title, body)
    }

    /**
     * Called from the iOS Shortcut to get the list of timeline colors for the picker.
     * Returns JSON: [{"id":"","title":"None"},{"id":"Orange","title":"Orange"},...]
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getTimelineColorsForShortcutsWithCompletion")
    fun getTimelineColorsForShortcuts(callback: (String) -> Unit) {
        GlobalScope.launch {
            fun escape(s: String) = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            val none = """{"id":"","title":"None"}"""
            val colors = TimelineColor.entries.map {
                """{"id":"${it.name}","title":"${escape(it.displayName)}"}"""
            }
            val json = listOf(none) + colors
            withContext(Dispatchers.Main) { callback("[${json.joinToString(",")}]") }
        }
    }

    /**
     * Called from the iOS Shortcut to get the list of timeline icons for the picker.
     * Returns JSON: [{"id":"","title":"None"},{"id":"system://images/GENERIC_SMS","title":"Generic Sms"},...]
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getTimelineIconsForShortcutsWithCompletion")
    fun getTimelineIconsForShortcuts(callback: (String) -> Unit) {
        GlobalScope.launch {
            fun escape(s: String) = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            fun iconTitle(icon: TimelineIcon) = icon.code
                .replace("system://images/", "")
                .replace("_", " ")
                .lowercase()
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

            val none = """{"id":"","title":"None"}"""
            val icons = TimelineIcon.entries.map {
                """{"id":"${escape(it.code)}","title":"${escape(iconTitle(it))}"}"""
            }
            val json = listOf(none) + icons
            withContext(Dispatchers.Main) { callback("[${json.joinToString(",")}]") }
        }
    }

    /**
     * Called from the iOS Shortcut to send a notification with custom title, body, color and icon.
     * Pass null or empty string for colorName/iconCode to use none.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("sendDetailedNotificationToWatch")
    fun sendDetailedNotificationToWatch(title: String, body: String, colorName: String?, iconCode: String?) {
        notificationActions.sendDetailedNotification(
            title,
            body,
            colorName?.takeIf { it.isNotEmpty() },
            iconCode?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Called from the iOS Shortcut to enable or disable Quiet Time on the watch.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setQuietTimeEnabledWithEnabled")
    fun setQuietTimeEnabled(enabled: Boolean) {
        quietTimeActions.setQuietTimeEnabled(enabled)
    }

    /**
     * Called from the iOS Shortcut to set Quiet Time show notifications: true = Show, false = Hide.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setQuietTimeShowNotificationsWithShow")
    fun setQuietTimeShowNotifications(show: Boolean) {
        quietTimeActions.setQuietTimeShowNotifications(show)
    }

    /**
     * Called from the iOS Shortcut to set Quiet Time interruptions: AllOff or PhoneCalls.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setQuietTimeInterruptionsWithAlertMaskName")
    fun setQuietTimeInterruptions(alertMaskName: String) {
        quietTimeActions.setQuietTimeInterruptions(alertMaskName)
    }

    /**
     * Called from the iOS Shortcut to enable or disable notification backlight on the watch.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setNotificationBacklightWithEnabled")
    fun setNotificationBacklight(enabled: Boolean) {
        notificationActions.setNotificationBacklight(enabled)
    }

    /**
     * Called from the iOS Shortcut to enable or disable motion backlight (backlight on wrist raise) on the watch.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setBacklightMotionWithEnabled")
    fun setBacklightMotion(enabled: Boolean) {
        val watchInfoActions: PebbleWatchInfoActions = get()
        watchInfoActions.setBacklightMotion(enabled)
    }

    /**
     * Called from the iOS Shortcut to set notification filter: AllOn, PhoneCalls, or AllOff.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setNotificationFilterWithAlertMaskName")
    fun setNotificationFilter(alertMaskName: String) {
        notificationActions.setNotificationFilter(alertMaskName)
    }

    /**
     * Called from the iOS Shortcut to get the list of watchfaces for the picker.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getLockerWatchfacesForShortcutsWithCompletion")
    fun getLockerWatchfacesForShortcuts(callback: (String) -> Unit) {
        getLockerItemsForShortcutsByType(AppType.Watchface, callback)
    }

    /**
     * Called from the iOS Shortcut to get the list of watchapps for the picker.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getLockerWatchappsForShortcutsWithCompletion")
    fun getLockerWatchappsForShortcuts(callback: (String) -> Unit) {
        getLockerItemsForShortcutsByType(AppType.Watchapp, callback)
    }

    private fun getLockerItemsForShortcutsByType(type: AppType, callback: (String) -> Unit) {
        GlobalScope.launch {
            val libPebble: LibPebble = get()
            val list = libPebble.getAllLockerBasicInfo().first().filter { it.type == type }
            fun escape(s: String) = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            val json = list.joinToString(",") { """{"id":"${it.id}","title":"${escape(it.title)}"}""" }
                .let { "[$it]" }
            withContext(Dispatchers.Main) {
                callback(json)
            }
        }
    }

    /**
     * Called from the iOS Shortcut to launch an app/watchface on the watch by UUID.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("launchAppByUuidWithUuid")
    fun launchAppByUuid(uuid: String) {
        appActions.launchApp(uuid)
    }

    /**
     * Called from the iOS Shortcut to get the list of notification apps for the picker.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getNotificationAppsForShortcutsWithCompletion")
    fun getNotificationAppsForShortcuts(callback: (String) -> Unit) {
        GlobalScope.launch {
            val libPebble: LibPebble = get()
            val list = libPebble.notificationApps().first()
            fun escape(s: String) = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            val json = list.joinToString(",") { entry ->
                val app = entry.app
                val muted = app.muteState == MuteState.Always
                """{"id":"${escape(app.packageName)}","title":"${escape(app.name)}","muted":$muted}"""
            }.let { "[$it]" }
            withContext(Dispatchers.Main) {
                callback(json)
            }
        }
    }

    /**
     * Called from the iOS Shortcut to mute or unmute a notification app by package name.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setNotificationAppMuteStateWithPackageNameMute")
    fun setNotificationAppMuteState(packageName: String, mute: Boolean) {
        notificationActions.setAppMuteState(packageName, mute)
    }

    /**
     * Called from the iOS Shortcut to insert a timeline pin under the Settings app.
     * Uses [SystemAppIDs.SETTINGS_APP_UUID] so pins appear in the timeline under Settings.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("insertTimelinePinWithPinJson")
    fun insertTimelinePin(pinJson: String) {
        timelineActions.insertTimelinePin(pinJson, SystemAppIDs.SETTINGS_APP_UUID.toString())
    }

    /**
     * Called from the iOS Shortcut to delete a timeline pin by id (pins use Settings app).
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("deleteTimelinePinWithPinId")
    fun deleteTimelinePin(pinId: String) {
        timelineActions.deleteTimelinePin(SystemAppIDs.SETTINGS_APP_UUID.toString(), pinId)
    }

    /**
     * Called from the iOS Shortcut to get the connected watch battery level (0–100).
     * Returns the percentage as string, or empty string if no watch connected or battery unknown.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getWatchBatteryLevelWithCompletion")
    fun getWatchBatteryLevel(callback: (String) -> Unit) {
        GlobalScope.launch {
            val watchInfoActions: PebbleWatchInfoActions = get()
            val level = watchInfoActions.getWatchBatteryLevel()
            withContext(Dispatchers.Main) {
                callback(level?.toString() ?: "")
            }
        }
    }

    /**
     * Called from the iOS Shortcut to know if a watch is fully connected.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getWatchConnectedWithCompletion")
    fun getWatchConnected(callback: (Boolean) -> Unit) {
        GlobalScope.launch {
            val watchInfoActions: PebbleWatchInfoActions = get()
            val connected = watchInfoActions.isWatchConnected()
            withContext(Dispatchers.Main) {
                callback(connected)
            }
        }
    }

    /**
     * Called from the iOS Shortcut to get the connected watch name.
     * Returns empty string if no watch connected.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getWatchNameWithCompletion")
    fun getWatchName(callback: (String) -> Unit) {
        GlobalScope.launch {
            val watchInfoActions: PebbleWatchInfoActions = get()
            val name = watchInfoActions.getConnectedWatchName()
            withContext(Dispatchers.Main) {
                callback(name ?: "")
            }
        }
    }

    /**
     * Called from the iOS Shortcut to get health stats (steps, sleep) as JSON.
     * Returns JSON object with totalSteps30Days, averageStepsPerDay, totalSleepSeconds30Days,
     * averageSleepSecondsPerDay, todaySteps, lastNightSleepHours, latestDataTimestamp, daysOfData.
     * Returns "{}" on error.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getWatchHealthStatsWithCompletion")
    fun getWatchHealthStats(callback: (String) -> Unit) {
        GlobalScope.launch {
            try {
                val healthActions: PebbleHealthActions = get()
                val json = healthActions.getHealthStatsJson()
                withContext(Dispatchers.Main) {
                    callback(json)
                }
            } catch (e: Exception) {
                shortcutsLogger.e(e) { "getWatchHealthStats failed" }
                withContext(Dispatchers.Main) {
                    callback("{}")
                }
            }
        }
    }

    /**
     * Called from the iOS Shortcut to get the watch screenshot as base64 PNG.
     * Returns empty string if no watch connected or screenshot failed.
     * In Shortcuts, use "Decode Base64" on the result to get the image.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getWatchScreenshotWithCompletion")
    fun getWatchScreenshot(@ObjCName("completion") callback: (String) -> Unit) {
        GlobalScope.launch {
            try {
                val libPebble: LibPebble = get()
                val base64 = getWatchScreenshotBase64(libPebble)
                withContext(Dispatchers.Main) { callback(base64) }
            } catch (e: Exception) {
                shortcutsLogger.e(e) { "getWatchScreenshot failed" }
                withContext(Dispatchers.Main) { callback("") }
            }
        }
    }

}

