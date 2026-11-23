package coredevices.pebble.ui

import androidx.compose.runtime.Composable
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.watch_pref_color_blue
import coreapp.pebble.generated.resources.watch_pref_color_cool_white
import coreapp.pebble.generated.resources.watch_pref_color_cyan
import coreapp.pebble.generated.resources.watch_pref_color_green
import coreapp.pebble.generated.resources.watch_pref_color_lime
import coreapp.pebble.generated.resources.watch_pref_color_magenta
import coreapp.pebble.generated.resources.watch_pref_color_orange
import coreapp.pebble.generated.resources.watch_pref_color_pink
import coreapp.pebble.generated.resources.watch_pref_color_purple
import coreapp.pebble.generated.resources.watch_pref_color_red
import coreapp.pebble.generated.resources.watch_pref_color_warm_white
import coreapp.pebble.generated.resources.watch_pref_color_yellow
import coreapp.pebble.generated.resources.watch_pref_option_advanced
import coreapp.pebble.generated.resources.watch_pref_option_all_off
import coreapp.pebble.generated.resources.watch_pref_option_all_on
import coreapp.pebble.generated.resources.watch_pref_option_all_on_legacy
import coreapp.pebble.generated.resources.watch_pref_option_automatic
import coreapp.pebble.generated.resources.watch_pref_option_battery_saver
import coreapp.pebble.generated.resources.watch_pref_option_blinding
import coreapp.pebble.generated.resources.watch_pref_option_bright
import coreapp.pebble.generated.resources.watch_pref_option_default
import coreapp.pebble.generated.resources.watch_pref_option_dim
import coreapp.pebble.generated.resources.watch_pref_option_disabled
import coreapp.pebble.generated.resources.watch_pref_option_double_pulse_medium
import coreapp.pebble.generated.resources.watch_pref_option_double_tap
import coreapp.pebble.generated.resources.watch_pref_option_gentle
import coreapp.pebble.generated.resources.watch_pref_option_heartbeat
import coreapp.pebble.generated.resources.watch_pref_option_hide
import coreapp.pebble.generated.resources.watch_pref_option_high
import coreapp.pebble.generated.resources.watch_pref_option_imperial
import coreapp.pebble.generated.resources.watch_pref_option_jackhammer
import coreapp.pebble.generated.resources.watch_pref_option_knots
import coreapp.pebble.generated.resources.watch_pref_option_larger
import coreapp.pebble.generated.resources.watch_pref_option_low
import coreapp.pebble.generated.resources.watch_pref_option_mario
import coreapp.pebble.generated.resources.watch_pref_option_max_brightness
import coreapp.pebble.generated.resources.watch_pref_option_medium
import coreapp.pebble.generated.resources.watch_pref_option_medium_high
import coreapp.pebble.generated.resources.watch_pref_option_medium_low
import coreapp.pebble.generated.resources.watch_pref_option_no_vibe
import coreapp.pebble.generated.resources.watch_pref_option_nudge_nudge
import coreapp.pebble.generated.resources.watch_pref_option_off
import coreapp.pebble.generated.resources.watch_pref_option_other
import coreapp.pebble.generated.resources.watch_pref_option_overlay
import coreapp.pebble.generated.resources.watch_pref_option_pebble_morse
import coreapp.pebble.generated.resources.watch_pref_option_phone_calls
import coreapp.pebble.generated.resources.watch_pref_option_pulse
import coreapp.pebble.generated.resources.watch_pref_option_reveille
import coreapp.pebble.generated.resources.watch_pref_option_same_as_system
import coreapp.pebble.generated.resources.watch_pref_option_shift_up
import coreapp.pebble.generated.resources.watch_pref_option_show
import coreapp.pebble.generated.resources.watch_pref_option_smaller
import coreapp.pebble.generated.resources.watch_pref_option_squish_up
import coreapp.pebble.generated.resources.watch_pref_option_standard
import coreapp.pebble.generated.resources.watch_pref_option_standard_high
import coreapp.pebble.generated.resources.watch_pref_option_standard_low
import coreapp.pebble.generated.resources.watch_pref_option_tap
import coreapp.pebble.generated.resources.watch_pref_option_very_high
import coreapp.pebble.generated.resources.watch_pref_option_very_low
import coreapp.pebble.generated.resources.watch_pref_option_vibe_on_locked
import coreapp.pebble.generated.resources.watch_pref_option_vibe_on_wrap_around
import coreapp.pebble.generated.resources.watch_pref_option_wave
import io.rebble.libpebblecommon.database.entity.RgbColorPreset
import io.rebble.libpebblecommon.database.entity.WatchPrefEnum
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Keyed by libpebble3's English displayName, which stays as the plugin API's name for each value. */
internal val WATCH_PREF_OPTION_LABELS: Map<String, StringResource> = mapOf(
    "Smaller" to Res.string.watch_pref_option_smaller,
    "Default" to Res.string.watch_pref_option_default,
    "Larger" to Res.string.watch_pref_option_larger,
    "Same as System" to Res.string.watch_pref_option_same_as_system,
    "All Off" to Res.string.watch_pref_option_all_off,
    "Phone Calls" to Res.string.watch_pref_option_phone_calls,
    "Other" to Res.string.watch_pref_option_other,
    "All On (Legacy)" to Res.string.watch_pref_option_all_on_legacy,
    "All On" to Res.string.watch_pref_option_all_on,
    "Hide" to Res.string.watch_pref_option_hide,
    "Show" to Res.string.watch_pref_option_show,
    "Low" to Res.string.watch_pref_option_low,
    "Medium" to Res.string.watch_pref_option_medium,
    "High" to Res.string.watch_pref_option_high,
    "Disabled" to Res.string.watch_pref_option_disabled,
    "Standard - Low" to Res.string.watch_pref_option_standard_low,
    "Standard - High" to Res.string.watch_pref_option_standard_high,
    "Pulse" to Res.string.watch_pref_option_pulse,
    "Nudge Nudge" to Res.string.watch_pref_option_nudge_nudge,
    "Jackhammer" to Res.string.watch_pref_option_jackhammer,
    "Reveille" to Res.string.watch_pref_option_reveille,
    "Mario" to Res.string.watch_pref_option_mario,
    "Gentle" to Res.string.watch_pref_option_gentle,
    "Double Pulse Medium" to Res.string.watch_pref_option_double_pulse_medium,
    "Pebble Morse" to Res.string.watch_pref_option_pebble_morse,
    "Heartbeat" to Res.string.watch_pref_option_heartbeat,
    "Double Tap" to Res.string.watch_pref_option_double_tap,
    "Wave" to Res.string.watch_pref_option_wave,
    "Imperial" to Res.string.watch_pref_option_imperial,
    "No Vibe" to Res.string.watch_pref_option_no_vibe,
    "Vibe On Wrap Around" to Res.string.watch_pref_option_vibe_on_wrap_around,
    "Vibe On Locked" to Res.string.watch_pref_option_vibe_on_locked,
    "Automatic" to Res.string.watch_pref_option_automatic,
    "knots" to Res.string.watch_pref_option_knots,
    "Very Low" to Res.string.watch_pref_option_very_low,
    "Medium-Low" to Res.string.watch_pref_option_medium_low,
    "Medium-High" to Res.string.watch_pref_option_medium_high,
    "Very High" to Res.string.watch_pref_option_very_high,
    "Blinding" to Res.string.watch_pref_option_blinding,
    "Tap" to Res.string.watch_pref_option_tap,
    "Off" to Res.string.watch_pref_option_off,
    "Bright" to Res.string.watch_pref_option_bright,
    "Standard" to Res.string.watch_pref_option_standard,
    "Dim" to Res.string.watch_pref_option_dim,
    "Max Brightness" to Res.string.watch_pref_option_max_brightness,
    "Battery Saver" to Res.string.watch_pref_option_battery_saver,
    "Advanced" to Res.string.watch_pref_option_advanced,
    "Overlay" to Res.string.watch_pref_option_overlay,
    "Shift Up" to Res.string.watch_pref_option_shift_up,
    "Squish Up" to Res.string.watch_pref_option_squish_up,
)

internal val WATCH_PREF_COLOR_LABELS: Map<String, StringResource> = mapOf(
    "Red" to Res.string.watch_pref_color_red,
    "Orange" to Res.string.watch_pref_color_orange,
    "Yellow" to Res.string.watch_pref_color_yellow,
    "Lime" to Res.string.watch_pref_color_lime,
    "Green" to Res.string.watch_pref_color_green,
    "Cyan" to Res.string.watch_pref_color_cyan,
    "Blue" to Res.string.watch_pref_color_blue,
    "Purple" to Res.string.watch_pref_color_purple,
    "Magenta" to Res.string.watch_pref_color_magenta,
    "Pink" to Res.string.watch_pref_color_pink,
    "Warm White" to Res.string.watch_pref_color_warm_white,
    "Cool White" to Res.string.watch_pref_color_cool_white,
)

@Composable
fun WatchPrefEnum.label(): String = WATCH_PREF_OPTION_LABELS[displayName]?.let { stringResource(it) } ?: displayName

@Composable
fun RgbColorPreset.label(): String = WATCH_PREF_COLOR_LABELS[displayName]?.let { stringResource(it) } ?: displayName
