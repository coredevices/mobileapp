package coredevices.pebble.ui

import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import io.rebble.libpebblecommon.SystemAppIDs.AIRPLANE_MODE_UUID
import io.rebble.libpebblecommon.SystemAppIDs.BACKLIGHT_UUID
import io.rebble.libpebblecommon.SystemAppIDs.HEALTH_APP_UUID
import io.rebble.libpebblecommon.SystemAppIDs.MOTION_BACKLIGHT_UUID
import io.rebble.libpebblecommon.SystemAppIDs.QUIET_TIME_TOGGLE_UUID
import io.rebble.libpebblecommon.SystemAppIDs.TIMELINE_FUTURE_UUID
import io.rebble.libpebblecommon.SystemAppIDs.TIMELINE_PAST_UUID
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.ColorWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.NumberWatchPref
import io.rebble.libpebblecommon.database.entity.QuickLaunchSetting
import io.rebble.libpebblecommon.database.entity.QuicklaunchWatchPref
import io.rebble.libpebblecommon.database.entity.WatchPref
import io.rebble.libpebblecommon.database.entity.WatchPrefEnum
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.timeline.TimelineColor
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

@Composable
fun watchPrefs(): List<SettingsItem> {
    val libPebble = rememberLibPebble()
    val settings by libPebble.watchPrefs.collectAsState(emptyList())
    val quickLaunchOptions = quickLaunchOptions(libPebble)
    val mapped = remember(settings, quickLaunchOptions) {
        settings.map { item ->
            when (val pref = item.pref) {
                is BoolWatchPref -> booleanPref(pref.castParent(item), libPebble)
                is EnumWatchPref -> enumPref(pref.castParent(item), libPebble)
                is QuicklaunchWatchPref -> quicklaunchPref(pref.castParent(item), libPebble, quickLaunchOptions)
                is ColorWatchPref -> colorPref(pref.castParent(item), libPebble)
                is NumberWatchPref -> numberPref(pref.castParent(item), libPebble)
            }
        }
    }
    return mapped
}

fun WatchPref<*>.section(): Section = when (this) {
    BoolWatchPref.TimezoneSourceIsManual -> Section.Time
    BoolWatchPref.Clock24h -> Section.Time
    BoolWatchPref.StandbyMode -> Section.Default
    BoolWatchPref.LeftHandedMode -> Section.Display
    BoolWatchPref.Backlight -> Section.Display
    BoolWatchPref.AmbientLightSensor -> Section.Display
    BoolWatchPref.BacklightMotion -> Section.Display
    BoolWatchPref.DynamicBacklightIntensity -> Section.Display
    BoolWatchPref.LanguageEnglish -> Section.Default
    ColorWatchPref.SettingsMenuHighlightColor -> Section.Display
    ColorWatchPref.AppMenuHighlightColor -> Section.Display
    EnumWatchPref.TextSize -> Section.Notifications
    NumberWatchPref.MotionSensitivity -> Section.Display
    NumberWatchPref.BacklightTimeoutMs -> Section.Display
    NumberWatchPref.AmbientLightThreshold -> Section.Display
    NumberWatchPref.DynamicBacklightMinThreshold -> Section.Display
    NumberWatchPref.DynamicBacklightMaxThreshold -> Section.Display
    QuicklaunchWatchPref.QlUp -> Section.QuickLaunch
    QuicklaunchWatchPref.QlDown -> Section.QuickLaunch
    QuicklaunchWatchPref.QlSelect -> Section.QuickLaunch
    QuicklaunchWatchPref.QlBack -> Section.QuickLaunch
    QuicklaunchWatchPref.QlSingleClickUp -> Section.QuickLaunch
    QuicklaunchWatchPref.QlSingleClickDown -> Section.QuickLaunch
    BoolWatchPref.TimelineQuickViewEnabled -> Section.Timeline
    NumberWatchPref.TimelineQuickViewMinsBefore -> Section.Timeline
    EnumWatchPref.NotificationFilter -> Section.Notifications
    EnumWatchPref.QuietTimeInterruptions -> Section.QuietTime
    EnumWatchPref.QuietTimeShowNotifications -> Section.QuietTime
    EnumWatchPref.LegacyVibeIntensity -> Section.Notifications
    EnumWatchPref.VibeScoreNotifications -> Section.Notifications
    EnumWatchPref.VibeScoreCalls -> Section.Notifications
    EnumWatchPref.VibeScoreAlarms -> Section.Notifications
    BoolWatchPref.QuietTimeManuallyEnabled -> Section.QuietTime
    BoolWatchPref.CalendarAwareQuietTime -> Section.QuietTime
    BoolWatchPref.AlternativeNotificationStyle -> Section.Notifications
    BoolWatchPref.NotificationVibeDelay -> Section.Notifications
    BoolWatchPref.NotificationBacklight -> Section.Notifications
    NumberWatchPref.NotificationTimeoutMs -> Section.Notifications
}

