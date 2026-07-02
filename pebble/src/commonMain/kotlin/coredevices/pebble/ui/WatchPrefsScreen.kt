package coredevices.pebble.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.pebble.backlight.formatBacklightScheduleMinute
import coredevices.pebble.rememberLibPebble
import coredevices.ui.ConfirmDialog
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
import io.rebble.libpebblecommon.database.entity.RgbColorWatchPref
import io.rebble.libpebblecommon.database.entity.WatchPref
import io.rebble.libpebblecommon.database.entity.WatchPrefEnum
import io.rebble.libpebblecommon.database.entity.isBacklightColorScheduleHelperPref
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.timeline.TimelineColor
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

// Snap the notification timeout slider to 30-second increments (20 stops across 0..600s)
// so the displayed MM:SS value is always a clean :00 or :30.
private const val NOTIFICATION_TIMEOUT_STEP_COUNT = 19

@Composable
fun watchPrefs(
    hasDayNightBacklightSupport: Boolean,
    weatherScheduleAvailable: Boolean,
    useWeatherSchedule: Boolean,
    onUseWeatherScheduleChanged: (Boolean) -> Unit,
): List<SettingsItem> {
    val libPebble = rememberLibPebble()
    val settings by libPebble.watchPrefs.collectAsState(emptyList())
    val quickLaunchOptions = quickLaunchOptions(libPebble)
    val mapped = remember(
        settings,
        quickLaunchOptions,
        hasDayNightBacklightSupport,
        weatherScheduleAvailable,
        useWeatherSchedule,
        onUseWeatherScheduleChanged,
    ) {
        val settingsByPref = settings.associateBy { it.pref }
        val manualTimezone = preference(settingsByPref, BoolWatchPref.TimezoneSourceIsManual).valueOrDefault()
        val weatherBacklightScheduleAvailable = weatherScheduleAvailable && !manualTimezone
        settings.mapNotNull { item ->
            if (item.pref.isBacklightColorScheduleHelperPref()) {
                return@mapNotNull null
            }
            when (val pref = item.pref) {
                is BoolWatchPref -> booleanPref(pref.castParent(item), libPebble)
                is EnumWatchPref -> enumPref(pref.castParent(item), libPebble)
                is QuicklaunchWatchPref -> quicklaunchPref(pref.castParent(item), libPebble, quickLaunchOptions)
                is ColorWatchPref -> colorPref(pref.castParent(item), libPebble)
                is RgbColorWatchPref -> {
                    if (pref == RgbColorWatchPref.BacklightColor && hasDayNightBacklightSupport) {
                        backlightColorCompositePref(
                            dayColor = preference(settingsByPref, RgbColorWatchPref.BacklightColor),
                            nightColor = preference(settingsByPref, RgbColorWatchPref.BacklightColorNight),
                            enabled = preference(settingsByPref, BoolWatchPref.BacklightColorDayNightEnabled),
                            sunrise = preference(settingsByPref, NumberWatchPref.BacklightColorSunriseMinute),
                            sunset = preference(settingsByPref, NumberWatchPref.BacklightColorSunsetMinute),
                            weatherScheduleAvailable = weatherBacklightScheduleAvailable,
                            useWeatherSchedule = useWeatherSchedule,
                            onUseWeatherScheduleChanged = onUseWeatherScheduleChanged,
                            libPebble = libPebble,
                        )
                    } else {
                        rgbColorPref(pref.castParent(item), libPebble)
                    }
                }
                is NumberWatchPref -> numberPref(pref.castParent(item), libPebble)
            }
        }
    }
    val showConfirmReset = remember { mutableStateOf(false) }
    ConfirmDialog(
        show = showConfirmReset,
        title = "Reset To Defaults?",
        text = "Reset all settings to defaults",
        onConfirm = {
            settings.forEach { setting ->
                if (setting.value != setting.pref.defaultValue) {
                    @Suppress("UNCHECKED_CAST")
                    val pref = setting.pref as WatchPref<Any?>
                    libPebble.setWatchPref(WatchPreference(pref, pref.defaultValue))
                }
            }
        },
        confirmText = "Reset",
    )
    val reset = basicSettingsActionItem(
        title = "Reset To Defaults",
        topLevelType = TopLevelType.Watch,
        section = Section.Defaults,
        action = {
            showConfirmReset.value = true
        },
        description = "Reset all watch settings to defaults",
    )
    return listOf(reset) + mapped
}

