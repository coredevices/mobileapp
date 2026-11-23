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
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.settings_prefs_minutes
import coreapp.pebble.generated.resources.watch_pref_wind_speed_desc
import coreapp.pebble.generated.resources.watch_pref_wind_speed
import coreapp.pebble.generated.resources.watch_pref_vibe_score_notifications
import coreapp.pebble.generated.resources.watch_pref_vibe_score_calls
import coreapp.pebble.generated.resources.watch_pref_vibe_score_alarms
import coreapp.pebble.generated.resources.watch_pref_timezone_source_is_manual_desc
import coreapp.pebble.generated.resources.watch_pref_timezone_source_is_manual
import coreapp.pebble.generated.resources.watch_pref_timeline_quick_view_unsupported_faces_desc
import coreapp.pebble.generated.resources.watch_pref_timeline_quick_view_unsupported_faces
import coreapp.pebble.generated.resources.watch_pref_timeline_quick_view_mins_before
import coreapp.pebble.generated.resources.watch_pref_timeline_quick_view_enabled_desc
import coreapp.pebble.generated.resources.watch_pref_timeline_quick_view_enabled
import coreapp.pebble.generated.resources.watch_pref_temperature_desc
import coreapp.pebble.generated.resources.watch_pref_temperature
import coreapp.pebble.generated.resources.watch_pref_system_text_size
import coreapp.pebble.generated.resources.watch_pref_standby_mode_desc
import coreapp.pebble.generated.resources.watch_pref_standby_mode
import coreapp.pebble.generated.resources.watch_pref_respect_phone_silence_desc
import coreapp.pebble.generated.resources.watch_pref_respect_phone_silence
import coreapp.pebble.generated.resources.watch_pref_quiet_time_weekend_schedule_enabled_desc
import coreapp.pebble.generated.resources.watch_pref_quiet_time_weekend_schedule_enabled
import coreapp.pebble.generated.resources.watch_pref_quiet_time_weekend_schedule
import coreapp.pebble.generated.resources.watch_pref_quiet_time_weekday_schedule_enabled_desc
import coreapp.pebble.generated.resources.watch_pref_quiet_time_weekday_schedule_enabled
import coreapp.pebble.generated.resources.watch_pref_quiet_time_weekday_schedule
import coreapp.pebble.generated.resources.watch_pref_quiet_time_touch_backlight_desc
import coreapp.pebble.generated.resources.watch_pref_quiet_time_touch_backlight
import coreapp.pebble.generated.resources.watch_pref_quiet_time_show_notifications
import coreapp.pebble.generated.resources.watch_pref_quiet_time_notification_backlight_desc
import coreapp.pebble.generated.resources.watch_pref_quiet_time_notification_backlight
import coreapp.pebble.generated.resources.watch_pref_quiet_time_motion_backlight_desc
import coreapp.pebble.generated.resources.watch_pref_quiet_time_motion_backlight
import coreapp.pebble.generated.resources.watch_pref_quiet_time_manually_enabled_desc
import coreapp.pebble.generated.resources.watch_pref_quiet_time_manually_enabled
import coreapp.pebble.generated.resources.watch_pref_quiet_time_interruptions
import coreapp.pebble.generated.resources.watch_pref_quiet_time_auto_dismiss_desc
import coreapp.pebble.generated.resources.watch_pref_quiet_time_auto_dismiss
import coreapp.pebble.generated.resources.watch_pref_ql_up
import coreapp.pebble.generated.resources.watch_pref_ql_single_click_up
import coreapp.pebble.generated.resources.watch_pref_ql_single_click_down
import coreapp.pebble.generated.resources.watch_pref_ql_select
import coreapp.pebble.generated.resources.watch_pref_ql_down
import coreapp.pebble.generated.resources.watch_pref_ql_combo_up_down
import coreapp.pebble.generated.resources.watch_pref_ql_combo_back_up
import coreapp.pebble.generated.resources.watch_pref_ql_back
import coreapp.pebble.generated.resources.watch_pref_notification_vibe_delay_desc
import coreapp.pebble.generated.resources.watch_pref_notification_vibe_delay
import coreapp.pebble.generated.resources.watch_pref_notification_timeout_ms_desc
import coreapp.pebble.generated.resources.watch_pref_notification_timeout_ms
import coreapp.pebble.generated.resources.watch_pref_notification_text_size
import coreapp.pebble.generated.resources.watch_pref_notification_filter
import coreapp.pebble.generated.resources.watch_pref_notification_dismissal_sync_desc
import coreapp.pebble.generated.resources.watch_pref_notification_dismissal_sync
import coreapp.pebble.generated.resources.watch_pref_notification_backlight_desc
import coreapp.pebble.generated.resources.watch_pref_notification_backlight
import coreapp.pebble.generated.resources.watch_pref_music_show_volume_controls
import coreapp.pebble.generated.resources.watch_pref_music_show_progress_bar
import coreapp.pebble.generated.resources.watch_pref_music_show_album_art
import coreapp.pebble.generated.resources.watch_pref_motion_sensitivity
import coreapp.pebble.generated.resources.watch_pref_menu_scroll_wrap_around_desc
import coreapp.pebble.generated.resources.watch_pref_menu_scroll_wrap_around
import coreapp.pebble.generated.resources.watch_pref_menu_scroll_vibe
import coreapp.pebble.generated.resources.watch_pref_legacy_vibe_intensity
import coreapp.pebble.generated.resources.watch_pref_left_handed_mode_desc
import coreapp.pebble.generated.resources.watch_pref_left_handed_mode
import coreapp.pebble.generated.resources.watch_pref_dynamic_backlight_mode_desc
import coreapp.pebble.generated.resources.watch_pref_dynamic_backlight_mode
import coreapp.pebble.generated.resources.watch_pref_clock24h
import coreapp.pebble.generated.resources.watch_pref_calendar_aware_quiet_time_desc
import coreapp.pebble.generated.resources.watch_pref_calendar_aware_quiet_time
import coreapp.pebble.generated.resources.watch_pref_backlight_touch_desc
import coreapp.pebble.generated.resources.watch_pref_backlight_touch
import coreapp.pebble.generated.resources.watch_pref_backlight_timeout_ms
import coreapp.pebble.generated.resources.watch_pref_backlight_preset_desc
import coreapp.pebble.generated.resources.watch_pref_backlight_preset
import coreapp.pebble.generated.resources.watch_pref_backlight_motion_desc
import coreapp.pebble.generated.resources.watch_pref_backlight_motion
import coreapp.pebble.generated.resources.watch_pref_backlight_intensity_desc
import coreapp.pebble.generated.resources.watch_pref_backlight_intensity
import coreapp.pebble.generated.resources.watch_pref_backlight_color_sunset_minute
import coreapp.pebble.generated.resources.watch_pref_backlight_color_sunrise_minute
import coreapp.pebble.generated.resources.watch_pref_backlight_color_night_desc
import coreapp.pebble.generated.resources.watch_pref_backlight_color_night
import coreapp.pebble.generated.resources.watch_pref_backlight_color_desc
import coreapp.pebble.generated.resources.watch_pref_backlight_color_day_night_enabled_desc
import coreapp.pebble.generated.resources.watch_pref_backlight_color_day_night_enabled
import coreapp.pebble.generated.resources.watch_pref_backlight_color
import coreapp.pebble.generated.resources.watch_pref_backlight
import coreapp.pebble.generated.resources.watch_pref_ambient_light_threshold
import coreapp.pebble.generated.resources.watch_pref_ambient_light_sensor_desc
import coreapp.pebble.generated.resources.watch_pref_ambient_light_sensor
import coreapp.pebble.generated.resources.watch_pref_alternative_notification_style
import coreapp.pebble.generated.resources.notifications
import coreapp.pebble.generated.resources.settings_color
import coreapp.pebble.generated.resources.settings_group_vibration
import coreapp.pebble.generated.resources.settings_prefs_alarms
import coreapp.pebble.generated.resources.settings_prefs_auto_sun_times
import coreapp.pebble.generated.resources.settings_prefs_auto_sun_times_desc
import coreapp.pebble.generated.resources.settings_prefs_change_color_at_night
import coreapp.pebble.generated.resources.settings_prefs_end_time
import coreapp.pebble.generated.resources.settings_prefs_from
import coreapp.pebble.generated.resources.settings_prefs_incoming_calls
import coreapp.pebble.generated.resources.settings_prefs_intensity
import coreapp.pebble.generated.resources.settings_prefs_minutes_before_event
import coreapp.pebble.generated.resources.settings_prefs_night_color
import coreapp.pebble.generated.resources.settings_prefs_preset
import coreapp.pebble.generated.resources.settings_prefs_reset
import coreapp.pebble.generated.resources.settings_prefs_reset_confirm_text
import coreapp.pebble.generated.resources.settings_prefs_reset_confirm_title
import coreapp.pebble.generated.resources.settings_prefs_reset_to_defaults
import coreapp.pebble.generated.resources.settings_prefs_reset_to_defaults_desc
import coreapp.pebble.generated.resources.settings_prefs_seconds
import coreapp.pebble.generated.resources.settings_prefs_sensitivity
import coreapp.pebble.generated.resources.settings_prefs_set
import coreapp.pebble.generated.resources.settings_prefs_show_upcoming_events
import coreapp.pebble.generated.resources.settings_prefs_start_time
import coreapp.pebble.generated.resources.settings_prefs_sunrise
import coreapp.pebble.generated.resources.settings_prefs_sunset
import coreapp.pebble.generated.resources.settings_prefs_system_intensity
import coreapp.pebble.generated.resources.settings_prefs_threshold
import coreapp.pebble.generated.resources.settings_prefs_timeout
import coreapp.pebble.generated.resources.settings_prefs_to
import coreapp.pebble.generated.resources.settings_prefs_unsupported_faces
import coreapp.pebble.generated.resources.settings_prefs_vibe_delay
import coreapp.pebble.generated.resources.settings_prefs_wrap_around
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.enable
import coreapp.util.generated.resources.none
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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
        title = stringResource(Res.string.settings_prefs_reset_confirm_title),
        text = stringResource(Res.string.settings_prefs_reset_confirm_text),
        onConfirm = {
            settings.forEach { setting ->
                if (setting.value != setting.pref.defaultValue) {
                    @Suppress("UNCHECKED_CAST")
                    val pref = setting.pref as WatchPref<Any?>
                    libPebble.setWatchPref(WatchPreference(pref, pref.defaultValue))
                }
            }
        },
        confirmText = stringResource(Res.string.settings_prefs_reset),
    )
    val reset = basicSettingsActionItem(
        title = SettingsText(Res.string.settings_prefs_reset_to_defaults),
        topLevelType = TopLevelType.Watch,
        section = Section.Defaults,
        action = {
            showConfirmReset.value = true
        },
        description = SettingsText(Res.string.settings_prefs_reset_to_defaults_desc),
    )
    return listOf(reset) + mapped
}