private fun numberPref(item: WatchPreference<Long>, libPebble: LibPebble): SettingsItem {
    val pref = item.pref as NumberWatchPref
    return basicSettingsNumberItem(
        title = pref.displayName,
        topLevelType = TopLevelType.Watch,
        section = pref.section(),
        value = item.valueOrDefault(),
        min = pref.min,
        max = pref.max,
        onValueChange = {
            libPebble.setWatchPref(item.copy(value = it))
        },
        isDebugSetting = pref.isDebugSetting,
    )
}

private fun colorPref(item: WatchPreference<TimelineColor>, libPebble: LibPebble): SettingsItem {
    val pref = item.pref as ColorWatchPref
    val default = item.valueOrDefault()
    return SettingsItem(
        title = pref.displayName,
        topLevelType = TopLevelType.Watch,
        section = pref.section(),
        item = {
            ListItem(
                headlineContent = {
                    Text(pref.displayName)
                },
                supportingContent = {
                    SelectColorOrNone(
                        currentColorName = default.identifier,
                        onChangeColor = { color ->
                            libPebble.setWatchPref(item.copy(value = color))
                        },
                        availableColors = pref.availableColors,
                        defaultToListTab = true,
                    )
                },
                shadowElevation = 2.dp,
            )
        },
        isDebugSetting = pref.isDebugSetting,
    )
}

private fun booleanPref(item: WatchPreference<Boolean>, libPebble: LibPebble): SettingsItem {
    return basicSettingsToggleItem(
        title = item.pref.displayName,
        topLevelType = TopLevelType.Watch,
        section = item.pref.section(),
        checked = item.valueOrDefault(),
        onCheckChanged = { enabled ->
            libPebble.setWatchPref(item.copy(value = enabled))
        },
        isDebugSetting = item.pref.isDebugSetting,
    )
}

private fun enumPref(item: WatchPreference<WatchPrefEnum>, libPebble: LibPebble): SettingsItem {
    val pref = item.pref as EnumWatchPref
    return basicSettingsDropdownItem(
        title = pref.displayName,
        topLevelType = TopLevelType.Watch,
        section = pref.section(),
        selectedItem = item.valueOrDefault(),
        items = pref.options,
        onItemSelected = {
            libPebble.setWatchPref(item.copy(value = it))
        },
        itemText = { it.displayName },
        isDebugSetting = pref.isDebugSetting,
    )
}

data class QuickLaunchOption(
    val uuid: Uuid?,
    val displayName: String,
)

@Composable
private fun quickLaunchOptions(libPebble: LibPebble): List<QuickLaunchOption> {
    val installedApps by libPebble.getLocker(
        type = AppType.Watchapp,
        searchQuery = null,
        limit = LOCKER_UI_LOAD_LIMIT,
    ).map { apps ->
        apps.filter { app -> app.isSynced() }
    }.collectAsState(emptyList())
    return remember(installedApps) {
        listOf(QuickLaunchOption(null, "None")) +
                QuickLaunchOption(QUIET_TIME_TOGGLE_UUID, "Quiet Time") +
                QuickLaunchOption(BACKLIGHT_UUID, "Backlight") +
                QuickLaunchOption(MOTION_BACKLIGHT_UUID, "Backlight") +
                QuickLaunchOption(AIRPLANE_MODE_UUID, "Airplane Mode") +
                QuickLaunchOption(TIMELINE_PAST_UUID, "Timeline Past") +
                QuickLaunchOption(TIMELINE_FUTURE_UUID, "Timeline Future") +

                QuickLaunchOption(HEALTH_APP_UUID, "Health") +
                installedApps.map { app ->
                    QuickLaunchOption(app.properties.id, app.properties.title)
                }
    }
}

private fun quicklaunchPref(item: WatchPreference<QuickLaunchSetting>, libPebble: LibPebble, options: List<QuickLaunchOption>): SettingsItem {
    val default = item.valueOrDefault()
    val defaultQl = options.firstOrNull { it.uuid == default.uuid } ?: options[0]
    return basicSettingsDropdownItem(
        title = item.pref.displayName,
        topLevelType = TopLevelType.Watch,
        section = item.pref.section(),
        selectedItem = defaultQl,
        items = options,
        onItemSelected = {
            libPebble.setWatchPref(
                WatchPreference(
                    item.pref, QuickLaunchSetting(
                        enabled = it.uuid != null,
                        uuid = it.uuid,
                    )
                )
            )
        },
        itemText = { it.displayName },
        isDebugSetting = item.pref.isDebugSetting,
    )
}