fun WatchPref<*>.section(): Section = when (this) {
    BoolWatchPref.TimezoneSourceIsManual -> Section.Time
    BoolWatchPref.Clock24h -> Section.Time
    BoolWatchPref.StandbyMode -> Section.Other
    BoolWatchPref.LeftHandedMode -> Section.Display
    BoolWatchPref.Backlight -> Section.Display
    BoolWatchPref.AmbientLightSensor -> Section.Display
    BoolWatchPref.BacklightMotion -> Section.Display
    BoolWatchPref.BacklightColorDayNightEnabled -> Section.Display
    BoolWatchPref.DynamicBacklightIntensity -> Section.Display
    BoolWatchPref.LanguageEnglish -> Section.Other
//    ColorWatchPref.SettingsMenuHighlightColor -> Section.Display
//    ColorWatchPref.AppMenuHighlightColor -> Section.Display
    EnumWatchPref.TextSize -> Section.Notifications
    EnumWatchPref.MotionSensitivity -> Section.Display
    EnumWatchPref.BacklightIntensity -> Section.Display
    EnumWatchPref.BacklightTouch -> Section.Display
    RgbColorWatchPref.BacklightColor -> Section.Display
    RgbColorWatchPref.BacklightColorNight -> Section.Display
    NumberWatchPref.BacklightTimeoutMs -> Section.Display
    NumberWatchPref.AmbientLightThreshold -> Section.Display
    NumberWatchPref.DynamicBacklightMinThreshold -> Section.Display
    NumberWatchPref.BacklightColorSunriseMinute -> Section.Display
    NumberWatchPref.BacklightColorSunsetMinute -> Section.Display
    QuicklaunchWatchPref.QlUp -> Section.QuickLaunch
    QuicklaunchWatchPref.QlDown -> Section.QuickLaunch
    QuicklaunchWatchPref.QlComboBackUp -> Section.QuickLaunch
    QuicklaunchWatchPref.QlComboUpDown -> Section.QuickLaunch
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
    BoolWatchPref.MenuScrollWrapAround -> Section.Display
    EnumWatchPref.MenuScrollVibe -> Section.Display
    BoolWatchPref.QuietTimeMotionBacklight -> Section.QuietTime
    BoolWatchPref.MusicShowVolumeControls -> Section.Music
    BoolWatchPref.MusicShowProgressBar -> Section.Music
}

private fun <T> preference(
    settingsByPref: Map<WatchPref<*>, WatchPreference<*>>,
    pref: WatchPref<T>,
): WatchPreference<T> {
    val item = settingsByPref[pref] ?: return WatchPreference(pref, null)
    return pref.castParent(item)
}

private fun numberPref(item: WatchPreference<Long>, libPebble: LibPebble): SettingsItem {
    val pref = item.pref as NumberWatchPref
    return when (pref) {
        NumberWatchPref.BacklightTimeoutMs -> {
            basicSettingsNumberSecondsItem(
                pref = pref,
                item = item,
                libPebble = libPebble,
                valueFormatter = { seconds -> "$seconds seconds" },
            )
        }
        NumberWatchPref.NotificationTimeoutMs -> {
            basicSettingsNumberSecondsItem(
                pref = pref,
                item = item,
                libPebble = libPebble,
                valueFormatter = { seconds ->
                    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                },
                steps = NOTIFICATION_TIMEOUT_STEP_COUNT,
            )
        }
        else -> basicSettingsNumberItem(
            id = pref.id,
            title = pref.displayName,
            description = pref.description,
            topLevelType = TopLevelType.Watch,
            section = pref.section(),
            value = item.valueOrDefault(),
            min = pref.min,
            max = pref.max,
            onValueChange = {
                libPebble.setWatchPref(item.copy(value = it))
            },
            isDebugSetting = pref.isDebugSetting,
            defaultValue = pref.defaultValue,
            unit = pref.unit,
        )
    }
}

