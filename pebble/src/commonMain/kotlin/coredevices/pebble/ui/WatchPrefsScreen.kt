package coredevices.pebble.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
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
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.weather.WeatherFetcher
import coredevices.ui.ConfirmDialog
import coredevices.util.CoreConfigHolder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_SHORTCUTS
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BacklightPresetMode
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.ColorWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.NumberWatchPref
import io.rebble.libpebblecommon.database.entity.QuickLaunchSetting
import io.rebble.libpebblecommon.database.entity.QuicklaunchWatchPref
import io.rebble.libpebblecommon.database.entity.QuietTimeSchedule
import io.rebble.libpebblecommon.database.entity.RgbColorWatchPref
import io.rebble.libpebblecommon.database.entity.ScheduleWatchPref
import io.rebble.libpebblecommon.database.entity.WatchPref
import io.rebble.libpebblecommon.database.entity.WatchPrefEnum
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.timeline.TimelineColor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

// Snap the notification timeout slider to 15-second increments (40 stops across 15..600s)
// so the displayed MM:SS value is always a clean quarter minute.
private const val NOTIFICATION_TIMEOUT_STEP_COUNT = 38

// The watch overwrites these whenever a non-Advanced backlight preset is set, so showing them
// under a preset would offer settings the watch immediately reverts.
private val PRESET_MANAGED_BACKLIGHT_PREFS = setOf<WatchPref<*>>(
    BoolWatchPref.AmbientLightSensor,
    BoolWatchPref.BacklightMotion,
    EnumWatchPref.BacklightIntensity,
    EnumWatchPref.DynamicBacklightMode,
    EnumWatchPref.BacklightTouch,
    NumberWatchPref.BacklightTimeoutMs,
)

internal fun List<WatchPreference<*>>.hidePresetManagedBacklightPrefs(): List<WatchPreference<*>> {
    val preset = firstOrNull { it.pref == EnumWatchPref.BacklightPreset }?.valueOrDefault()
    return if (preset == BacklightPresetMode.Advanced) this
    else filterNot { it.pref in PRESET_MANAGED_BACKLIGHT_PREFS }
}

// Rendered inside the single "Backlight Color" item (backlightColorPref), not loose in the list.
private val BACKLIGHT_COLOR_SCHEDULE_PREFS = setOf<WatchPref<*>>(
    BoolWatchPref.BacklightColorDayNightEnabled,
    RgbColorWatchPref.BacklightColorNight,
    NumberWatchPref.BacklightColorSunriseMinute,
    NumberWatchPref.BacklightColorSunsetMinute,
)

internal fun List<WatchPreference<*>>.hideFoldedBacklightColorPrefs(): List<WatchPreference<*>> =
    filterNot { it.pref in BACKLIGHT_COLOR_SCHEDULE_PREFS }

private val SCHEDULE_HOURS = mapOf<WatchPref<*>, WatchPref<*>>(
    BoolWatchPref.QuietTimeWeekdayScheduleEnabled to ScheduleWatchPref.QuietTimeWeekdaySchedule,
    BoolWatchPref.QuietTimeWeekendScheduleEnabled to ScheduleWatchPref.QuietTimeWeekendSchedule,
)

/** Schedule hours sit directly under the toggle they belong to, and only while it is on. */
internal fun List<WatchPreference<*>>.groupQuietTimeScheduleHours(): List<WatchPreference<*>> {
    val hours = filter { it.pref is ScheduleWatchPref }
    return filterNot { it.pref is ScheduleWatchPref }.flatMap { item ->
        val pref = SCHEDULE_HOURS[item.pref]?.takeIf { item.valueOrDefault() == true }
        listOfNotNull(item, hours.firstOrNull { it.pref == pref })
    }
}