internal class PrefLayout(
    val pref: WatchPref<*>,
    val section: Section,
    val group: SettingsGroup? = null,
    /** Its title under the group's heading. */
    val title: StringResource? = null,
    val name: StringResource,
    val description: StringResource? = null,
)

/** Every watch pref, in the order the settings screen shows them (WatchPrefLayoutTest checks none is missing). */
internal val WATCH_PREF_LAYOUT: List<PrefLayout> = listOf(
    PrefLayout(QuicklaunchWatchPref.QlUp, Section.QuickLaunch, name = Res.string.watch_pref_ql_up),
    PrefLayout(QuicklaunchWatchPref.QlDown, Section.QuickLaunch, name = Res.string.watch_pref_ql_down),
    PrefLayout(QuicklaunchWatchPref.QlSelect, Section.QuickLaunch, name = Res.string.watch_pref_ql_select),
    PrefLayout(QuicklaunchWatchPref.QlBack, Section.QuickLaunch, name = Res.string.watch_pref_ql_back),
    PrefLayout(QuicklaunchWatchPref.QlComboBackUp, Section.QuickLaunch, name = Res.string.watch_pref_ql_combo_back_up),
    PrefLayout(QuicklaunchWatchPref.QlComboUpDown, Section.QuickLaunch, name = Res.string.watch_pref_ql_combo_up_down),
    PrefLayout(QuicklaunchWatchPref.QlSingleClickUp, Section.QuickLaunch, name = Res.string.watch_pref_ql_single_click_up),
    PrefLayout(QuicklaunchWatchPref.QlSingleClickDown, Section.QuickLaunch, name = Res.string.watch_pref_ql_single_click_down),

    PrefLayout(EnumWatchPref.SystemTextSize, Section.Display, name = Res.string.watch_pref_system_text_size),
    PrefLayout(BoolWatchPref.Backlight, Section.Display, SettingsGroup.Backlight, title = UtilRes.string.enable, name = Res.string.watch_pref_backlight),
    PrefLayout(EnumWatchPref.BacklightPreset, Section.Display, SettingsGroup.Backlight, title = Res.string.settings_prefs_preset, name = Res.string.watch_pref_backlight_preset, description = Res.string.watch_pref_backlight_preset_desc),
    PrefLayout(EnumWatchPref.BacklightIntensity, Section.Display, SettingsGroup.Backlight, title = Res.string.settings_prefs_intensity, name = Res.string.watch_pref_backlight_intensity, description = Res.string.watch_pref_backlight_intensity_desc),
    PrefLayout(EnumWatchPref.DynamicBacklightMode, Section.Display, SettingsGroup.Backlight, name = Res.string.watch_pref_dynamic_backlight_mode, description = Res.string.watch_pref_dynamic_backlight_mode_desc),
    PrefLayout(EnumWatchPref.BacklightTouch, Section.Display, SettingsGroup.Backlight, name = Res.string.watch_pref_backlight_touch, description = Res.string.watch_pref_backlight_touch_desc),
    PrefLayout(NumberWatchPref.BacklightTimeoutMs, Section.Display, SettingsGroup.Backlight, title = Res.string.settings_prefs_timeout, name = Res.string.watch_pref_backlight_timeout_ms),
    PrefLayout(BoolWatchPref.AmbientLightSensor, Section.Display, SettingsGroup.BacklightAmbient, title = UtilRes.string.enable, name = Res.string.watch_pref_ambient_light_sensor, description = Res.string.watch_pref_ambient_light_sensor_desc),
    PrefLayout(NumberWatchPref.AmbientLightThreshold, Section.Display, SettingsGroup.BacklightAmbient, title = Res.string.settings_prefs_threshold, name = Res.string.watch_pref_ambient_light_threshold),
    PrefLayout(BoolWatchPref.BacklightMotion, Section.Display, SettingsGroup.BacklightMotion, title = UtilRes.string.enable, name = Res.string.watch_pref_backlight_motion, description = Res.string.watch_pref_backlight_motion_desc),
    PrefLayout(EnumWatchPref.MotionSensitivity, Section.Display, SettingsGroup.BacklightMotion, title = Res.string.settings_prefs_sensitivity, name = Res.string.watch_pref_motion_sensitivity),
    PrefLayout(RgbColorWatchPref.BacklightColor, Section.Display, SettingsGroup.BacklightColor, title = Res.string.settings_color, name = Res.string.watch_pref_backlight_color, description = Res.string.watch_pref_backlight_color_desc),
    PrefLayout(BoolWatchPref.BacklightColorDayNightEnabled, Section.Display, SettingsGroup.BacklightColor, title = Res.string.settings_prefs_change_color_at_night, name = Res.string.watch_pref_backlight_color_day_night_enabled, description = Res.string.watch_pref_backlight_color_day_night_enabled_desc),
    PrefLayout(RgbColorWatchPref.BacklightColorNight, Section.Display, SettingsGroup.BacklightColor, title = Res.string.settings_prefs_night_color, name = Res.string.watch_pref_backlight_color_night, description = Res.string.watch_pref_backlight_color_night_desc),
    PrefLayout(NumberWatchPref.BacklightColorSunriseMinute, Section.Display, SettingsGroup.BacklightColor, title = Res.string.settings_prefs_sunrise, name = Res.string.watch_pref_backlight_color_sunrise_minute),
    PrefLayout(NumberWatchPref.BacklightColorSunsetMinute, Section.Display, SettingsGroup.BacklightColor, title = Res.string.settings_prefs_sunset, name = Res.string.watch_pref_backlight_color_sunset_minute),
    PrefLayout(BoolWatchPref.MenuScrollWrapAround, Section.Display, SettingsGroup.MenuScrolling, title = Res.string.settings_prefs_wrap_around, name = Res.string.watch_pref_menu_scroll_wrap_around, description = Res.string.watch_pref_menu_scroll_wrap_around_desc),
    PrefLayout(EnumWatchPref.MenuScrollVibe, Section.Display, SettingsGroup.MenuScrolling, title = Res.string.settings_group_vibration, name = Res.string.watch_pref_menu_scroll_vibe),
    PrefLayout(BoolWatchPref.LeftHandedMode, Section.Display, name = Res.string.watch_pref_left_handed_mode, description = Res.string.watch_pref_left_handed_mode_desc),

    PrefLayout(EnumWatchPref.Temperature, Section.Weather, name = Res.string.watch_pref_temperature, description = Res.string.watch_pref_temperature_desc),
    PrefLayout(EnumWatchPref.WindSpeed, Section.Weather, name = Res.string.watch_pref_wind_speed, description = Res.string.watch_pref_wind_speed_desc),

    PrefLayout(EnumWatchPref.LegacyVibeIntensity, Section.Notifications, SettingsGroup.Vibration, title = Res.string.settings_prefs_system_intensity, name = Res.string.watch_pref_legacy_vibe_intensity),
    PrefLayout(EnumWatchPref.VibeScoreNotifications, Section.Notifications, SettingsGroup.Vibration, title = Res.string.notifications, name = Res.string.watch_pref_vibe_score_notifications),
    PrefLayout(EnumWatchPref.VibeScoreCalls, Section.Notifications, SettingsGroup.Vibration, title = Res.string.settings_prefs_incoming_calls, name = Res.string.watch_pref_vibe_score_calls),
    PrefLayout(EnumWatchPref.VibeScoreAlarms, Section.Notifications, SettingsGroup.Vibration, title = Res.string.settings_prefs_alarms, name = Res.string.watch_pref_vibe_score_alarms),
    PrefLayout(BoolWatchPref.NotificationVibeDelay, Section.Notifications, SettingsGroup.Vibration, title = Res.string.settings_prefs_vibe_delay, name = Res.string.watch_pref_notification_vibe_delay, description = Res.string.watch_pref_notification_vibe_delay_desc),
    PrefLayout(BoolWatchPref.NotificationBacklight, Section.Notifications, name = Res.string.watch_pref_notification_backlight, description = Res.string.watch_pref_notification_backlight_desc),
    PrefLayout(BoolWatchPref.RespectPhoneSilence, Section.Notifications, name = Res.string.watch_pref_respect_phone_silence, description = Res.string.watch_pref_respect_phone_silence_desc),
    PrefLayout(BoolWatchPref.NotificationDismissalSync, Section.Notifications, name = Res.string.watch_pref_notification_dismissal_sync, description = Res.string.watch_pref_notification_dismissal_sync_desc),
    PrefLayout(EnumWatchPref.NotificationTextSize, Section.Notifications, name = Res.string.watch_pref_notification_text_size),
    PrefLayout(EnumWatchPref.NotificationFilter, Section.Notifications, name = Res.string.watch_pref_notification_filter),
    PrefLayout(NumberWatchPref.NotificationTimeoutMs, Section.Notifications, name = Res.string.watch_pref_notification_timeout_ms, description = Res.string.watch_pref_notification_timeout_ms_desc),
    PrefLayout(BoolWatchPref.AlternativeNotificationStyle, Section.Notifications, name = Res.string.watch_pref_alternative_notification_style),

    PrefLayout(BoolWatchPref.TimezoneSourceIsManual, Section.Time, name = Res.string.watch_pref_timezone_source_is_manual, description = Res.string.watch_pref_timezone_source_is_manual_desc),
    PrefLayout(BoolWatchPref.Clock24h, Section.Time, name = Res.string.watch_pref_clock24h),

    PrefLayout(BoolWatchPref.TimelineQuickViewEnabled, Section.Timeline, SettingsGroup.QuickView, title = Res.string.settings_prefs_show_upcoming_events, name = Res.string.watch_pref_timeline_quick_view_enabled, description = Res.string.watch_pref_timeline_quick_view_enabled_desc),
    PrefLayout(NumberWatchPref.TimelineQuickViewMinsBefore, Section.Timeline, SettingsGroup.QuickView, title = Res.string.settings_prefs_minutes_before_event, name = Res.string.watch_pref_timeline_quick_view_mins_before),
    PrefLayout(EnumWatchPref.TimelineQuickViewUnsupportedFaces, Section.Timeline, SettingsGroup.QuickView, title = Res.string.settings_prefs_unsupported_faces, name = Res.string.watch_pref_timeline_quick_view_unsupported_faces, description = Res.string.watch_pref_timeline_quick_view_unsupported_faces_desc),

    PrefLayout(BoolWatchPref.QuietTimeManuallyEnabled, Section.QuietTime, name = Res.string.watch_pref_quiet_time_manually_enabled, description = Res.string.watch_pref_quiet_time_manually_enabled_desc),
    PrefLayout(BoolWatchPref.CalendarAwareQuietTime, Section.QuietTime, name = Res.string.watch_pref_calendar_aware_quiet_time, description = Res.string.watch_pref_calendar_aware_quiet_time_desc),
    PrefLayout(BoolWatchPref.QuietTimeWeekdayScheduleEnabled, Section.QuietTime, name = Res.string.watch_pref_quiet_time_weekday_schedule_enabled, description = Res.string.watch_pref_quiet_time_weekday_schedule_enabled_desc),
    PrefLayout(ScheduleWatchPref.QuietTimeWeekdaySchedule, Section.QuietTime, name = Res.string.watch_pref_quiet_time_weekday_schedule),
    PrefLayout(BoolWatchPref.QuietTimeWeekendScheduleEnabled, Section.QuietTime, name = Res.string.watch_pref_quiet_time_weekend_schedule_enabled, description = Res.string.watch_pref_quiet_time_weekend_schedule_enabled_desc),
    PrefLayout(ScheduleWatchPref.QuietTimeWeekendSchedule, Section.QuietTime, name = Res.string.watch_pref_quiet_time_weekend_schedule),
    PrefLayout(BoolWatchPref.QuietTimeMotionBacklight, Section.QuietTime, name = Res.string.watch_pref_quiet_time_motion_backlight, description = Res.string.watch_pref_quiet_time_motion_backlight_desc),
    PrefLayout(BoolWatchPref.QuietTimeTouchBacklight, Section.QuietTime, name = Res.string.watch_pref_quiet_time_touch_backlight, description = Res.string.watch_pref_quiet_time_touch_backlight_desc),
    PrefLayout(BoolWatchPref.QuietTimeNotificationBacklight, Section.QuietTime, name = Res.string.watch_pref_quiet_time_notification_backlight, description = Res.string.watch_pref_quiet_time_notification_backlight_desc),
    PrefLayout(BoolWatchPref.QuietTimeAutoDismiss, Section.QuietTime, name = Res.string.watch_pref_quiet_time_auto_dismiss, description = Res.string.watch_pref_quiet_time_auto_dismiss_desc),
    PrefLayout(EnumWatchPref.QuietTimeInterruptions, Section.QuietTime, name = Res.string.watch_pref_quiet_time_interruptions),
    PrefLayout(EnumWatchPref.QuietTimeShowNotifications, Section.QuietTime, name = Res.string.watch_pref_quiet_time_show_notifications),

    PrefLayout(BoolWatchPref.MusicShowVolumeControls, Section.Music, name = Res.string.watch_pref_music_show_volume_controls),
    PrefLayout(BoolWatchPref.MusicShowProgressBar, Section.Music, name = Res.string.watch_pref_music_show_progress_bar),
    PrefLayout(BoolWatchPref.MusicShowAlbumArt, Section.Music, name = Res.string.watch_pref_music_show_album_art),

    PrefLayout(BoolWatchPref.StandbyMode, Section.Other, name = Res.string.watch_pref_standby_mode, description = Res.string.watch_pref_standby_mode_desc),
)