private fun basicSettingsNumberSecondsItem(
    pref: NumberWatchPref,
    item: WatchPreference<Long>,
    libPebble: LibPebble,
    valueFormatter: (Long) -> String,
    steps: Int? = null,
): SettingsItem = basicSettingsNumberItem(
    id = pref.id,
    title = pref.displayName,
    description = pref.description,
    topLevelType = TopLevelType.Watch,
    section = pref.section(),
    value = item.valueOrDefault().milliseconds.inWholeSeconds,
    min = pref.min.milliseconds.inWholeSeconds.toInt(),
    max = pref.max.milliseconds.inWholeSeconds.toInt(),
    onValueChange = {
        libPebble.setWatchPref(item.copy(value = it.seconds.inWholeMilliseconds))
    },
    isDebugSetting = pref.isDebugSetting,
    defaultValue = pref.defaultValue.milliseconds.inWholeSeconds,
    unit = "",
    valueFormatter = valueFormatter,
    steps = steps,
)

private fun colorPref(item: WatchPreference<TimelineColor>, libPebble: LibPebble): SettingsItem {
    val pref = item.pref as ColorWatchPref
    val default = item.valueOrDefault()
    return SettingsItem(
        id = pref.id,
        title = pref.displayName,
        topLevelType = TopLevelType.Watch,
        section = pref.section(),
        item = {
            ListItem(
                headlineContent = {
                    Text(pref.displayName)
                },
                supportingContent = {
                    Column {
                        pref.description?.let { description ->
                            Text(description, fontSize = 11.sp)
                        }
                        SelectColorOrNone(
                            currentColorName = default.identifier,
                            onChangeColor = { color ->
                                libPebble.setWatchPref(item.copy(value = color))
                            },
                            availableColors = pref.availableColors,
                            defaultToListTab = true,
                        )
                    }
                },
                shadowElevation = 2.dp,
            )
        },
        isDebugSetting = pref.isDebugSetting,
    )
}