@Composable
fun watchPrefs(): List<SettingsItem> {
    val libPebble = rememberLibPebble()
    val coreConfigHolder = koinInject<CoreConfigHolder>()
    val settings by libPebble.watchPrefs.collectAsState(emptyList())
    val quickLaunchOptions = quickLaunchOptions(libPebble)
    val mapped = remember(settings, quickLaunchOptions) {
        settings.hidePresetManagedBacklightPrefs()
            .groupQuietTimeScheduleHours()
            .hideFoldedBacklightColorPrefs()
            .map { item ->
                when (val pref = item.pref) {
                    is BoolWatchPref -> booleanPref(pref.castParent(item), libPebble)
                    is EnumWatchPref -> enumPref(pref.castParent(item), libPebble)
                    is QuicklaunchWatchPref -> quicklaunchPref(pref.castParent(item), libPebble, quickLaunchOptions)
                    is ColorWatchPref -> colorPref(pref.castParent(item), libPebble)
                    is RgbColorWatchPref ->
                        if (pref == RgbColorWatchPref.BacklightColor) {
                            backlightColorPref(settings, libPebble, coreConfigHolder)
                        } else {
                            rgbColorPref(pref.castParent(item), libPebble)
                        }
                    is NumberWatchPref -> numberPref(pref.castParent(item), libPebble)
                    is ScheduleWatchPref -> schedulePref(pref.castParent(item), libPebble)
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
    RgbColorWatchPref.BacklightColorNight -> Section.Display
    NumberWatchPref.BacklightColorSunriseMinute -> Section.Display
    NumberWatchPref.BacklightColorSunsetMinute -> Section.Display
    EnumWatchPref.WindSpeed -> Section.Weather
//    ColorWatchPref.SettingsMenuHighlightColor -> Section.Display
//    ColorWatchPref.AppMenuHighlightColor -> Section.Display
    EnumWatchPref.SystemTextSize -> Section.Display
    EnumWatchPref.NotificationTextSize -> Section.Notifications
    EnumWatchPref.MotionSensitivity -> Section.Display
    EnumWatchPref.BacklightPreset -> Section.Display
    EnumWatchPref.BacklightIntensity -> Section.Display
    EnumWatchPref.DynamicBacklightMode -> Section.Display
    EnumWatchPref.BacklightTouch -> Section.Display
    RgbColorWatchPref.BacklightColor -> Section.Display
    NumberWatchPref.BacklightTimeoutMs -> Section.Display
    NumberWatchPref.AmbientLightThreshold -> Section.Display
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
    EnumWatchPref.TimelineQuickViewUnsupportedFaces -> Section.Timeline
    EnumWatchPref.NotificationFilter -> Section.Notifications
    EnumWatchPref.QuietTimeInterruptions -> Section.QuietTime
    EnumWatchPref.QuietTimeShowNotifications -> Section.QuietTime
    EnumWatchPref.LegacyVibeIntensity -> Section.Notifications
    EnumWatchPref.VibeScoreNotifications -> Section.Notifications
    EnumWatchPref.VibeScoreCalls -> Section.Notifications
    EnumWatchPref.VibeScoreAlarms -> Section.Notifications
    BoolWatchPref.QuietTimeManuallyEnabled -> Section.QuietTime
    BoolWatchPref.CalendarAwareQuietTime -> Section.QuietTime
    BoolWatchPref.QuietTimeWeekdayScheduleEnabled -> Section.QuietTime
    BoolWatchPref.QuietTimeWeekendScheduleEnabled -> Section.QuietTime
    ScheduleWatchPref.QuietTimeWeekdaySchedule -> Section.QuietTime
    ScheduleWatchPref.QuietTimeWeekendSchedule -> Section.QuietTime
    BoolWatchPref.AlternativeNotificationStyle -> Section.Notifications
    BoolWatchPref.NotificationVibeDelay -> Section.Notifications
    BoolWatchPref.NotificationBacklight -> Section.Notifications
    BoolWatchPref.RespectPhoneSilence -> Section.Notifications
    NumberWatchPref.NotificationTimeoutMs -> Section.Notifications
    BoolWatchPref.MenuScrollWrapAround -> Section.Display
    EnumWatchPref.MenuScrollVibe -> Section.Display
    BoolWatchPref.QuietTimeMotionBacklight -> Section.QuietTime
    BoolWatchPref.QuietTimeTouchBacklight -> Section.QuietTime
    BoolWatchPref.QuietTimeNotificationBacklight -> Section.QuietTime
    BoolWatchPref.QuietTimeAutoDismiss -> Section.QuietTime
    BoolWatchPref.MusicShowVolumeControls -> Section.Music
    BoolWatchPref.MusicShowProgressBar -> Section.Music
    BoolWatchPref.MusicShowAlbumArt -> Section.Music
}

fun WatchPref<*>.topLevelType(): TopLevelType = when (this) {
    // Weather is configured on the phone, even though the unit it sets is a watch pref.
    EnumWatchPref.WindSpeed -> TopLevelType.Phone
    else -> TopLevelType.Watch
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
            topLevelType = pref.topLevelType(),
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

internal fun formatMinuteOfDay(minute: Long): String {
    val m = minute.coerceIn(0, 1439)
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
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
    topLevelType = pref.topLevelType(),
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

private fun schedulePref(
    item: WatchPreference<QuietTimeSchedule>,
    libPebble: LibPebble,
): SettingsItem {
    val pref = item.pref as ScheduleWatchPref
    return SettingsItem(
        id = pref.id,
        title = pref.displayName,
        topLevelType = pref.topLevelType(),
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
                        SelectTimeRange(
                            schedule = item.valueOrDefault(),
                            onChangeSchedule = { schedule ->
                                libPebble.setWatchPref(item.copy(value = schedule))
                            },
                        )
                    }
                },
                shadowElevation = 2.dp,
            )
        },
        isDebugSetting = pref.isDebugSetting,
    )
}

private enum class ScheduleEdge(val title: String) {
    From("Start time"),
    To("End time"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectTimeRange(
    schedule: QuietTimeSchedule,
    onChangeSchedule: (QuietTimeSchedule) -> Unit,
) {
    var editing by remember { mutableStateOf<ScheduleEdge?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("From", fontSize = 14.sp)
        TextButton(onClick = { editing = ScheduleEdge.From }) {
            Text(schedule.fromTime())
        }
        Text("to", fontSize = 14.sp)
        TextButton(onClick = { editing = ScheduleEdge.To }) {
            Text(schedule.toTime())
        }
    }
    val edge = editing ?: return
    val state = rememberTimePickerState(
        initialHour = if (edge == ScheduleEdge.From) schedule.fromHour else schedule.toHour,
        initialMinute = if (edge == ScheduleEdge.From) schedule.fromMinute else schedule.toMinute,
    )
    TimePickerDialog(
        onDismissRequest = { editing = null },
        title = { Text(edge.title) },
        dismissButton = {
            TextButton(onClick = { editing = null }) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                editing = null
                onChangeSchedule(
                    when (edge) {
                        ScheduleEdge.From -> schedule.copy(
                            fromHour = state.hour,
                            fromMinute = state.minute,
                        )
                        ScheduleEdge.To -> schedule.copy(
                            toHour = state.hour,
                            toMinute = state.minute,
                        )
                    }
                )
            }) {
                Text("Set")
            }
        },
    ) {
        TimePicker(state = state)
    }
}

private fun colorPref(item: WatchPreference<TimelineColor>, libPebble: LibPebble): SettingsItem {
    val pref = item.pref as ColorWatchPref
    val default = item.valueOrDefault()
    return SettingsItem(
        id = pref.id,
        title = pref.displayName,
        topLevelType = pref.topLevelType(),
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
        topLevelType = item.pref.topLevelType(),
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
        topLevelType = pref.topLevelType(),
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
        topLevelType = pref.topLevelType(),
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

private fun <T> List<WatchPreference<*>>.preference(pref: WatchPref<T>): WatchPreference<T> =
    pref.castParent(firstOrNull { it.pref == pref } ?: WatchPreference(pref, null))

/**
 * Day colour, the night toggle, and — only while night colour is on — night colour, the
 * automatic-times toggle and (only while that is off) manual sunrise/sunset pickers, in one item.
 */
private fun backlightColorPref(
    settings: List<WatchPreference<*>>,
    libPebble: LibPebble,
    coreConfigHolder: CoreConfigHolder,
): SettingsItem {
    val dayPref = RgbColorWatchPref.BacklightColor
    val nightPref = RgbColorWatchPref.BacklightColorNight
    val day = settings.preference(dayPref)
    val night = settings.preference(nightPref)
    val enabled = settings.preference(BoolWatchPref.BacklightColorDayNightEnabled)
    val sunrise = settings.preference(NumberWatchPref.BacklightColorSunriseMinute)
    val sunset = settings.preference(NumberWatchPref.BacklightColorSunsetMinute)
    return SettingsItem(
        id = dayPref.id,
        title = dayPref.displayName,
        topLevelType = dayPref.topLevelType(),
        section = dayPref.section(),
        item = {
            val coreConfig by coreConfigHolder.config.collectAsState()
            val autoSunriseSunset = coreConfig.backlightColorAutoSunriseSunset
            val dayNightOn = enabled.valueOrDefault()
            ListItem(
                headlineContent = { Text(dayPref.displayName) },
                supportingContent = {
                    Column {
                        dayPref.description?.let { Text(it, fontSize = 11.sp) }
                        SelectRgbColor(
                            currentRgb = day.valueOrDefault(),
                            defaultRgb = dayPref.defaultValue,
                            presets = dayPref.presets,
                            label = if (dayNightOn) "Day" else "Color",
                            onChangeColor = { libPebble.setWatchPref(day.copy(value = it)) },
                        )
                        BacklightToggleRow(
                            label = BoolWatchPref.BacklightColorDayNightEnabled.displayName,
                            checked = dayNightOn,
                            onCheckedChange = { libPebble.setWatchPref(enabled.copy(value = it)) },
                        )
                        if (dayNightOn) {
                            SelectRgbColor(
                                currentRgb = night.valueOrDefault(),
                                defaultRgb = nightPref.defaultValue,
                                presets = nightPref.presets,
                                label = "Night",
                                onChangeColor = { libPebble.setWatchPref(night.copy(value = it)) },
                            )
                            val weatherFetcher = koinInject<WeatherFetcher>()
                            BacklightToggleRow(
                                label = "Automatic Sunrise/Sunset",
                                checked = autoSunriseSunset,
                                onCheckedChange = { on ->
                                    coreConfigHolder.update(coreConfig.copy(backlightColorAutoSunriseSunset = on))
                                    if (on) GlobalScope.launch { weatherFetcher.fetchWeather(this) }
                                },
                            )
                            if (!autoSunriseSunset) {
                                SelectTimeOfDay("Sunrise", sunrise.valueOrDefault()) {
                                    libPebble.setWatchPref(sunrise.copy(value = it))
                                }
                                SelectTimeOfDay("Sunset", sunset.valueOrDefault()) {
                                    libPebble.setWatchPref(sunset.copy(value = it))
                                }
                            }
                        }
                    }
                },
            )
        },
        isDebugSetting = dayPref.isDebugSetting,
    )
}

@Composable
private fun BacklightToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectTimeOfDay(label: String, minuteOfDay: Long, onChange: (Long) -> Unit) {
    val minute = minuteOfDay.coerceIn(0, 1439).toInt()
    var editing by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            TextButton(onClick = { editing = true }) { Text(formatMinuteOfDay(minute.toLong())) }
        },
    )
    if (!editing) return
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60)
    TimePickerDialog(
        onDismissRequest = { editing = false },
        title = { Text(label) },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = {
                editing = false
                onChange((state.hour * 60 + state.minute).toLong())
            }) { Text("Set") }
        },
    ) {
        TimePicker(state = state)
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
                SYSTEM_SHORTCUTS.map { (uuid, name) -> QuickLaunchOption(uuid, name) } +
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
        topLevelType = item.pref.topLevelType(),
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
