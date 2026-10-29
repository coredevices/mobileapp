package coredevices.pebble.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
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

private val BACKLIGHT_SUN_TIMES = setOf<WatchPref<*>>(
    NumberWatchPref.BacklightColorSunriseMinute,
    NumberWatchPref.BacklightColorSunsetMinute,
)

/** Night colour and its times only while night colour is on; manual times only while they aren't automatic. */
internal fun List<WatchPreference<*>>.hideUnusedBacklightColorPrefs(autoSunTimes: Boolean): List<WatchPreference<*>> {
    val night = firstOrNull { it.pref == BoolWatchPref.BacklightColorDayNightEnabled }?.valueOrDefault() == true
    return filterNot {
        (!night && (it.pref == RgbColorWatchPref.BacklightColorNight || it.pref in BACKLIGHT_SUN_TIMES)) ||
            (autoSunTimes && it.pref in BACKLIGHT_SUN_TIMES)
    }
}

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
    val coreConfig by coreConfigHolder.config.collectAsState()
    val weatherFetcher = koinInject<WeatherFetcher>()
    val quickLaunchOptions = quickLaunchOptions(libPebble)
    val mapped = remember(settings, quickLaunchOptions, coreConfig) {
        settings.inLayoutOrder()
            .hidePresetManagedBacklightPrefs()
            .groupQuietTimeScheduleHours()
            .hideUnusedBacklightColorPrefs(coreConfig.backlightColorAutoSunriseSunset)
            .flatMap { item ->
                val setting = when (val pref = item.pref) {
                    is BoolWatchPref -> booleanPref(pref.castParent(item), libPebble)
                    is EnumWatchPref -> enumPref(pref.castParent(item), libPebble)
                    is QuicklaunchWatchPref -> quicklaunchPref(pref.castParent(item), libPebble, quickLaunchOptions)
                    is ColorWatchPref -> colorPref(pref.castParent(item), libPebble)
                    is RgbColorWatchPref -> rgbColorPref(pref.castParent(item), libPebble)
                    is NumberWatchPref -> numberPref(pref.castParent(item), libPebble)
                    is ScheduleWatchPref -> schedulePref(pref.castParent(item), libPebble)
                }
                // Automatic sun times are a phone setting, shown with the night colour they serve.
                if (item.pref == RgbColorWatchPref.BacklightColorNight) {
                    listOf(setting, autoSunTimesItem(coreConfigHolder, weatherFetcher))
                } else {
                    listOf(setting)
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

internal class PrefLayout(
    val pref: WatchPref<*>,
    val section: Section,
    val group: SettingsGroup? = null,
    /** Its title under the group's heading. */
    val title: String? = null,
)

/** Every watch pref, in the order the settings screen shows them (WatchPrefLayoutTest checks none is missing). */
internal val WATCH_PREF_LAYOUT: List<PrefLayout> = listOf(
    PrefLayout(QuicklaunchWatchPref.QlUp, Section.QuickLaunch),
    PrefLayout(QuicklaunchWatchPref.QlDown, Section.QuickLaunch),
    PrefLayout(QuicklaunchWatchPref.QlSelect, Section.QuickLaunch),
    PrefLayout(QuicklaunchWatchPref.QlBack, Section.QuickLaunch),
    PrefLayout(QuicklaunchWatchPref.QlComboBackUp, Section.QuickLaunch),
    PrefLayout(QuicklaunchWatchPref.QlComboUpDown, Section.QuickLaunch),
    PrefLayout(QuicklaunchWatchPref.QlSingleClickUp, Section.QuickLaunch),
    PrefLayout(QuicklaunchWatchPref.QlSingleClickDown, Section.QuickLaunch),

    PrefLayout(EnumWatchPref.SystemTextSize, Section.Display),
    PrefLayout(BoolWatchPref.Backlight, Section.Display, SettingsGroup.Backlight, "Enabled"),
    PrefLayout(EnumWatchPref.BacklightPreset, Section.Display, SettingsGroup.Backlight, "Preset"),
    PrefLayout(EnumWatchPref.BacklightIntensity, Section.Display, SettingsGroup.Backlight, "Intensity"),
    PrefLayout(EnumWatchPref.DynamicBacklightMode, Section.Display, SettingsGroup.Backlight),
    PrefLayout(EnumWatchPref.BacklightTouch, Section.Display, SettingsGroup.Backlight),
    PrefLayout(NumberWatchPref.BacklightTimeoutMs, Section.Display, SettingsGroup.Backlight, "Timeout"),
    PrefLayout(BoolWatchPref.AmbientLightSensor, Section.Display, SettingsGroup.BacklightAmbient, "Enabled"),
    PrefLayout(NumberWatchPref.AmbientLightThreshold, Section.Display, SettingsGroup.BacklightAmbient, "Threshold"),
    PrefLayout(BoolWatchPref.BacklightMotion, Section.Display, SettingsGroup.BacklightMotion, "Enabled"),
    PrefLayout(EnumWatchPref.MotionSensitivity, Section.Display, SettingsGroup.BacklightMotion, "Sensitivity"),
    PrefLayout(RgbColorWatchPref.BacklightColor, Section.Display, SettingsGroup.BacklightColor, "Color"),
    PrefLayout(BoolWatchPref.BacklightColorDayNightEnabled, Section.Display, SettingsGroup.BacklightColor, "Change Color at Night"),
    PrefLayout(RgbColorWatchPref.BacklightColorNight, Section.Display, SettingsGroup.BacklightColor, "Night Color"),
    PrefLayout(NumberWatchPref.BacklightColorSunriseMinute, Section.Display, SettingsGroup.BacklightColor, "Sunrise"),
    PrefLayout(NumberWatchPref.BacklightColorSunsetMinute, Section.Display, SettingsGroup.BacklightColor, "Sunset"),
    PrefLayout(BoolWatchPref.MenuScrollWrapAround, Section.Display, SettingsGroup.MenuScrolling, "Wrap Around"),
    PrefLayout(EnumWatchPref.MenuScrollVibe, Section.Display, SettingsGroup.MenuScrolling, "Vibration"),
    PrefLayout(BoolWatchPref.LeftHandedMode, Section.Display),

    PrefLayout(EnumWatchPref.WindSpeed, Section.Weather),

    PrefLayout(EnumWatchPref.LegacyVibeIntensity, Section.Notifications, SettingsGroup.Vibration, "System Intensity"),
    PrefLayout(EnumWatchPref.VibeScoreNotifications, Section.Notifications, SettingsGroup.Vibration, "Notifications"),
    PrefLayout(EnumWatchPref.VibeScoreCalls, Section.Notifications, SettingsGroup.Vibration, "Incoming Calls"),
    PrefLayout(EnumWatchPref.VibeScoreAlarms, Section.Notifications, SettingsGroup.Vibration, "Alarms"),
    PrefLayout(BoolWatchPref.NotificationVibeDelay, Section.Notifications, SettingsGroup.Vibration, "Delay Until Notification Is Visible"),
    PrefLayout(BoolWatchPref.NotificationBacklight, Section.Notifications),
    PrefLayout(BoolWatchPref.RespectPhoneSilence, Section.Notifications),
    PrefLayout(EnumWatchPref.NotificationTextSize, Section.Notifications),
    PrefLayout(EnumWatchPref.NotificationFilter, Section.Notifications),
    PrefLayout(NumberWatchPref.NotificationTimeoutMs, Section.Notifications),
    PrefLayout(BoolWatchPref.AlternativeNotificationStyle, Section.Notifications),

    PrefLayout(BoolWatchPref.TimezoneSourceIsManual, Section.Time),
    PrefLayout(BoolWatchPref.Clock24h, Section.Time),

    PrefLayout(BoolWatchPref.TimelineQuickViewEnabled, Section.Timeline, SettingsGroup.QuickView, "Show Upcoming Events"),
    PrefLayout(NumberWatchPref.TimelineQuickViewMinsBefore, Section.Timeline, SettingsGroup.QuickView, "Minutes Before Event"),
    PrefLayout(EnumWatchPref.TimelineQuickViewUnsupportedFaces, Section.Timeline, SettingsGroup.QuickView, "Unsupported Faces"),

    PrefLayout(BoolWatchPref.QuietTimeManuallyEnabled, Section.QuietTime),
    PrefLayout(BoolWatchPref.CalendarAwareQuietTime, Section.QuietTime),
    PrefLayout(BoolWatchPref.QuietTimeWeekdayScheduleEnabled, Section.QuietTime),
    PrefLayout(ScheduleWatchPref.QuietTimeWeekdaySchedule, Section.QuietTime),
    PrefLayout(BoolWatchPref.QuietTimeWeekendScheduleEnabled, Section.QuietTime),
    PrefLayout(ScheduleWatchPref.QuietTimeWeekendSchedule, Section.QuietTime),
    PrefLayout(BoolWatchPref.QuietTimeMotionBacklight, Section.QuietTime),
    PrefLayout(BoolWatchPref.QuietTimeTouchBacklight, Section.QuietTime),
    PrefLayout(BoolWatchPref.QuietTimeNotificationBacklight, Section.QuietTime),
    PrefLayout(BoolWatchPref.QuietTimeAutoDismiss, Section.QuietTime),
    PrefLayout(EnumWatchPref.QuietTimeInterruptions, Section.QuietTime),
    PrefLayout(EnumWatchPref.QuietTimeShowNotifications, Section.QuietTime),

    PrefLayout(BoolWatchPref.MusicShowVolumeControls, Section.Music),
    PrefLayout(BoolWatchPref.MusicShowProgressBar, Section.Music),
    PrefLayout(BoolWatchPref.MusicShowAlbumArt, Section.Music),

    PrefLayout(BoolWatchPref.StandbyMode, Section.Other),
)

private val LAYOUT = WATCH_PREF_LAYOUT.associateBy { it.pref }
private val POSITION = WATCH_PREF_LAYOUT.withIndex().associate { (index, layout) -> layout.pref to index }

fun WatchPref<*>.section(): Section = LAYOUT[this]?.section ?: Section.Other

fun WatchPref<*>.settingsTitle(): String = LAYOUT[this]?.title ?: displayName

fun WatchPref<*>.settingsGroup(): SettingsGroup? = LAYOUT[this]?.group

internal fun List<WatchPreference<*>>.inLayoutOrder(): List<WatchPreference<*>> =
    sortedBy { POSITION[it.pref] ?: Int.MAX_VALUE }

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
        NumberWatchPref.BacklightColorSunriseMinute, NumberWatchPref.BacklightColorSunsetMinute -> timeOfDayPref(item, libPebble)
        else -> basicSettingsNumberItem(
            id = pref.id,
            title = pref.settingsTitle(),
            description = pref.description,
            topLevelType = pref.topLevelType(),
            section = pref.section(),
            group = pref.settingsGroup(),
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
    title = pref.settingsTitle(),
    description = pref.description,
    topLevelType = pref.topLevelType(),
    section = pref.section(),
    group = pref.settingsGroup(),
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
        title = pref.settingsTitle(),
        topLevelType = pref.topLevelType(),
        section = pref.section(),
        group = pref.settingsGroup(),
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
        title = pref.settingsTitle(),
        topLevelType = pref.topLevelType(),
        section = pref.section(),
        group = pref.settingsGroup(),
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
        title = item.pref.settingsTitle(),
        description = item.pref.description,
        topLevelType = item.pref.topLevelType(),
        section = item.pref.section(),
        group = item.pref.settingsGroup(),
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
        title = pref.settingsTitle(),
        description = pref.description,
        topLevelType = pref.topLevelType(),
        section = pref.section(),
        group = pref.settingsGroup(),
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
        title = pref.settingsTitle(),
        topLevelType = pref.topLevelType(),
        section = pref.section(),
        group = pref.settingsGroup(),
        item = {
            ListItem(
                headlineContent = { Text(pref.settingsTitle()) },
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

private fun autoSunTimesItem(coreConfigHolder: CoreConfigHolder, weatherFetcher: WeatherFetcher): SettingsItem {
    val pref = RgbColorWatchPref.BacklightColorNight
    val coreConfig = coreConfigHolder.config.value
    return basicSettingsToggleItem(
        title = "Automatic Sunrise/Sunset",
        description = "Use the sunrise and sunset times for your location",
        topLevelType = pref.topLevelType(),
        section = pref.section(),
        group = pref.settingsGroup(),
        checked = coreConfig.backlightColorAutoSunriseSunset,
        onCheckChanged = { on ->
            coreConfigHolder.update(coreConfig.copy(backlightColorAutoSunriseSunset = on))
            if (on) GlobalScope.launch { weatherFetcher.fetchWeather(this) }
        },
    )
}

private fun timeOfDayPref(item: WatchPreference<Long>, libPebble: LibPebble): SettingsItem {
    val pref = item.pref
    return SettingsItem(
        id = pref.id,
        title = pref.settingsTitle(),
        topLevelType = pref.topLevelType(),
        section = pref.section(),
        group = pref.settingsGroup(),
        item = {
            SelectTimeOfDay(pref.settingsTitle(), item.valueOrDefault()) {
                libPebble.setWatchPref(item.copy(value = it))
            }
        },
        isDebugSetting = pref.isDebugSetting,
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
        title = item.pref.settingsTitle(),
        description = item.pref.description,
        topLevelType = item.pref.topLevelType(),
        section = item.pref.section(),
        group = item.pref.settingsGroup(),
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