private fun booleanPref(item: WatchPreference<Boolean>, libPebble: LibPebble): SettingsItem {
    return basicSettingsToggleItem(
        id = item.pref.id,
        title = item.pref.displayName,
        description = item.pref.description,
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
        id = pref.id,
        title = pref.displayName,
        description = pref.description,
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

private fun rgbColorPref(item: WatchPreference<UInt>, libPebble: LibPebble): SettingsItem {
    val pref = item.pref as RgbColorWatchPref
    return SettingsItem(
        id = pref.id,
        title = pref.displayName,
        topLevelType = TopLevelType.Watch,
        section = pref.section(),
        item = {
            ListItem(
                headlineContent = { Text(pref.displayName) },
                supportingContent = {
                    Column {
                        pref.description?.let { description ->
                            Text(description, fontSize = 11.sp)
                        }
                        SelectRgbColor(
                            currentRgb = item.valueOrDefault(),
                            defaultRgb = pref.defaultValue,
                            presets = pref.presets,
                            onChangeColor = { rgb ->
                                libPebble.setWatchPref(item.copy(value = rgb))
                            },
                        )
                    }
                },
            )
        },
        isDebugSetting = pref.isDebugSetting,
    )
}

private fun backlightColorCompositePref(
    dayColor: WatchPreference<UInt>,
    nightColor: WatchPreference<UInt>,
    enabled: WatchPreference<Boolean>,
    sunrise: WatchPreference<Long>,
    sunset: WatchPreference<Long>,
    weatherScheduleAvailable: Boolean,
    useWeatherSchedule: Boolean,
    onUseWeatherScheduleChanged: (Boolean) -> Unit,
    libPebble: LibPebble,
): SettingsItem {
    val dayPref = dayColor.pref as RgbColorWatchPref
    val nightPref = nightColor.pref as RgbColorWatchPref
    val isEnabled = enabled.valueOrDefault()
    val weatherScheduleChecked = useWeatherSchedule && weatherScheduleAvailable
    return SettingsItem(
        id = dayPref.id,
        title = dayPref.displayName,
        topLevelType = TopLevelType.Watch,
        section = Section.Display,
        item = {
            ListItem(
                headlineContent = { Text("Backlight Color") },
                supportingContent = {
                    Column {
                        Text(
                            "LED color used when the backlight is on, unless over-ridden by an app.",
                            fontSize = 11.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        libPebble.setWatchPref(sunrise.copy(value = sunrise.valueOrDefault()))
                                        libPebble.setWatchPref(sunset.copy(value = sunset.valueOrDefault()))
                                        if (weatherScheduleChecked) {
                                            onUseWeatherScheduleChanged(true)
                                        }
                                    }
                                    libPebble.setWatchPref(enabled.copy(value = checked))
                                },
                            )
                            Text(
                                BoolWatchPref.BacklightColorDayNightEnabled.displayName,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (isEnabled) {
                            SelectRgbColor(
                                currentRgb = dayColor.valueOrDefault(),
                                defaultRgb = dayPref.defaultValue,
                                presets = dayPref.presets,
                                label = "Day",
                                onChangeColor = { rgb ->
                                    libPebble.setWatchPref(dayColor.copy(value = rgb))
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SelectRgbColor(
                                currentRgb = nightColor.valueOrDefault(),
                                defaultRgb = nightPref.defaultValue,
                                presets = nightPref.presets,
                                label = "Night",
                                onChangeColor = { rgb ->
                                    libPebble.setWatchPref(nightColor.copy(value = rgb))
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            WeatherScheduleCheckbox(
                                checked = weatherScheduleChecked,
                                enabled = weatherScheduleAvailable,
                                onCheckedChange = onUseWeatherScheduleChanged,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SelectTimeOfDay(
                                label = "Sunrise",
                                minuteOfDay = sunrise.valueOrDefault(),
                                enabled = !weatherScheduleChecked,
                                onChangeMinute = { minute ->
                                    libPebble.setWatchPref(sunrise.copy(value = minute))
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SelectTimeOfDay(
                                label = "Sunset",
                                minuteOfDay = sunset.valueOrDefault(),
                                enabled = !weatherScheduleChecked,
                                onChangeMinute = { minute ->
                                    libPebble.setWatchPref(sunset.copy(value = minute))
                                },
                            )
                        } else {
                            SelectRgbColor(
                                currentRgb = dayColor.valueOrDefault(),
                                defaultRgb = dayPref.defaultValue,
                                presets = dayPref.presets,
                                label = "Color",
                                onChangeColor = { rgb ->
                                    libPebble.setWatchPref(dayColor.copy(value = rgb))
                                },
                            )
                        }
                    }
                },
            )
        },
        isDebugSetting = dayPref.isDebugSetting,
    )
}

@Composable
private fun WeatherScheduleCheckbox(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val label = if (enabled) {
        "Use local sunrise and sunset"
    } else {
        "Use local sunrise and sunset (requires Weather enabled in Settings)"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Text(
            label,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SelectTimeOfDay(
    label: String,
    minuteOfDay: Long,
    enabled: Boolean,
    onChangeMinute: (Long) -> Unit,
) {
    val minute = minuteOfDay.coerceIn(0, 1439)
    val hour = (minute / 60).toInt()
    val minutePart = (minute % 60).toInt()
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    ListItem(
        headlineContent = { Text(label, color = contentColor) },
        supportingContent = {
            Text(formatBacklightScheduleMinute(minute), color = contentColor)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimePartDropdown(
                    value = hour,
                    values = 0..23,
                    enabled = enabled,
                    onValueSelected = { selectedHour ->
                        onChangeMinute((selectedHour * 60 + minutePart).toLong())
                    },
                )
                Text(":", color = contentColor)
                TimePartDropdown(
                    value = minutePart,
                    values = 0..59,
                    enabled = enabled,
                    onValueSelected = { selectedMinute ->
                        onChangeMinute((hour * 60 + selectedMinute).toLong())
                    },
                )
            }
        },
    )
}

@Composable
private fun TimePartDropdown(
    value: Int,
    values: IntRange,
    enabled: Boolean,
    onValueSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            enabled = enabled,
            onClick = { expanded = true },
        ) {
            Text(value.toString().padStart(2, '0'))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString().padStart(2, '0')) },
                    onClick = {
                        expanded = false
                        onValueSelected(option)
                    },
                )
            }
        }
    }
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
        limit = 100,
    ).map { apps ->
        apps.filter { app -> app.isSynced() }
    }.collectAsState(emptyList())
    return remember(installedApps) {
        listOf(QuickLaunchOption(null, "None")) +
                QuickLaunchOption(QUIET_TIME_TOGGLE_UUID, "Quiet Time") +
                QuickLaunchOption(BACKLIGHT_UUID, "Backlight") +
                QuickLaunchOption(MOTION_BACKLIGHT_UUID, "Motion Backlight") +
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
        id = item.pref.id,
        title = item.pref.displayName,
        description = item.pref.description,
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