private val LAYOUT = WATCH_PREF_LAYOUT.associateBy { it.pref }
private val POSITION = WATCH_PREF_LAYOUT.withIndex().associate { (index, layout) -> layout.pref to index }

fun WatchPref<*>.section(): Section = LAYOUT[this]?.section ?: Section.Other

fun WatchPref<*>.settingsTitle(): SettingsText =
    (LAYOUT[this]?.let { it.title ?: it.name })?.let { SettingsText(it) } ?: SettingsText(displayName)

fun WatchPref<*>.settingsDescription(): SettingsText? = LAYOUT[this]?.description?.let { SettingsText(it) }

fun WatchPref<*>.settingsGroup(): SettingsGroup? = LAYOUT[this]?.group

internal fun List<WatchPreference<*>>.inLayoutOrder(): List<WatchPreference<*>> =
    sortedBy { POSITION[it.pref] ?: Int.MAX_VALUE }

fun WatchPref<*>.topLevelType(): TopLevelType = when (this) {
    // Weather is configured on the phone, even though the unit it sets is a watch pref.
    EnumWatchPref.WindSpeed, EnumWatchPref.Temperature -> TopLevelType.Phone
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
                valueFormatter = { seconds -> pluralStringResource(Res.plurals.settings_prefs_seconds, seconds.toInt(), seconds) },
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
        NumberWatchPref.TimelineQuickViewMinsBefore -> basicSettingsNumberItem(
            id = pref.id,
            title = pref.settingsTitle(),
            description = pref.settingsDescription(),
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
            unit = null,
            valueFormatter = { minutes -> pluralStringResource(Res.plurals.settings_prefs_minutes, minutes.toInt(), minutes) },
        )
        else -> basicSettingsNumberItem(
            id = pref.id,
            title = pref.settingsTitle(),
            description = pref.settingsDescription(),
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
            unit = SettingsText(pref.unit),
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
    valueFormatter: @Composable (Long) -> String,
    steps: Int? = null,
): SettingsItem = basicSettingsNumberItem(
    id = pref.id,
    title = pref.settingsTitle(),
    description = pref.settingsDescription(),
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
    unit = null,
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
                    Text(pref.settingsTitle().text())
                },
                supportingContent = {
                    Column {
                        pref.settingsDescription()?.let { description ->
                            Text(description.text(), fontSize = 11.sp)
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

private enum class ScheduleEdge(val title: StringResource) {
    From(Res.string.settings_prefs_start_time),
    To(Res.string.settings_prefs_end_time),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectTimeRange(
    schedule: QuietTimeSchedule,
    onChangeSchedule: (QuietTimeSchedule) -> Unit,
) {
    var editing by remember { mutableStateOf<ScheduleEdge?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.settings_prefs_from), fontSize = 14.sp)
        TextButton(onClick = { editing = ScheduleEdge.From }) {
            Text(schedule.fromTime())
        }
        Text(stringResource(Res.string.settings_prefs_to), fontSize = 14.sp)
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
        title = { Text(stringResource(edge.title)) },
        dismissButton = {
            TextButton(onClick = { editing = null }) {
                Text(stringResource(UtilRes.string.cancel))
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
                Text(stringResource(Res.string.settings_prefs_set))
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
                    Text(pref.settingsTitle().text())
                },
                supportingContent = {
                    Column {
                        pref.settingsDescription()?.let { description ->
                            Text(description.text(), fontSize = 11.sp)
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
        description = item.pref.settingsDescription(),
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
        description = pref.settingsDescription(),
        topLevelType = pref.topLevelType(),
        section = pref.section(),
        group = pref.settingsGroup(),
        selectedItem = item.valueOrDefault(),
        items = pref.options,
        onItemSelected = {
            libPebble.setWatchPref(item.copy(value = it))
        },
        itemText = { it.label() },
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
                headlineContent = { Text(pref.settingsTitle().text()) },
                supportingContent = {
                    Column {
                        pref.settingsDescription()?.let { description ->
                            Text(description.text(), fontSize = 11.sp)
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
        title = SettingsText(Res.string.settings_prefs_auto_sun_times),
        description = SettingsText(Res.string.settings_prefs_auto_sun_times_desc),
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
            SelectTimeOfDay(pref.settingsTitle().text(), item.valueOrDefault()) {
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
        dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(UtilRes.string.cancel)) } },
        confirmButton = {
            TextButton(onClick = {
                editing = false
                onChange((state.hour * 60 + state.minute).toLong())
            }) { Text(stringResource(Res.string.settings_prefs_set)) }
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
    val noneLabel = stringResource(UtilRes.string.none)
    return remember(installedApps, noneLabel) {
        listOf(QuickLaunchOption(null, noneLabel)) +
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
        description = item.pref.settingsDescription(),
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
