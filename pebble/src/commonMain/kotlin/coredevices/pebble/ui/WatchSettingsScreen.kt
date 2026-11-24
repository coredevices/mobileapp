package coredevices.pebble.ui

import AppUpdateTracker
import CommonRoutes
import CoreAppVersion
import NextBugReportContext
import PlatformUiContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import com.cactus.isCactusSupported
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.apps
import coreapp.pebble.generated.resources.health
import coreapp.pebble.generated.resources.notifications
import coreapp.pebble.generated.resources.settings
import coreapp.pebble.generated.resources.settings_about_item
import coreapp.pebble.generated.resources.settings_always_send_notifications
import coreapp.pebble.generated.resources.settings_always_send_notifications_desc
import coreapp.pebble.generated.resources.settings_answer_calls_on_speaker
import coreapp.pebble.generated.resources.settings_answer_calls_on_speaker_desc
import coreapp.pebble.generated.resources.settings_app_activity
import coreapp.pebble.generated.resources.settings_app_activity_desc
import coreapp.pebble.generated.resources.settings_app_theme
import coreapp.pebble.generated.resources.settings_app_update_available
import coreapp.pebble.generated.resources.settings_app_update_available_desc
import coreapp.pebble.generated.resources.settings_app_version
import coreapp.pebble.generated.resources.settings_app_version_value
import coreapp.pebble.generated.resources.settings_ask_legacy_apps
import coreapp.pebble.generated.resources.settings_ask_legacy_apps_desc
import coreapp.pebble.generated.resources.settings_auto_update_pebbleos
import coreapp.pebble.generated.resources.settings_auto_update_pebbleos_desc
import coreapp.pebble.generated.resources.settings_background_refresh
import coreapp.pebble.generated.resources.settings_background_refresh_desc
import coreapp.pebble.generated.resources.settings_bottom_bar
import coreapp.pebble.generated.resources.settings_bottom_bar_desc
import coreapp.pebble.generated.resources.settings_calendar_reminders
import coreapp.pebble.generated.resources.settings_calendar_reminders_desc
import coreapp.pebble.generated.resources.settings_calendars
import coreapp.pebble.generated.resources.settings_calendars_desc
import coreapp.pebble.generated.resources.settings_cloud_recognition_provider
import coreapp.pebble.generated.resources.settings_complications
import coreapp.pebble.generated.resources.settings_complications_desc
import coreapp.pebble.generated.resources.settings_configure_appstore_sources
import coreapp.pebble.generated.resources.settings_data_usage
import coreapp.pebble.generated.resources.settings_data_usage_desc
import coreapp.pebble.generated.resources.settings_declined_events
import coreapp.pebble.generated.resources.settings_declined_events_desc
import coreapp.pebble.generated.resources.settings_decrease
import coreapp.pebble.generated.resources.settings_default_value
import coreapp.pebble.generated.resources.settings_disable_cdm
import coreapp.pebble.generated.resources.settings_disable_cdm_desc
import coreapp.pebble.generated.resources.settings_download_in_background
import coreapp.pebble.generated.resources.settings_download_in_background_desc
import coreapp.pebble.generated.resources.settings_emulate_timeline
import coreapp.pebble.generated.resources.settings_emulate_timeline_desc
import coreapp.pebble.generated.resources.settings_enable_calendar
import coreapp.pebble.generated.resources.settings_enable_calendar_desc
import coreapp.pebble.generated.resources.settings_enable_health_tracking
import coreapp.pebble.generated.resources.settings_enable_index_feed
import coreapp.pebble.generated.resources.settings_enable_weather
import coreapp.pebble.generated.resources.settings_enable_weather_desc
import coreapp.pebble.generated.resources.settings_error_starting_download
import coreapp.pebble.generated.resources.settings_error_try_again
import coreapp.pebble.generated.resources.settings_foreground_service
import coreapp.pebble.generated.resources.settings_foreground_service_desc
import coreapp.pebble.generated.resources.settings_gender
import coreapp.pebble.generated.resources.settings_gender_desc
import coreapp.pebble.generated.resources.settings_gender_female
import coreapp.pebble.generated.resources.settings_gender_male
import coreapp.pebble.generated.resources.settings_gender_other
import coreapp.pebble.generated.resources.settings_getting_started
import coreapp.pebble.generated.resources.settings_group_advanced
import coreapp.pebble.generated.resources.settings_group_ambient_light
import coreapp.pebble.generated.resources.settings_group_app_updates
import coreapp.pebble.generated.resources.settings_group_app_usage
import coreapp.pebble.generated.resources.settings_group_automatic_updates
import coreapp.pebble.generated.resources.settings_group_backlight
import coreapp.pebble.generated.resources.settings_group_backlight_color
import coreapp.pebble.generated.resources.settings_group_backlight_motion
import coreapp.pebble.generated.resources.settings_group_custom_vibration
import coreapp.pebble.generated.resources.settings_group_heart_rate
import coreapp.pebble.generated.resources.settings_group_insights
import coreapp.pebble.generated.resources.settings_group_menu_scrolling
import coreapp.pebble.generated.resources.settings_group_permissions
import coreapp.pebble.generated.resources.settings_group_phone_effects
import coreapp.pebble.generated.resources.settings_group_quick_view
import coreapp.pebble.generated.resources.settings_group_touch
import coreapp.pebble.generated.resources.settings_group_vibration
import coreapp.pebble.generated.resources.settings_group_your_details
import coreapp.pebble.generated.resources.settings_health_sync_now
import coreapp.pebble.generated.resources.settings_health_sync_now_desc
import coreapp.pebble.generated.resources.settings_health_syncing
import coreapp.pebble.generated.resources.settings_heart_rate_monitor
import coreapp.pebble.generated.resources.settings_heart_rate_monitor_desc
import coreapp.pebble.generated.resources.settings_height
import coreapp.pebble.generated.resources.settings_height_desc
import coreapp.pebble.generated.resources.settings_hr_background_sampling
import coreapp.pebble.generated.resources.settings_hr_background_sampling_desc
import coreapp.pebble.generated.resources.settings_hr_during_activities
import coreapp.pebble.generated.resources.settings_hr_during_activities_desc
import coreapp.pebble.generated.resources.settings_hr_every_10_min
import coreapp.pebble.generated.resources.settings_hr_every_30_min
import coreapp.pebble.generated.resources.settings_hr_every_5_min
import coreapp.pebble.generated.resources.settings_hr_every_hour
import coreapp.pebble.generated.resources.settings_hr_off
import coreapp.pebble.generated.resources.settings_ignore_other_pebble_apps
import coreapp.pebble.generated.resources.settings_ignore_other_pebble_apps_desc
import coreapp.pebble.generated.resources.settings_imperial_units
import coreapp.pebble.generated.resources.settings_imperial_units_desc
import coreapp.pebble.generated.resources.settings_increase
import coreapp.pebble.generated.resources.settings_insights_activity
import coreapp.pebble.generated.resources.settings_insights_activity_desc
import coreapp.pebble.generated.resources.settings_insights_sleep
import coreapp.pebble.generated.resources.settings_insights_sleep_desc
import coreapp.pebble.generated.resources.settings_intercept_pkjs_weather
import coreapp.pebble.generated.resources.settings_intercept_pkjs_weather_desc
import coreapp.pebble.generated.resources.settings_interval_12_hours
import coreapp.pebble.generated.resources.settings_interval_15_minutes
import coreapp.pebble.generated.resources.settings_interval_1_hour
import coreapp.pebble.generated.resources.settings_interval_24_hours
import coreapp.pebble.generated.resources.settings_interval_30_minutes
import coreapp.pebble.generated.resources.settings_interval_6_hours
import coreapp.pebble.generated.resources.settings_lan_dev_connection
import coreapp.pebble.generated.resources.settings_lan_dev_connection_desc
import coreapp.pebble.generated.resources.settings_legacy_pebblekit
import coreapp.pebble.generated.resources.settings_legacy_pebblekit_desc
import coreapp.pebble.generated.resources.settings_log_notifications
import coreapp.pebble.generated.resources.settings_log_notifications_desc
import coreapp.pebble.generated.resources.settings_log_sensitive
import coreapp.pebble.generated.resources.settings_log_sensitive_desc
import coreapp.pebble.generated.resources.settings_manage_offline_models
import coreapp.pebble.generated.resources.settings_model_download_failed
import coreapp.pebble.generated.resources.settings_music_seek
import coreapp.pebble.generated.resources.settings_music_seek_desc
import coreapp.pebble.generated.resources.settings_mute_call_effects
import coreapp.pebble.generated.resources.settings_mute_call_effects_desc
import coreapp.pebble.generated.resources.settings_mute_notification_effects
import coreapp.pebble.generated.resources.settings_mute_notification_effects_desc
import coreapp.pebble.generated.resources.settings_new_bug_report
import coreapp.pebble.generated.resources.settings_new_bug_report_desc
import coreapp.pebble.generated.resources.settings_notify_fully_charged
import coreapp.pebble.generated.resources.settings_notify_fully_charged_desc
import coreapp.pebble.generated.resources.settings_offline_models_note
import coreapp.pebble.generated.resources.settings_offline_stt
import coreapp.pebble.generated.resources.settings_open_google_fit
import coreapp.pebble.generated.resources.settings_open_google_fit_desc
import coreapp.pebble.generated.resources.settings_passive_reconnection
import coreapp.pebble.generated.resources.settings_passive_reconnection_desc
import coreapp.pebble.generated.resources.settings_permission_missing
import coreapp.pebble.generated.resources.settings_permission_policy
import coreapp.pebble.generated.resources.settings_permissions_all_granted
import coreapp.pebble.generated.resources.settings_permissions_in_use
import coreapp.pebble.generated.resources.settings_permissions_in_use_desc
import coreapp.pebble.generated.resources.settings_permissions_missing
import coreapp.pebble.generated.resources.settings_post_test_notification
import coreapp.pebble.generated.resources.settings_post_test_notification_desc
import coreapp.pebble.generated.resources.settings_preferred_sources
import coreapp.pebble.generated.resources.settings_preferred_sources_desc
import coreapp.pebble.generated.resources.settings_quick_replies
import coreapp.pebble.generated.resources.settings_quick_replies_desc
import coreapp.pebble.generated.resources.settings_reminder_vibe_pattern
import coreapp.pebble.generated.resources.settings_republish_gatt
import coreapp.pebble.generated.resources.settings_respect_dnd_legacy
import coreapp.pebble.generated.resources.settings_respect_dnd_legacy_desc
import coreapp.pebble.generated.resources.settings_restore_system_app_positions
import coreapp.pebble.generated.resources.settings_restore_system_app_positions_desc
import coreapp.pebble.generated.resources.settings_search_languages
import coreapp.pebble.generated.resources.settings_section_about
import coreapp.pebble.generated.resources.settings_section_appearance
import coreapp.pebble.generated.resources.settings_section_battery
import coreapp.pebble.generated.resources.settings_section_calendar
import coreapp.pebble.generated.resources.settings_section_connectivity
import coreapp.pebble.generated.resources.settings_section_debug
import coreapp.pebble.generated.resources.settings_section_defaults
import coreapp.pebble.generated.resources.settings_section_diagnostics
import coreapp.pebble.generated.resources.settings_section_display
import coreapp.pebble.generated.resources.settings_section_vibrations
import coreapp.pebble.generated.resources.settings_section_themes
import coreapp.pebble.generated.resources.settings_section_general
import coreapp.pebble.generated.resources.settings_section_music
import coreapp.pebble.generated.resources.settings_section_other
import coreapp.pebble.generated.resources.settings_section_plugins
import coreapp.pebble.generated.resources.settings_section_quick_launch
import coreapp.pebble.generated.resources.settings_section_quiet_time
import coreapp.pebble.generated.resources.settings_section_speech
import coreapp.pebble.generated.resources.settings_section_support
import coreapp.pebble.generated.resources.settings_section_time
import coreapp.pebble.generated.resources.settings_section_timeline
import coreapp.pebble.generated.resources.settings_section_weather
import coreapp.pebble.generated.resources.settings_select_language
import coreapp.pebble.generated.resources.settings_send_app_analytics
import coreapp.pebble.generated.resources.settings_send_app_analytics_desc
import coreapp.pebble.generated.resources.settings_send_app_crashes
import coreapp.pebble.generated.resources.settings_send_app_crashes_desc
import coreapp.pebble.generated.resources.settings_send_local_only
import coreapp.pebble.generated.resources.settings_send_local_only_desc
import coreapp.pebble.generated.resources.settings_send_notification_images
import coreapp.pebble.generated.resources.settings_send_notification_images_desc
import coreapp.pebble.generated.resources.settings_send_watch_analytics
import coreapp.pebble.generated.resources.settings_send_watch_analytics_desc
import coreapp.pebble.generated.resources.settings_show_debug_options
import coreapp.pebble.generated.resources.settings_show_debug_options_desc
import coreapp.pebble.generated.resources.settings_show_update_badge
import coreapp.pebble.generated.resources.settings_shows_ui_actions
import coreapp.pebble.generated.resources.settings_shows_ui_actions_desc
import coreapp.pebble.generated.resources.settings_sign_in_pebble
import coreapp.pebble.generated.resources.settings_sign_in_pebble_desc
import coreapp.pebble.generated.resources.settings_sign_out_pebble
import coreapp.pebble.generated.resources.settings_sign_out_pebble_desc
import coreapp.pebble.generated.resources.settings_sign_out_rebble
import coreapp.pebble.generated.resources.settings_sign_out_rebble_desc
import coreapp.pebble.generated.resources.settings_spoken_language
import coreapp.pebble.generated.resources.settings_spoken_language_note
import coreapp.pebble.generated.resources.settings_store_disabled
import coreapp.pebble.generated.resources.settings_store_disabled_desc
import coreapp.pebble.generated.resources.settings_store_notifications
import coreapp.pebble.generated.resources.settings_store_notifications_desc
import coreapp.pebble.generated.resources.settings_stt_cloud_local_fallback
import coreapp.pebble.generated.resources.settings_stt_cloud_only
import coreapp.pebble.generated.resources.settings_stt_cloud_requires_sign_in
import coreapp.pebble.generated.resources.settings_stt_downloading_background
import coreapp.pebble.generated.resources.settings_stt_local_cloud_fallback
import coreapp.pebble.generated.resources.settings_stt_local_only
import coreapp.pebble.generated.resources.settings_stt_local_rebble_fallback
import coreapp.pebble.generated.resources.settings_stt_no_local_support
import coreapp.pebble.generated.resources.settings_stt_no_system_support
import coreapp.pebble.generated.resources.settings_stt_rebble_local_fallback
import coreapp.pebble.generated.resources.settings_stt_rebble_only
import coreapp.pebble.generated.resources.settings_stt_rebble_requires_subscription
import coreapp.pebble.generated.resources.settings_stt_system
import coreapp.pebble.generated.resources.settings_sync_apple_health
import coreapp.pebble.generated.resources.settings_sync_health_connect
import coreapp.pebble.generated.resources.settings_system_speech_model
import coreapp.pebble.generated.resources.settings_tab_all
import coreapp.pebble.generated.resources.settings_tab_phone
import coreapp.pebble.generated.resources.settings_tab_watch
import coreapp.pebble.generated.resources.settings_time_window
import coreapp.pebble.generated.resources.settings_time_window_only
import coreapp.pebble.generated.resources.settings_unit_cm
import coreapp.pebble.generated.resources.settings_unit_days
import coreapp.pebble.generated.resources.settings_unit_in
import coreapp.pebble.generated.resources.settings_unit_kg
import coreapp.pebble.generated.resources.settings_unit_lb
import coreapp.pebble.generated.resources.settings_use_os_vibe_patterns
import coreapp.pebble.generated.resources.settings_use_os_vibe_patterns_desc
import coreapp.pebble.generated.resources.settings_vibe_override_default
import coreapp.pebble.generated.resources.settings_vibe_pattern_title
import coreapp.pebble.generated.resources.settings_view_bug_reports
import coreapp.pebble.generated.resources.settings_weather_locations
import coreapp.pebble.generated.resources.settings_weather_locations_desc
import coreapp.pebble.generated.resources.settings_weather_pins
import coreapp.pebble.generated.resources.settings_weather_pins_desc
import coreapp.pebble.generated.resources.settings_weather_provider
import coreapp.pebble.generated.resources.settings_weather_provider_desc
import coreapp.pebble.generated.resources.settings_weather_provider_plugin
import coreapp.pebble.generated.resources.settings_weather_refresh
import coreapp.pebble.generated.resources.settings_weight
import coreapp.pebble.generated.resources.settings_weight_desc
import coreapp.pebble.generated.resources.settings_whats_new_app
import coreapp.pebble.generated.resources.settings_whats_new_pebbleos
import coreapp.pebble.generated.resources.settings_wifi_only
import coreapp.pebble.generated.resources.settings_wifi_only_desc
import coreapp.pebble.generated.resources.settings_year_of_birth
import coreapp.pebble.generated.resources.settings_year_of_birth_desc
import coreapp.pebble.generated.resources.wispr_flow_logo_black
import coreapp.pebble.generated.resources.wispr_flow_logo_white
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.automatic
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.ok
import coredevices.CoreBackgroundSync
import coredevices.EnableExperimentalDevices
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.CoreAnalytics
import coredevices.analytics.setUser
import coredevices.coreapp.util.AppUpdate
import coredevices.coreapp.util.AppUpdateState
import coredevices.firestore.UsersDao
import coredevices.pebble.PebbleFeatures
import coredevices.pebble.Platform
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.health.HealthSyncTracker
import coredevices.pebble.health.PlatformHealthSync
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.ui.SettingsIds.EnableActivityInsights
import coredevices.pebble.ui.SettingsIds.EnableHealthPlatformSync
import coredevices.pebble.ui.SettingsIds.EnableHealthTracking
import coredevices.pebble.ui.SettingsIds.EnableSleepInsights
import coredevices.pebble.ui.SettingsIds.OfflineSpeechRecognition
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_FIREBASE_UPLOADS
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MEMFAULT_UPLOADS
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MIXPANEL_UPLOADS
import coredevices.pebble.weather.WeatherFetcher
import coredevices.pebble.weather.WeatherPlugin
import coredevices.ui.ConfirmDialog
import coredevices.ui.CoreLinearProgressIndicator
import coredevices.ui.M3Dialog
import coredevices.ui.SignInDialog
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigHolder
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.STTConfig
import coredevices.util.emailOrNull
import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.ModelInfo
import coredevices.util.models.ModelManager
import coredevices.util.models.RecommendedModel
import coredevices.util.models.inProgress
import coredevices.util.rememberUiContext
import coredevices.util.transcription.PlatformSpeechRecognizer
import coredevices.util.transcription.SpeechModelAvailability
import coredevices.util.transcription.SpokenLanguageOptions
import coredevices.util.transcription.platformModelNeedsDownload
import coredevices.util.transcription.platformModelState
import coredevices.util.transcription.spokenLanguageLabel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.crashlytics.crashlytics
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.database.entity.HRMonitoringInterval
import io.rebble.libpebblecommon.database.entity.HealthGender
import io.rebble.libpebblecommon.database.entity.QuietTimeSchedule
import io.rebble.libpebblecommon.database.entity.currentYear
import io.rebble.libpebblecommon.database.entity.watchReceivesWatchPrefs
import io.rebble.libpebblecommon.js.PKJSApp
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.permissions.PermissionPolicy
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.PluginRegistry
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import theme.CoreAppTheme
import theme.ThemeProvider
import theme.currentColorScheme
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import theme.coreOrange

enum class TopLevelType(val displayName: StringResource) {
    Phone(Res.string.settings_tab_phone),
    Watch(Res.string.settings_tab_watch),
    All(Res.string.settings_tab_all),
    Notifications(Res.string.notifications),
    ;

    fun icon(platform: Platform) = when (this) {
        Phone -> when (platform) {
            Platform.Android -> Icons.Default.PhoneAndroid
            Platform.IOS -> Icons.Default.PhoneIphone
        }
        Watch -> Icons.Default.Watch
        All -> Icons.AutoMirrored.Filled.List
        Notifications -> Icons.Default.Notifications
    }

    fun show(type: TopLevelType): Boolean = when (this) {
        All -> true
        else -> this == type
    }
}

enum class Section(val title: StringResource, val icon: ImageVector) {
    About(Res.string.settings_section_about, Icons.Default.Info),
    Support(Res.string.settings_section_support, Icons.Default.SupportAgent),
    Defaults(Res.string.settings_section_defaults, Icons.Default.Tune),
    QuickLaunch(Res.string.settings_section_quick_launch, Icons.Default.RocketLaunch), // watch only
    NotificationsWatch(Res.string.notifications, Icons.Default.Notifications), // watch only
    General(Res.string.settings_section_general, Icons.Default.Settings),
    Appearance(Res.string.settings_section_appearance, Icons.Default.Palette),
    Apps(Res.string.apps, Icons.Default.Apps),
    Plugins(Res.string.settings_section_plugins, Icons.Default.Power),
    Battery(Res.string.settings_section_battery, Icons.Default.BatteryFull),
    Calendar(Res.string.settings_section_calendar, Icons.Default.CalendarMonth),
    Health(Res.string.health, Icons.AutoMirrored.Filled.DirectionsRun),
    Speech(Res.string.settings_section_speech, Icons.Default.Mic),
    Display(Res.string.settings_section_display, Icons.Default.DarkMode), // watch only
    Themes(Res.string.settings_section_themes, Icons.Default.ColorLens), // watch only
    Weather(Res.string.settings_section_weather, Icons.Default.Cloud),
    Notifications(Res.string.notifications, Icons.Default.Notifications),
    Vibrations(Res.string.settings_section_vibrations, Icons.Default.Vibration), // watch only
    Time(Res.string.settings_section_time, Icons.Default.Schedule), // watch only
    Timeline(Res.string.settings_section_timeline, Icons.Default.Timeline), // watch only
    QuietTime(Res.string.settings_section_quiet_time, Icons.Default.DoNotDisturb),
    Connectivity(Res.string.settings_section_connectivity, Icons.Default.Wifi),
    Music(Res.string.settings_section_music, Icons.Default.MusicNote),
    Other(Res.string.settings_section_other, Icons.Default.MoreHoriz), // watch only
    Diagnostics(Res.string.settings_section_diagnostics, Icons.Default.Timeline),
    Debug(Res.string.settings_section_debug, Icons.Default.BugReport),
}

fun Section.navigatesDirectlyTo(): NavBarRoute? = when (this) {
    Section.Battery -> PebbleNavBarRoutes.BatterySettingsRoute
    else -> null
}

object SettingsIds {
    const val PermissionPolicy = "PermissionPolicy"
    const val OfflineSpeechRecognition = "OfflineSpeechRecognition"
    const val EnableHealthTracking = "EnableHealthTracking"
    const val EnableActivityInsights = "EnableActivityInsights"
    const val EnableSleepInsights = "EnableSleepInsights"
    const val EnableHealthPlatformSync = "EnableHealthPlatformSync"
    const val HealthHeight = "HealthHeight"
    const val HealthWeight = "HealthWeight"
    const val HealthAge = "HealthAge"
    const val HealthGenderId = "HealthGender"
    const val HealthImperialUnits = "HealthImperialUnits"
    const val HrmEnabled = "HrmEnabled"
    const val HrmMeasurementInterval = "HrmMeasurementInterval"
    const val HrmActivityTracking = "HrmActivityTracking"
}

enum class SettingsGroup(val title: StringResource) {
    Permissions(Res.string.settings_group_permissions),
    AppUpdates(Res.string.settings_group_app_updates),
    MenuScrolling(Res.string.settings_group_menu_scrolling),
    BacklightColor(Res.string.settings_group_backlight_color),
    BacklightMotion(Res.string.settings_group_backlight_motion),
    BacklightAmbient(Res.string.settings_group_ambient_light),
    Backlight(Res.string.settings_group_backlight),
    Vibration(Res.string.settings_group_vibration),
    Touch(Res.string.settings_group_touch),
    QuickView(Res.string.settings_group_quick_view),
    HeartRate(Res.string.settings_group_heart_rate),
    Insights(Res.string.settings_group_insights),
    YourDetails(Res.string.settings_group_your_details),
    PhoneEffects(Res.string.settings_group_phone_effects),
    CustomVibration(Res.string.settings_group_custom_vibration),
    Advanced(Res.string.settings_group_advanced),
    AutomaticFirmwareUpdatess(Res.string.settings_group_automatic_updates),
    AppUsage(Res.string.settings_group_app_usage),
}

/** A settings title or description: a string resource, or text that isn't translated (e.g. from libpebble3). */
interface SettingsText {
    val key: String

    @Composable
    fun text(): String
}

private class ResourceSettingsText(private val resource: StringResource, private val args: Array<out Any>) : SettingsText {
    override val key: String get() = resource.key

    @Composable
    override fun text(): String = stringResource(resource, *args)
}

private class PluralSettingsText(
    private val resource: PluralStringResource,
    private val quantity: Int,
    private val args: Array<out Any>,
) : SettingsText {
    override val key: String get() = resource.key

    @Composable
    override fun text(): String = pluralStringResource(resource, quantity, *args)
}

private class RawSettingsText(private val raw: String) : SettingsText {
    override val key: String get() = raw

    @Composable
    override fun text(): String = raw
}

fun SettingsText(resource: StringResource, vararg args: Any): SettingsText = ResourceSettingsText(resource, args)

fun SettingsText(resource: PluralStringResource, quantity: Int, vararg args: Any): SettingsText =
    PluralSettingsText(resource, quantity, args)

fun SettingsText(raw: String): SettingsText = RawSettingsText(raw)

data class SettingsItem(
    val id: String? = null,
    val title: SettingsText,
    val topLevelType: TopLevelType,
    val section: Section,
    /** Shown together, in a card under the group's heading. */
    val group: SettingsGroup? = null,
    val keywords: String = "",
    val show: () -> Boolean = { true },
    val badge: String? = null,
    private val item: @Composable () -> Unit,
    val isDebugSetting: Boolean,
    val onDisplayed: (@Composable () -> Unit)? = null,
) {
    val key: String get() = "${group?.name.orEmpty()}/${title.key}"

    /** Every word of [query] is somewhere in the title, keywords or group, so "activity insights" finds "Activity" in Insights. */
    fun matches(query: String, titleText: String, groupTitle: String?): Boolean {
        val text = listOfNotNull(titleText, keywords, groupTitle)
        return query.split(' ').filter { it.isNotBlank() }.all { word -> text.any { it.contains(word, ignoreCase = true) } }
    }

    @Composable
    fun Item() {
        onDisplayed?.invoke()
        item()
    }
}

/** [items] in order, each group gathered where its first item is, in a card under its heading. */
private fun LazyListScope.settingsItems(items: List<SettingsItem>) {
    items.groupBy { it.group ?: it.key }.values.forEach { run ->
        val group = run.first().group
        if (group == null) {
            items(items = run, key = { it.key }) { it.Item() }
            return@forEach
        }
        item(key = "group:${group.name}") {
            CardGroup(stringResource(group.title), run, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { it.Item() }
        }
    }
}

/** [items] in a rounded card under [title], as the settings screen groups its items. */
@Composable
internal fun <T> CardGroup(
    title: String?,
    items: List<T>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    dividerColor: Color = MaterialTheme.colorScheme.background,
    item: @Composable (T) -> Unit,
) {
    Column(modifier = modifier) {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = if (subtitle == null) 6.dp else 0.dp),
            )
        }
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = containerColor)) {
            // The items' ListItems paint `surface`, which would hide the card's colour.
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = containerColor)) {
                Column {
                    items.forEachIndexed { index, value ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = dividerColor)
                        item(value)
                    }
                }
            }
        }
    }
}

private val ELEVATION = 0.dp

@Composable
fun settingsBadgeTotal(): Int {
    val permissionRequester: PermissionRequester = koinInject()
    val missingPermissions by permissionRequester.missingPermissions.collectAsState()
    val coreConfigHolder: CoreConfigHolder = koinInject()
    val coreConfig by coreConfigHolder.config.collectAsState()
    val permissionBadgeCount = if (coreConfig.hidePermissionWarningBadges) 0 else missingPermissions.size
    val appUpdate: AppUpdate = koinInject()
    val updateState by appUpdate.updateAvailable.collectAsState()
    val updatesAvailable = when (updateState) {
        AppUpdateState.NoUpdateAvailable -> 0
        is AppUpdateState.UpdateAvailable -> 1
    }
    val appUpdateTracker: AppUpdateTracker = koinInject()
    val appWasUpdated by appUpdateTracker.appWasUpdated.collectAsState()
    val appUpdated = if (appWasUpdated && coreConfig.showAppUpdatedBadge) 1 else 0
    return permissionBadgeCount + updatesAvailable + appUpdated
}

private val logger = Logger.withTag("WatchSettingsScreen")

sealed interface RequestedLocalSTTMode {
    val mode: CactusSTTMode

    object Disabled : RequestedLocalSTTMode {
        override val mode = CactusSTTMode.RemoteOnly
    }

    data class Enabled(
        override val mode: CactusSTTMode,
        val modelName: String
    ) : RequestedLocalSTTMode
}

class WatchSettingsScreenViewModel : ViewModel() {
    val searchState = SearchState()
    var selectedTopLevelType by mutableStateOf(TopLevelType.Phone)
}

data class SettingsItemsState(
    val rawSettingsItems: List<SettingsItem>,
    val debugOptionsEnabled: Boolean,
    val anyWatchSupportsSettingsSync: Boolean,
    val coreConfig: CoreConfig,
    val healthTrackingEnabled: Boolean,
)

/** Shows the setting with this [SettingsItem.id], as on the settings screen. */
@Composable
fun SettingsItemsState?.Show(id: String) {
    if (this == null) {
        return
    }
    val setting = remember(this) {
        rawSettingsItems.find { it.id == id }
    }
    if (setting == null || !setting.show()) {
        return
    }
    setting.Item()
}

@Composable
fun rememberSettingsItemsState(navBarNav: NavBarNav?, snackbarDisplay: SnackbarDisplay): SettingsItemsState? {
    val libPebble = rememberLibPebble()
    val libPebbleConfig by libPebble.config.collectAsState()
    val coreConfigHolder: CoreConfigHolder = koinInject()
    val coreConfig by coreConfigHolder.config.collectAsState()
    val themeProvider: ThemeProvider = koinInject()
    val settings: Settings = koinInject()
    val currentTheme by themeProvider.theme.collectAsState()
    val pebbleFeatures = koinInject<PebbleFeatures>()
    val pebbleAccount = koinInject<PebbleAccount>()
    val loggedIn by pebbleAccount.loggedIn.collectAsState()
    val coreUser by Firebase.auth.authStateChanged.map {
        it?.emailOrNull
    }.distinctUntilChanged()
        .collectAsState(Firebase.auth.currentUser?.emailOrNull)
    val scope = rememberCoroutineScope()
    val appContext = koinInject<AppContext>()
    val appVersion = koinInject<CoreAppVersion>()
    val platform = koinInject<Platform>()
    val modelManager: ModelManager = koinInject()
    val nextBugReportContext: NextBugReportContext = koinInject()
    val appUpdate: AppUpdate = koinInject()
    val updateState by appUpdate.updateAvailable.collectAsState()
    val (showCopyTokenDialog, setShowCopyTokenDialog) = remember { mutableStateOf(false) }
    val coreBackgroundSync: CoreBackgroundSync = koinInject()
    if (showCopyTokenDialog) {
        PKJSCopyTokenDialog(onDismissRequest = { setShowCopyTokenDialog(false) })
    }
    var showHealthStatsDialog by remember { mutableStateOf(false) }
    val showFakeHealthDataDialog = remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }
    var debugOptionsEnabled by remember { mutableStateOf(settings.showDebugOptions()) }
    var pendingSTTModeDialog by remember { mutableStateOf<CactusSTTMode?>(null) }
    var showSpokenLanguageDialog by remember { mutableStateOf(false) }
    val recommendedSTTModel = modelManager.getRecommendedSTTModel()
    val modelDownloadState by modelManager.modelDownloadStatus.collectAsState()
    val platformSpeechRecognizer: PlatformSpeechRecognizer = koinInject()
    val platformSttAvailable by produceState(false) {
        value = withContext(Dispatchers.Default) { platformSpeechRecognizer.isAvailable() }
    }
    val platformDownloadStatus by platformSpeechRecognizer.downloadStatus.collectAsState()
    val platformModelAvailability by produceState(
        SpeechModelAvailability.Unsupported,
        coreConfig.sttConfig.spokenLanguage,
        platformDownloadStatus,
        platformSttAvailable,
    ) {
        value = if (platformSttAvailable) {
            withContext(Dispatchers.Default) {
                platformSpeechRecognizer.modelAvailability(coreConfig.sttConfig.spokenLanguage)
            }
        } else {
            SpeechModelAvailability.Unsupported
        }
    }
    val platformNeedsDownload = platformModelNeedsDownload(platformModelAvailability, platformDownloadStatus)
    if (showSpokenLanguageDialog) {
        SpokenLanguagePickerDialog(
            selectedCode = coreConfig.sttConfig.spokenLanguage,
            onLanguageSelected = { code ->
                coreConfigHolder.update(
                    coreConfig.copy(
                        sttConfig = coreConfig.sttConfig.copy(spokenLanguage = code)
                    )
                )
                platformSpeechRecognizer.releaseUnusedModels(code)
                showSpokenLanguageDialog = false
                if (coreConfig.sttConfig.mode == CactusSTTMode.PlatformOnly) {
                    scope.launch {
                        val availability = platformSpeechRecognizer.modelAvailability(code)
                        if (platformModelNeedsDownload(availability, platformDownloadStatus)) {
                            navBarNav?.navigateTo(CommonRoutes.SpeechModelDownloadDialog)
                        }
                    }
                }
            },
            onDismissRequest = { showSpokenLanguageDialog = false },
        )
    }
    pendingSTTModeDialog?.let { pendingSTTMode ->
        val recommendedModel by produceState<ModelInfo?>(null) {
            withContext(Dispatchers.Default) {
                val models = modelManager.getAvailableSTTModels()
                value = models.firstOrNull { it.slug == recommendedSTTModel.modelSlug }
                    ?: run {
                        snackbarDisplay.showSnackbar(getString(Res.string.settings_error_try_again))
                        logger.e { "Recommended model $recommendedSTTModel not found in available models: ${models.map { it.slug }}" }
                        pendingSTTModeDialog = null
                        null
                    }
            }
        }
        val recommendedModelFinal = recommendedModel
        if (recommendedModelFinal == null) {
            return@let
        }
        ModelDownloadPromptDialog(
            isLite = recommendedSTTModel is RecommendedModel.Lite,
            downloadSizeInMb = recommendedModelFinal.sizeInMB,
            onGetRecommended = {
                scope.launch {
                    if (!modelManager.downloadSTTModel(recommendedModelFinal, allowMetered = true)) {
                        snackbarDisplay.showSnackbar(getString(Res.string.settings_error_starting_download))
                        logger.e { "Failed to start download for recommended model ${recommendedModelFinal.slug}" }
                    } else {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                sttConfig = STTConfig(
                                    mode = pendingSTTMode,
                                    modelName = recommendedModelFinal.slug
                                )
                            )
                        )
                    }
                    pendingSTTModeDialog = null
                }
            },
            onDismiss = {
                pendingSTTModeDialog = null
            }
        )
    }
    if (showHealthStatsDialog) {
        HealthStatsDialog(
            libPebble = libPebble,
            onDismissRequest = { showHealthStatsDialog = false },
        )
    }
    ConfirmDialog(
        show = showFakeHealthDataDialog,
        title = "Populate fake health data",
        text = "This will wipe all existing health data and replace it with 30 days of fake data. This cannot be undone.",
        confirmText = "Wipe & Populate",
        onConfirm = {
            scope.launch { libPebble.populateDebugHealthData() }
        },
    )
    if (showSignInDialog) {
        SignInDialog(
            onDismiss = { showSignInDialog = false }
        )
    }
    val modelDownloadStatus by modelManager.modelDownloadStatus.collectAsState()
    LaunchedEffect(modelDownloadStatus) {
        when (modelDownloadStatus) {
            is ModelDownloadStatus.Failed -> {
                snackbarDisplay.showSnackbar(getString(Res.string.settings_model_download_failed))
            }
            else -> {}
        }
    }
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    val permissionRequester: PermissionRequester = koinInject()
    val missingPermissions by permissionRequester.missingPermissions.collectAsState()
    val uiContext = rememberUiContext()
    val analyticsBackend: AnalyticsBackend = koinInject()
    val usersDao: UsersDao = koinInject()
    val enableFirebase = remember { mutableStateOf(settings.getBoolean(KEY_ENABLE_FIREBASE_UPLOADS, true)) }
    val enableMemfault = remember { mutableStateOf(settings.getBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)) }
    val enableMixpanel = remember { mutableStateOf(settings.getBoolean(KEY_ENABLE_MIXPANEL_UPLOADS, true)) }
    val enableExperimentalDevices: EnableExperimentalDevices = koinInject()
    val experimentalDevices by enableExperimentalDevices.enabled.collectAsState()
    val appUpdateTracker: AppUpdateTracker = koinInject()
    val showChangelogBadge = remember { appUpdateTracker.appWasUpdated.value }
    val hasOfflineModels by produceState(false) {
        withContext(Dispatchers.Default) {
            value = modelManager.getDownloadedModelSlugs().any { it.startsWith("parakeet", false) }
        }
    }
    val cactusSupported = remember { isCactusSupported() }
    val bootConfigProvider: BootConfigProvider = koinInject()
    val rebbleVoiceAvailable by produceState(false, loggedIn) {
        value = withContext(Dispatchers.Default) {
            loggedIn != null && (bootConfigProvider.getBootConfig()?.config?.voice?.languages?.isNotEmpty() == true)
        }
    }
    val healthSettingsNullable by libPebble.healthSettings.collectAsState(null)
    val healthSettings = healthSettingsNullable ?: return null
    val thisYear = remember { currentYear() }
    val weatherFetcher: WeatherFetcher = koinInject()
    val watches by libPebble.watches.collectAsState(null)
    val watchesCastable = watches ?: return null
    val anyWatchSupportsSettingsSync = remember(watchesCastable) {
        watchesCastable.any {
            it is KnownPebbleDevice && it.capabilities.contains(
                ProtocolCapsFlag.SupportsBlobDbVersion
            )
        }
    }
    val anyWatchNeedsPhoneSideDnd = remember(watchesCastable, libPebbleConfig) {
        watchesCastable.any {
            it is KnownPebbleDevice && !watchReceivesWatchPrefs(it.capabilities, libPebbleConfig)
        }
    }
    val anyWatchSupportsFiveMinHrm = remember(watchesCastable) {
        watchesCastable.any {
            if (it !is KnownPebbleDevice) return@any false
            val fw = FirmwareVersion.from(
                tag = it.runningFwVersion,
                isRecovery = false,
                gitHash = "",
                timestamp = Instant.DISTANT_PAST,
                isDualSlot = false,
                isSlot0 = false,
            ) ?: return@any false
            HRMonitoringInterval.FiveMin.isSupportedBy(fw)
        }
    }
    val watchPrefs = watchPrefs()
    val coreAnalytics: CoreAnalytics = koinInject()
    val platformHealthSync: PlatformHealthSync = koinInject()
    val healthSyncTracker: HealthSyncTracker = koinInject()
    val healthPlatformSyncEnabled by healthSyncTracker.enabled.collectAsState()
    val healthIsSyncing by platformHealthSync.syncing.collectAsState()
    val healthPlatformAvailable by produceState(false) {
        value = withContext(Dispatchers.Default) { platformHealthSync.isAvailable() }
    }
    val preferredSources by libPebble.preferredSourcePlugins.collectAsState()

    val rawSettingsItems = remember(
            libPebbleConfig,
            preferredSources,
            debugOptionsEnabled,
            missingPermissions,
            updateState,
            enableFirebase,
            enableMemfault,
            enableMixpanel,
            coreConfig,
            experimentalDevices,
            loggedIn,
            watchPrefs,
            rebbleVoiceAvailable,
            platformSttAvailable,
            anyWatchSupportsFiveMinHrm,
            anyWatchNeedsPhoneSideDnd,
            healthPlatformAvailable,
        ) {
            listOfNotNull(
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_app_update_available),
                    description = SettingsText(Res.string.settings_app_update_available_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.About,
                    action = {
                        val update = updateState as? AppUpdateState.UpdateAvailable
                        if (uiContext != null && update != null) {
                            appUpdate.startUpdateFlow(uiContext, update.update)
                        }
                    },
                    badge = when (updateState) {
                        AppUpdateState.NoUpdateAvailable -> null
                        is AppUpdateState.UpdateAvailable -> "1"
                    },
                    show = { updateState is AppUpdateState.UpdateAvailable },
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_group_permissions),
                    description = if (missingPermissions.isEmpty()) {
                        SettingsText(Res.string.settings_permissions_all_granted)
                    } else if (missingPermissions.size == 1) {
                        SettingsText(Res.string.settings_permission_missing, missingPermissions.first())
                    } else {
                        SettingsText(Res.plurals.settings_permissions_missing, missingPermissions.size, missingPermissions.size)
                    },
                    topLevelType = TopLevelType.Phone,
                    section = Section.About,
                    action = if (missingPermissions.isNotEmpty()) {
                        {
                            nav.navigateTo(PebbleNavBarRoutes.PermissionsRoute)
                        }
                    } else null,
                    badge = if (missingPermissions.isEmpty() || coreConfig.hidePermissionWarningBadges) null else "${missingPermissions.size}",
                ) },
                SettingsItem(
                    title = SettingsText(Res.string.settings_app_version),
                    topLevelType = TopLevelType.Phone,
                    section = Section.About,
                    item = {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_app_version_value, appVersion.version))
                            },
                            shadowElevation = ELEVATION,
                        )
                    },
                    isDebugSetting = false,
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_whats_new_app),
                    topLevelType = TopLevelType.Phone,
                    section = Section.About,
                    action = {
                        nav.navigateTo(CommonRoutes.RoadmapChangelogRoute)
                    },
                    actionIcon = Icons.AutoMirrored.Default.Launch,
                    badge = if (showChangelogBadge && coreConfig.showAppUpdatedBadge) "1" else null,
                    onDisplayed = {
                        LaunchedEffect(Unit) {
                            appUpdateTracker.acknowledgeCurrentVersion()
                        }
                    },
                ) },
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_show_update_badge),
                    topLevelType = TopLevelType.Phone,
                    section = Section.About,
                    checked = coreConfig.showAppUpdatedBadge,
                    onCheckChanged = {
                        coreConfigHolder.update(coreConfig.copy(showAppUpdatedBadge = it))
                    },
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_whats_new_pebbleos),
                    topLevelType = TopLevelType.Phone,
                    section = Section.About,
                    action = {
                        nav.navigateTo(CommonRoutes.PebbleOsChangelogRoute)
                    },
                    actionIcon = Icons.AutoMirrored.Default.Launch,
                ) },
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_getting_started),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Support,
                    action = {
                        nav.navigateTo(CommonRoutes.TroubleshootingRoute)
                    },
                    actionIcon = Icons.AutoMirrored.Default.Launch,
                ) },
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_new_bug_report),
                    description = SettingsText(Res.string.settings_new_bug_report_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Support,
                    action = {
                        nextBugReportContext.nextContext = null
                        nav.navigateTo(
                            CommonRoutes.BugReport(
                                pebble = true,
                            )
                        )
                    },
                ) },
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_view_bug_reports),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Support,
                    action = {
                        nav.navigateTo(CommonRoutes.ViewMyBugReportsRoute)
                    },
                ) },
                basicSettingsDropdownItem(
                    title = SettingsText(Res.string.settings_app_theme),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Appearance,
                    keywords = "dark light system",
                    selectedItem = currentTheme,
                    items = CoreAppTheme.entries,
                    onItemSelected = {
                        themeProvider.setTheme(it)
                    },
                    itemText = {
                        stringResource(it.resource)
                    },
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_bottom_bar),
                    description = SettingsText(Res.string.settings_bottom_bar_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Appearance,
                    keywords = "tabs navigation health notifications devices apps index",
                    action = { nav.navigateTo(PebbleNavBarRoutes.NavBarTabsRoute) },
                ) },
                basicSettingsToggleItem(
                    id = SettingsIds.HealthImperialUnits,
                    title = SettingsText(Res.string.settings_imperial_units),
                    description = SettingsText(Res.string.settings_imperial_units_desc),
                    keywords = "weather health degrees celsius temperature miles",
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    checked = healthSettings.imperialUnits,
                    onCheckChanged = {
                        GlobalScope.launch {
                            libPebble.updateImperialUnits(it)
                        }
                    },
                ),
                basicSettingsDropdownItem(
                    title = SettingsText(Res.string.settings_background_refresh),
                    description = SettingsText(Res.string.settings_background_refresh_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    selectedItem = RegularSyncInterval.from(coreConfig.regularSyncInterval),
                    items = RegularSyncInterval.entries,
                    onItemSelected = {
                        coreBackgroundSync.updateFullSyncPeriod(it.period)
                    },
                    itemText = {
                        stringResource(it.displayName)
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_enable_index_feed),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    checked = coreConfig.enableIndex,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                enableIndex = it,
                                indexPermissionsConfirmed = if (it) coreConfig.indexPermissionsConfirmed else false,
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_notify_fully_charged),
                    description = SettingsText(Res.string.settings_notify_fully_charged_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    checked = coreConfig.notifyWatchFullyCharged,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                notifyWatchFullyCharged = it
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_auto_update_pebbleos),
                    description = SettingsText(Res.string.settings_auto_update_pebbleos_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    group = SettingsGroup.AutomaticFirmwareUpdatess,
                    keywords = "firmware pebbleos update automatic",
                    checked = coreConfig.autoFirmwareUpdate,
                    onCheckChanged = {
                        coreConfigHolder.update(coreConfig.copy(autoFirmwareUpdate = it))
                    },
                ),
                SettingsItem(
                    title = SettingsText(Res.string.settings_time_window),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    group = SettingsGroup.AutomaticFirmwareUpdatess,
                    keywords = "firmware pebbleos update automatic time window",
                    show = { coreConfig.autoFirmwareUpdate },
                    isDebugSetting = false,
                    item = {
                        val window = coreConfig.autoFirmwareUpdateWindow
                        ListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_time_window_only))
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = window != null,
                                    onCheckedChange = {
                                        coreConfigHolder.update(
                                            coreConfig.copy(
                                                autoFirmwareUpdateWindow = if (it) QuietTimeSchedule(2, 0, 5, 0) else null
                                            )
                                        )
                                    },
                                )
                            },
                            supportingContent = {
                                if (window != null) {
                                    SelectTimeRange(
                                        schedule = window,
                                        onChangeSchedule = {
                                            coreConfigHolder.update(coreConfig.copy(autoFirmwareUpdateWindow = it))
                                        },
                                    )
                                }
                            },
                            shadowElevation = ELEVATION,
                        )
                    },
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_quick_replies),
                    description = SettingsText(Res.string.settings_quick_replies_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    action = {
                        nav.navigateTo(PebbleNavBarRoutes.CannedRepliesRoute)
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ) },
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_always_send_notifications),
                    description = SettingsText(Res.string.settings_always_send_notifications_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    checked = libPebbleConfig.notificationConfig.alwaysSendNotifications,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    alwaysSendNotifications = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_respect_dnd_legacy),
                    description = SettingsText(Res.string.settings_respect_dnd_legacy_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    checked = libPebbleConfig.notificationConfig.respectDoNotDisturb,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    respectDoNotDisturb = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() && anyWatchNeedsPhoneSideDnd },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_mute_notification_effects),
                    description = SettingsText(Res.string.settings_mute_notification_effects_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    group = SettingsGroup.PhoneEffects,
                    checked = libPebbleConfig.notificationConfig.mutePhoneNotificationSoundsWhenConnected,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    mutePhoneNotificationSoundsWhenConnected = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationHints() },

                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_mute_call_effects),
                    description = SettingsText(Res.string.settings_mute_call_effects_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    group = SettingsGroup.PhoneEffects,
                    checked = libPebbleConfig.notificationConfig.mutePhoneCallSoundsWhenConnected,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    mutePhoneCallSoundsWhenConnected = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationHints() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_answer_calls_on_speaker),
                    description = SettingsText(Res.string.settings_answer_calls_on_speaker_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    group = SettingsGroup.PhoneEffects,
                    checked = libPebbleConfig.notificationConfig.answerCallsFromWatchOnSpeaker,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    answerCallsFromWatchOnSpeaker = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationHints() },
                ),
                SettingsItem(
                    title = SettingsText(Res.string.settings_vibe_pattern_title),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    group = SettingsGroup.CustomVibration,
                    show = { pebbleFeatures.supportsVibePatterns() },
                    item = {
                        SelectVibePatternOrNone(
                            currentPattern = libPebbleConfig.notificationConfig.overrideDefaultVibePattern,
                            onChangePattern = { pattern ->
                                libPebble.updateConfig(
                                    libPebbleConfig.copy(
                                        notificationConfig = libPebbleConfig.notificationConfig.copy(
                                            overrideDefaultVibePattern = pattern?.name
                                        )
                                    )
                                )
                            },
                            subtext = stringResource(Res.string.settings_vibe_override_default),
                        )
                    },
                    isDebugSetting = false,
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_use_os_vibe_patterns),
                    description = SettingsText(Res.string.settings_use_os_vibe_patterns_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    group = SettingsGroup.CustomVibration,
                    checked = libPebbleConfig.notificationConfig.useAndroidVibePatterns,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    useAndroidVibePatterns = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsVibePatterns() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_send_notification_images),
                    description = SettingsText(Res.string.settings_send_notification_images_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    checked = libPebbleConfig.notificationConfig.sendNotificationImages,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    sendNotificationImages = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationImages() },
                ),
                basicSettingsNumberItem(
                    title = SettingsText(Res.string.settings_store_notifications),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    description = SettingsText(Res.string.settings_store_notifications_desc),
                    value = libPebbleConfig.notificationConfig.storeNotifiationsForDays.toLong(),
                    onValueChange = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    storeNotifiationsForDays = it.toInt()
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                    min = 0,
                    max = 7,
                    unit = SettingsText(Res.string.settings_unit_days),
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_send_local_only),
                    description = SettingsText(Res.string.settings_send_local_only_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    group = SettingsGroup.Advanced,
                    checked = libPebbleConfig.notificationConfig.sendLocalOnlyNotifications,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    sendLocalOnlyNotifications = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_shows_ui_actions),
                    description = SettingsText(Res.string.settings_shows_ui_actions_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    group = SettingsGroup.Advanced,
                    checked = libPebbleConfig.notificationConfig.addShowsUserInterfaceActions,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    addShowsUserInterfaceActions = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_store_disabled),
                    description = SettingsText(Res.string.settings_store_disabled_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Notifications,
                    group = SettingsGroup.Advanced,
                    checked = libPebbleConfig.notificationConfig.storeDisabledNotifications,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    storeDisabledNotifications = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Show legacy watches in BLE scan"),
                    description = SettingsText("Allow Aplite/Basalt/Chalk watches to appear in BLE scan results. By default these only show via the Bluetooth Classic scan option."),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.watchConfig.allowLegacyWatchesInBleScan,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    allowLegacyWatchesInBleScan = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                    show = { pebbleFeatures.supportsBtClassic() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_disable_cdm),
                    description = SettingsText(Res.string.settings_disable_cdm_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = coreConfig.disableCompanionDeviceManager,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                disableCompanionDeviceManager = it,
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsCompanionDeviceManager() },
                ),

                basicSettingsToggleItem(
                    title = SettingsText("Ignore Missing PRF"),
                    description = SettingsText("Ignore missing PRF when connecting to development watches"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.watchConfig.ignoreMissingPrf,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    ignoreMissingPrf = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Use reversed PPoG"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.bleConfig.legacyReversedPPoG,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    legacyReversedPPoG = it
                                )
                            )
                        )
                    },
                    show = { false },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_enable_calendar),
                    description = SettingsText(Res.string.settings_enable_calendar_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Calendar,
                    checked = libPebbleConfig.watchConfig.calendarPins,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    calendarPins = it
                                )
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_calendar_reminders),
                    description = SettingsText(Res.string.settings_calendar_reminders_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Calendar,
                    checked = libPebbleConfig.watchConfig.calendarReminders,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    calendarReminders = it
                                )
                            )
                        )
                    },
                    show = { libPebbleConfig.watchConfig.calendarPins },
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_calendars),
                    description = SettingsText(Res.string.settings_calendars_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Calendar,
                    action = {
                        nav.navigateTo(PebbleNavBarRoutes.CalendarsRoute)
                    },
                    show = { libPebbleConfig.watchConfig.calendarPins },
                ) },
                SettingsItem(
                    title = SettingsText(Res.string.settings_reminder_vibe_pattern),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Calendar,
                    show = {
                        libPebbleConfig.watchConfig.calendarPins &&
                                libPebbleConfig.watchConfig.calendarReminders &&
                                pebbleFeatures.supportsVibePatterns()
                    },
                    item = {
                        SelectVibePatternOrNone(
                            currentPattern = libPebbleConfig.watchConfig.overrideCalendarVibePattern,
                            onChangePattern = { pattern ->
                                libPebble.updateConfig(
                                    libPebbleConfig.copy(
                                        watchConfig = libPebbleConfig.watchConfig.copy(
                                            overrideCalendarVibePattern = pattern?.name
                                        )
                                    )
                                )
                            },
                            subtext = stringResource(Res.string.settings_vibe_override_default),
                            title = stringResource(Res.string.settings_reminder_vibe_pattern),
                        )
                    },
                    isDebugSetting = false,
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_declined_events),
                    description = SettingsText(Res.string.settings_declined_events_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Calendar,
                    checked = libPebbleConfig.watchConfig.calendarShowDeclinedEvents,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    calendarShowDeclinedEvents = it
                                )
                            )
                        )
                    },
                    show = { libPebbleConfig.watchConfig.calendarPins },
                ),
                basicSettingsToggleItem(
                    id = EnableHealthTracking,
                    title = SettingsText(Res.string.settings_enable_health_tracking),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    checked = healthSettings.trackingEnabled,
                    onCheckChanged = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(
                                trackingEnabled = it
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    id = SettingsIds.HrmEnabled,
                    title = SettingsText(Res.string.settings_heart_rate_monitor),
                    description = SettingsText(Res.string.settings_heart_rate_monitor_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.HeartRate,
                    checked = healthSettings.hrmEnabled,
                    show = { healthSettings.trackingEnabled },
                    onCheckChanged = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(hrmEnabled = it)
                        )
                    },
                ),
                basicSettingsDropdownItem(
                    id = SettingsIds.HrmMeasurementInterval,
                    title = SettingsText(Res.string.settings_hr_background_sampling),
                    description = SettingsText(Res.string.settings_hr_background_sampling_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.HeartRate,
                    selectedItem = healthSettings.hrmMeasurementInterval,
                    selectorBelow = true,
                    items = HRMonitoringInterval.entries.filter {
                        it != HRMonitoringInterval.FiveMin || anyWatchSupportsFiveMinHrm
                    },
                    onItemSelected = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(hrmMeasurementInterval = it)
                        )
                    },
                    itemText = {
                        when (it) {
                            HRMonitoringInterval.FiveMin -> stringResource(Res.string.settings_hr_every_5_min)
                            HRMonitoringInterval.TenMin -> stringResource(Res.string.settings_hr_every_10_min)
                            HRMonitoringInterval.ThirtyMin -> stringResource(Res.string.settings_hr_every_30_min)
                            HRMonitoringInterval.OneHour -> stringResource(Res.string.settings_hr_every_hour)
                            HRMonitoringInterval.Disabled -> stringResource(Res.string.settings_hr_off)
                        }
                    },
                    show = { healthSettings.trackingEnabled && healthSettings.hrmEnabled },
                ),
                basicSettingsToggleItem(
                    id = SettingsIds.HrmActivityTracking,
                    title = SettingsText(Res.string.settings_hr_during_activities),
                    description = SettingsText(Res.string.settings_hr_during_activities_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.HeartRate,
                    checked = healthSettings.hrmActivityTrackingEnabled,
                    show = { healthSettings.trackingEnabled && healthSettings.hrmEnabled },
                    onCheckChanged = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(hrmActivityTrackingEnabled = it)
                        )
                    },
                ),
                basicSettingsToggleItem(
                    id = EnableActivityInsights,
                    title = SettingsText(Res.string.settings_insights_activity),
                    description = SettingsText(Res.string.settings_insights_activity_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.Insights,
                    checked = healthSettings.activityInsightsEnabled,
                    show = { healthSettings.trackingEnabled },
                    onCheckChanged = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(
                                activityInsightsEnabled = it
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    id = EnableSleepInsights,
                    title = SettingsText(Res.string.settings_insights_sleep),
                    description = SettingsText(Res.string.settings_insights_sleep_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.Insights,
                    checked = healthSettings.sleepInsightsEnabled,
                    show = { healthSettings.trackingEnabled },
                    onCheckChanged = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(
                                sleepInsightsEnabled = it
                            )
                        )
                    },
                ),
                basicSettingsNumberFieldItem(
                    id = SettingsIds.HealthHeight,
                    title = SettingsText(Res.string.settings_height),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.YourDetails,
                    description = SettingsText(Res.string.settings_height_desc),
                    value = if (healthSettings.imperialUnits) (healthSettings.heightMm / 25.4).roundToLong()
                            else (healthSettings.heightMm / 10).toLong(),
                    onValueChange = { v ->
                        val mm = if (healthSettings.imperialUnits) (v * 25.4).roundToLong().toShort()
                                 else (v * 10).toShort()
                        libPebble.updateHealthSettings(healthSettings.copy(heightMm = mm))
                    },
                    show = { healthSettings.trackingEnabled },
                    min = if (healthSettings.imperialUnits) 39 else 100,
                    max = if (healthSettings.imperialUnits) 87 else 220,
                    unit = SettingsText(if (healthSettings.imperialUnits) Res.string.settings_unit_in else Res.string.settings_unit_cm),
                    valueFormatter = if (healthSettings.imperialUnits) {
                        { v -> "${v / 12}' ${v % 12}\"" }
                    } else null,
                ),
                basicSettingsNumberFieldItem(
                    id = SettingsIds.HealthWeight,
                    title = SettingsText(Res.string.settings_weight),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.YourDetails,
                    description = SettingsText(Res.string.settings_weight_desc),
                    value = if (healthSettings.imperialUnits) (healthSettings.weightDag / 45.359).roundToLong()
                            else (healthSettings.weightDag / 100).toLong(),
                    onValueChange = { v ->
                        val dag = if (healthSettings.imperialUnits) (v * 45.359).roundToLong().toShort()
                                  else (v * 100).toShort()
                        libPebble.updateHealthSettings(healthSettings.copy(weightDag = dag))
                    },
                    show = { healthSettings.trackingEnabled },
                    min = if (healthSettings.imperialUnits) 66 else 30,
                    max = if (healthSettings.imperialUnits) 441 else 200,
                    unit = SettingsText(if (healthSettings.imperialUnits) Res.string.settings_unit_lb else Res.string.settings_unit_kg),
                ),
                basicSettingsNumberFieldItem(
                    id = SettingsIds.HealthAge,
                    title = SettingsText(Res.string.settings_year_of_birth),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.YourDetails,
                    description = SettingsText(Res.string.settings_year_of_birth_desc),
                    keywords = "age",
                    value = (healthSettings.birthYear ?: (thisYear - healthSettings.ageYears)).toLong(),
                    onValueChange = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(birthYear = it.toInt())
                        )
                    },
                    show = { healthSettings.trackingEnabled },
                    min = thisYear - 120,
                    max = thisYear - 1,
                    unit = null,
                ),
                basicSettingsDropdownItem(
                    id = SettingsIds.HealthGenderId,
                    title = SettingsText(Res.string.settings_gender),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    group = SettingsGroup.YourDetails,
                    selectedItem = healthSettings.gender,
                    items = HealthGender.entries,
                    onItemSelected = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(gender = it)
                        )
                    },
                    itemText = {
                        when (it) {
                            HealthGender.Female -> stringResource(Res.string.settings_gender_female)
                            HealthGender.Male -> stringResource(Res.string.settings_gender_male)
                            HealthGender.Other -> stringResource(Res.string.settings_gender_other)
                        }
                    },
                    show = { healthSettings.trackingEnabled },
                ),
                basicSettingsToggleItem(
                    id = EnableHealthPlatformSync,
                    title = SettingsText(if (platform == Platform.IOS) Res.string.settings_sync_apple_health else Res.string.settings_sync_health_connect),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    checked = healthPlatformSyncEnabled,
                    description = SettingsText(Res.string.settings_gender_desc),
                    show = { healthSettings.trackingEnabled && healthPlatformAvailable },
                    onCheckChanged = { enabled ->
                        scope.launch {
                            if (enabled) {
                                val granted = platformHealthSync.requestPermissions()
                                if (!granted) {
                                    logger.w { "Health platform permissions not granted" }
                                    return@launch
                                }
                            } else {
                                healthSyncTracker.setEnabled(false)
                            }
                        }
                    },
                ),
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_health_sync_now),
                    description = SettingsText(if (healthIsSyncing) Res.string.settings_health_syncing else Res.string.settings_health_sync_now_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    show = { healthPlatformSyncEnabled },
                    action = if (healthIsSyncing) {
                        null
                    } else {
                        {
                            scope.launch {
                                platformHealthSync.sync()
                            }
                        }
                    },
                ),
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_open_google_fit),
                    description = SettingsText(Res.string.settings_open_google_fit_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    show = { healthPlatformSyncEnabled && platform == Platform.Android },
                    action = {
                        openGoogleFitApp(uiContext)
                    },
                ),
                basicSettingsActionItem(
                    title = SettingsText("View debug stats"),
                    description = SettingsText("Health statistics and averages"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    keywords = "health steps sleep stats debug",
                    action = {
                        showHealthStatsDialog = true
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsActionItem(
                    title = SettingsText("Populate fake health data"),
                    description = SettingsText("Warning: this will wipe all existing health data!"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Health,
                    keywords = "health debug fake data populate",
                    action = {
                        showFakeHealthDataDialog.value = true
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_enable_weather),
                    description = SettingsText(Res.string.settings_enable_weather_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Weather,
                    checked = coreConfig.fetchWeather,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                fetchWeather = it,
                            )
                        )
                        GlobalScope.launch { weatherFetcher.fetchWeather(this) }
                    },
                ),
                run {
                    // Any plugin but the built-in one, which shows what this fetches.
                    val providers = libPebble.getAllPlugins().filter {
                        it.serves(WeatherPlugin.CATEGORY, WeatherPlugin.ITEM_LOCATION) &&
                            it.pluginUuid != WeatherPlugin.BUILT_IN_WEATHER_UUID
                    }
                    val chosen = preferredSources[PluginRegistry.sourceKey(WeatherPlugin.CATEGORY, WeatherPlugin.ITEM_LOCATION)]
                    basicSettingsDropdownItem(
                        title = SettingsText(Res.string.settings_weather_provider),
                        description = SettingsText(Res.string.settings_weather_provider_desc),
                        topLevelType = TopLevelType.Phone,
                        section = Section.Weather,
                        items = listOf<PluginMetadata?>(null) + providers,
                        selectedItem = providers.firstOrNull { it.pluginUuid == chosen },
                        onItemSelected = {
                            libPebble.setPreferredSourcePlugin(WeatherPlugin.CATEGORY, WeatherPlugin.ITEM_LOCATION, it?.pluginUuid)
                            GlobalScope.launch { weatherFetcher.fetchWeather(this) }
                        },
                        itemText = { it?.let { plugin -> stringResource(Res.string.settings_weather_provider_plugin, plugin.name) } ?: "Pebble" },
                        show = { coreConfig.fetchWeather && providers.isNotEmpty() },
                        selectorBelow = true,
                    )
                },
                basicSettingsDropdownItem(
                    title = SettingsText(Res.string.settings_weather_refresh),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Weather,
                    selectedItem = WeatherSyncInterval.from(coreConfig.weatherSyncInterval),
                    items = WeatherSyncInterval.entries,
                    onItemSelected = {
                        coreBackgroundSync.updateWeatherSyncPeriod(it.period)
                    },
                    itemText = {
                        stringResource(it.displayName)
                    },
                    show = { coreConfig.fetchWeather }
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_weather_pins),
                    description = SettingsText(Res.string.settings_weather_pins_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Weather,
                    checked = coreConfig.weatherPinsV2,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                weatherPinsV2 = it,
                            )
                        )
                        GlobalScope.launch { weatherFetcher.fetchWeather(this) }
                    },
                    show = { coreConfig.fetchWeather }
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_weather_locations),
                    description = SettingsText(Res.string.settings_weather_locations_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Weather,
                    action = {
                        nav.navigateTo(PebbleNavBarRoutes.WeatherRoute)
                    },
                    show = { coreConfig.fetchWeather }
                ) },
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_lan_dev_connection),
                    description = SettingsText(Res.string.settings_lan_dev_connection_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.watchConfig.lanDevConnection,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    lanDevConnection = it
                                )
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_show_debug_options),
                    description = SettingsText(Res.string.settings_show_debug_options_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    checked = debugOptionsEnabled,
                    onCheckChanged = {
                        settings.set(SHOW_DEBUG_OPTIONS_SETTINGS_KEY, it)
                        debugOptionsEnabled = it
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText("PKJS Debugger"),
                    description = SettingsText("Allow connection via the ${if (platform == Platform.Android) "Chrome" else "Safari"} remote inspector to debug PKJS apps. Restart watchapp after changing."),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    checked = libPebbleConfig.watchConfig.pkjsInspectable,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    pkjsInspectable = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText("PKJS requests through app instead of Webview"),
                    description = SettingsText("Watchapps' XMLHttpRequests go through the app, as on iOS, so their data usage is counted. Otherwise the WebView makes them itself. Restart watchapp after changing."),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    checked = libPebbleConfig.watchConfig.pkjsRequestsThroughApp,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    pkjsRequestsThroughApp = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                    show = { platform == Platform.Android },
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Watch settings sync"),
                    description = SettingsText("Only for debugging - disables syncing settings with watch when disabled"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    checked = libPebbleConfig.watchConfig.enableWatchSettingsSync,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    enableWatchSettingsSync = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsDropdownItem(
                    id = OfflineSpeechRecognition,
                    title = SettingsText(Res.string.settings_offline_stt),
                    keywords = "cactus stt speech recognition offline rebble",
                    topLevelType = TopLevelType.Phone,
                    section = Section.Speech,
                    selectorBelow = true,
                    items = CactusSTTMode.entries.filter { mode ->
                        when (mode) {
                            CactusSTTMode.RebbleOnly,
                            CactusSTTMode.RebbleFirst,
                            CactusSTTMode.RebbleFallback -> rebbleVoiceAvailable
                            CactusSTTMode.PlatformOnly -> platformSttAvailable
                            else -> true
                        }
                    },
                    selectedItem = coreConfig.sttConfig.mode,
                    onItemSelected = {
                        val isRebble = it == CactusSTTMode.RebbleOnly ||
                                it == CactusSTTMode.RebbleFirst ||
                                it == CactusSTTMode.RebbleFallback
                        val isPlatform = it == CactusSTTMode.PlatformOnly
                        val needsLocal = it == CactusSTTMode.LocalOnly ||
                                it == CactusSTTMode.LocalFirst ||
                                it == CactusSTTMode.RebbleFirst ||
                                it == CactusSTTMode.RebbleFallback
                        if (isRebble && !rebbleVoiceAvailable) {
                            scope.launch { snackbarDisplay.showSnackbar(getString(Res.string.settings_stt_rebble_requires_subscription)) }
                            showSignInDialog = true
                        } else if (isPlatform && !platformSttAvailable) {
                            scope.launch { snackbarDisplay.showSnackbar(getString(Res.string.settings_stt_no_system_support)) }
                        } else if (it != CactusSTTMode.RemoteOnly && !isPlatform && !cactusSupported) {
                            scope.launch { snackbarDisplay.showSnackbar(getString(Res.string.settings_stt_no_local_support)) }
                        } else if (it != CactusSTTMode.LocalOnly && !isPlatform && !isRebble && coreUser == null) {
                            scope.launch { snackbarDisplay.showSnackbar(getString(Res.string.settings_stt_cloud_requires_sign_in)) }
                            showSignInDialog = true
                        } else if (needsLocal && !hasOfflineModels) {
                            pendingSTTModeDialog = it
                        } else {
                            coreConfigHolder.update(
                                coreConfig.copy(
                                    sttConfig = coreConfig.sttConfig.copy(
                                        mode = it
                                    )
                                )
                            )
                            if (isPlatform && uiContext != null) {
                                scope.launch {
                                    permissionRequester.requestPermission(
                                        Permission.SpeechRecognizer,
                                        uiContext,
                                    )
                                }
                            }
                            if (isPlatform && platformNeedsDownload) {
                                navBarNav?.navigateTo(CommonRoutes.SpeechModelDownloadDialog)
                            }
                        }
                    },
                    itemText = { mode ->
                        when (mode) {
                            CactusSTTMode.RemoteOnly -> stringResource(Res.string.settings_stt_cloud_only)
                            CactusSTTMode.RemoteFirst -> stringResource(Res.string.settings_stt_cloud_local_fallback)
                            CactusSTTMode.LocalOnly -> stringResource(Res.string.settings_stt_local_only)
                            CactusSTTMode.LocalFirst -> stringResource(Res.string.settings_stt_local_cloud_fallback)
                            CactusSTTMode.RebbleOnly -> stringResource(Res.string.settings_stt_rebble_only)
                            CactusSTTMode.RebbleFirst -> stringResource(Res.string.settings_stt_rebble_local_fallback)
                            CactusSTTMode.RebbleFallback -> stringResource(Res.string.settings_stt_local_rebble_fallback)
                            CactusSTTMode.PlatformOnly -> stringResource(Res.string.settings_stt_system)
                        }
                    },
                    extraSupportingContent = {
                        modelDownloadState.takeIf { it.inProgress }?.let { state ->
                            Column {
                                Text(
                                    text = stringResource(Res.string.settings_stt_downloading_background),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                (state as? ModelDownloadStatus.Downloading)?.progress?.let { progress ->
                                    CoreLinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    )
                                } ?: CoreLinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                )
                            }
                        }
                    },
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_manage_offline_models),
                    description = if (coreConfig.sttConfig.mode == CactusSTTMode.LocalOnly ||
                        coreConfig.sttConfig.mode == CactusSTTMode.RebbleFallback) {
                        SettingsText(Res.string.settings_offline_models_note)
                    } else {
                        null
                    },
                    keywords = "cactus stt speech recognition offline",
                    topLevelType = TopLevelType.Phone,
                    section = Section.Speech,
                    show = {
                        coreConfig.sttConfig.mode !in setOf(
                            CactusSTTMode.RemoteOnly,
                            CactusSTTMode.RebbleOnly,
                            CactusSTTMode.PlatformOnly,
                        ) || hasOfflineModels
                    },
                    action = {
                        nav.navigateTo(PebbleNavBarRoutes.OfflineModelsRoute)
                    },
                ) },
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_system_speech_model),
                    description = SettingsText(
                        "${spokenLanguageLabel(coreConfig.sttConfig.spokenLanguage)} · " +
                            platformModelState(platformModelAvailability, platformDownloadStatus)
                    ),
                    keywords = "system stt speech recognition model download language",
                    topLevelType = TopLevelType.Phone,
                    section = Section.Speech,
                    show = { coreConfig.sttConfig.mode == CactusSTTMode.PlatformOnly },
                    action = { nav.navigateTo(CommonRoutes.SpeechModelDownloadDialog) }
                        .takeIf { platformNeedsDownload },
                ) },
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_spoken_language),
                    description = SettingsText(spokenLanguageLabel(coreConfig.sttConfig.spokenLanguage)),
                    keywords = "language stt speech recognition locale iso",
                    topLevelType = TopLevelType.Phone,
                    section = Section.Speech,
                    action = { showSpokenLanguageDialog = true },
                ),
                SettingsItem(
                    title = SettingsText(Res.string.settings_cloud_recognition_provider),
                    isDebugSetting = false,
                    topLevelType = TopLevelType.Phone,
                    section = Section.Speech,
                    keywords = "",
                    item = {
                        val logo = if (currentColorScheme().isDark) {
                            Res.drawable.wispr_flow_logo_white
                        } else {
                            Res.drawable.wispr_flow_logo_black
                        }
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.settings_cloud_recognition_provider)) },
                            trailingContent = {
                                Image(
                                    painter = painterResource(logo),
                                    contentDescription = "Wispr Flow",
                                    modifier = Modifier.height(20.dp),
                                )
                            },
                            shadowElevation = ELEVATION,
                        )
                    }
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_ignore_other_pebble_apps),
                    description = SettingsText(Res.string.settings_ignore_other_pebble_apps_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = coreConfig.ignoreOtherPebbleApps,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                ignoreOtherPebbleApps = !coreConfig.ignoreOtherPebbleApps,
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsDetectingOtherPebbleApps() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_send_app_crashes),
                    description = SettingsText(Res.string.settings_send_app_crashes_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Diagnostics,
                    checked = enableFirebase.value,
                    onCheckChanged = {
                        enableFirebase.value = it
                        settings.set(KEY_ENABLE_FIREBASE_UPLOADS, it)
                        if (!it) {
                            coreAnalytics.logEvent("crashlytics_collection_disabled")
                        }
                        Firebase.crashlytics.setCrashlyticsCollectionEnabled(it)
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_send_watch_analytics),
                    description = SettingsText(Res.string.settings_send_watch_analytics_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Diagnostics,
                    checked = enableMemfault.value,
                    onCheckChanged = {
                        enableMemfault.value = it
                        if (!it) {
                            coreAnalytics.logEvent("memfault_collection_disabled")
                        }
                        settings.set(KEY_ENABLE_MEMFAULT_UPLOADS, it)
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_send_app_analytics),
                    description = SettingsText(Res.string.settings_send_app_analytics_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Diagnostics,
                    checked = enableMixpanel.value,
                    onCheckChanged = {
                        enableMixpanel.value = it
                        settings.set(KEY_ENABLE_MIXPANEL_UPLOADS, it)
                        if (!it) {
                            coreAnalytics.logEvent("mixpanel_collection_disabled")
                        }
                        analyticsBackend.setEnabled(it)
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_log_notifications),
                    description = SettingsText(Res.string.settings_log_notifications_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Diagnostics,
                    checked = libPebbleConfig.notificationConfig.dumpNotificationContent,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    dumpNotificationContent = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationLogging() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_log_sensitive),
                    description = SettingsText(Res.string.settings_log_sensitive_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Diagnostics,
                    checked = !libPebbleConfig.notificationConfig.obfuscateContent,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    obfuscateContent = !it
                                )
                            )
                        )
                        coreConfigHolder.update(
                            coreConfig.copy(
                                obfuscateSensitiveLogs = !it
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Verbose connection logging"),
                    description = SettingsText("Detailed connectivity state machine logging (please don't enable this unless we ask you to)"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Diagnostics,
                    checked = libPebbleConfig.watchConfig.verboseWatchManagerLogging,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    verboseWatchManagerLogging = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Verbose PPoG logging"),
                    description = SettingsText("Detailed Pebble Protocol over GATT logging (please don't enable this unless we ask you to)"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Diagnostics,
                    checked = libPebbleConfig.bleConfig.verbosePpogLogging,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    verbosePpogLogging = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_republish_gatt),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.bleConfig.republishGattServicesOnRestore,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    republishGattServicesOnRestore = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsRestartingGattServerAfterBtPowerOn() }
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Reversed PPoG"),
                    description = SettingsText("Let the watch host the data connection when it supports it. Turn off to use the older phone-hosted mode. Reconnect for this to take effect"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.bleConfig.useReversedPpogV2,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    useReversedPpogV2 = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Bluetooth state restoration"),
                    description = SettingsText("Let iOS relaunch the app to restore the watch connection. Takes effect on next app launch"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.bleConfig.centralStateRestoration,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    centralStateRestoration = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsCentralStateRestoration() },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_passive_reconnection),
                    description = SettingsText(Res.string.settings_passive_reconnection_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.bleConfig.autoConnectAfterFailure,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    autoConnectAfterFailure = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsBleAutoConnect() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Filter BLE watch scans by UUID"),
                    description = SettingsText("Disable this only if BLE scans are not finding your Core watch or Pebble 2"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = libPebbleConfig.bleConfig.filterScanResultsByUuid,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    filterScanResultsByUuid = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_post_test_notification),
                    description = SettingsText(Res.string.settings_post_test_notification_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    action = { postTestNotification(appContext) },
                    show = { pebbleFeatures.supportsPostTestNotification() },
                ),
                basicSettingsDropdownItem(
                    title = SettingsText("Watch type for unknown devices"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    items = WatchType.entries,
                    selectedItem = libPebbleConfig.watchConfig.unknownWatchTypePlatform,
                    onItemSelected = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    unknownWatchTypePlatform = it
                                )
                            )
                        )
                    },
                    itemText = { it.name },
                    isDebugSetting = true,
                ),
                basicSettingsActionItem(
                    title = SettingsText("Force JSCore GC"),
                    description = SettingsText(""),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    action = {
                        scope.launch {
                            libPebble.watches.first().filterIsInstance<ConnectedPebble.CompanionAppControl>()
                                .forEach {
                                    it.currentCompanionAppSessions.value.filterIsInstance<PKJSApp>().firstOrNull()?.debugForceGC()
                                }
                        }
                    },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Disable FW update notifications"),
                    description = SettingsText("Ignore notifications for users who sideload their own firmware"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    checked = coreConfig.disableFirmwareUpdateNotifications,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                disableFirmwareUpdateNotifications = it
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsActionItem(
                    title = SettingsText("Do immediate background sync"),
                    description = SettingsText("Sync firmware updates, locker, etc manually now (happens regularly automatically)"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    action = {
                        GlobalScope.launch {
                            coreBackgroundSync.doBackgroundSync(this, force = true)
                        }
                    },
                    isDebugSetting = true,
                ),
                basicSettingsActionItem(
                    title = SettingsText("Copy PKJS account token"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    action = { setShowCopyTokenDialog(true) },
                    show = { loggedIn != null },
                    isDebugSetting = true,
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_complications),
                    description = SettingsText(Res.string.settings_complications_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Plugins,
                    action = { nav.navigateTo(PebbleNavBarRoutes.ComplicationsRoute) },
                ) },
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_preferred_sources),
                    description = SettingsText(Res.string.settings_preferred_sources_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Plugins,
                    action = { nav.navigateTo(PebbleNavBarRoutes.PreferredSourcesRoute) },
                    show = { hasSharedSources(libPebble.getAllPlugins()) },
                ) },
                basicSettingsDropdownItem(
                    id = SettingsIds.PermissionPolicy,
                    title = SettingsText(Res.string.settings_permission_policy),
                    description = SettingsText(libPebbleConfig.watchConfig.permissionPolicy.description()),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    group = SettingsGroup.Permissions,
                    items = PermissionPolicy.entries,
                    selectedItem = libPebbleConfig.watchConfig.permissionPolicy,
                    onItemSelected = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(permissionPolicy = it)
                            )
                        )
                    },
                    itemText = { it.displayName() },
                    selectorBelow = true,
                    details = { PermissionPolicyDetails() },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_legacy_pebblekit),
                    description = SettingsText(Res.string.settings_legacy_pebblekit_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    group = SettingsGroup.Permissions,
                    checked = libPebbleConfig.watchConfig.enableLegacyPebbleKit,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(enableLegacyPebbleKit = it)
                            )
                        )
                    },
                    // PebbleKit Classic is Android-only.
                    show = { platform == Platform.Android },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_ask_legacy_apps),
                    description = SettingsText(Res.string.settings_ask_legacy_apps_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    group = SettingsGroup.Permissions,
                    checked = libPebbleConfig.watchConfig.askLegacyAppsForPermissions,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(askLegacyAppsForPermissions = it)
                            )
                        )
                    },
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_permissions_in_use),
                    description = SettingsText(Res.string.settings_permissions_in_use_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    group = SettingsGroup.Permissions,
                    action = { nav.navigateTo(PebbleNavBarRoutes.PermissionUsageRoute) },
                ) },
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_sign_out_pebble),
                    description = SettingsText(Res.string.settings_sign_out_pebble_desc, coreUser.orEmpty()),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    action = {
                        scope.launch {
                            try {
                                usersDao.signOut()
                                libPebble.requestLockerSync()
                                analyticsBackend.setUser(email = null)
                                logger.d { "User signed out" }
                            } catch (e: Exception) {
                                logger.e(e) { "Failed to sign out" }
                            }
                        }
                    },
                    show = { coreUser != null },
                ),
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_sign_in_pebble),
                    description = SettingsText(Res.string.settings_sign_in_pebble_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    action = { showSignInDialog = true },
                    show = { coreUser == null },
                ),
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_sign_out_rebble),
                    description = SettingsText(Res.string.settings_sign_out_rebble_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    action = {
                        scope.launch {
                            pebbleAccount.setToken(null, null)
                        }
                    },
                    show = { loggedIn != null },
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Auto-Resume Firmware Updates"),
                    description = SettingsText("Automatically continue an interrupted firmware update when the watch reconnects"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    checked = libPebbleConfig.watchConfig.autoResumeFirmwareUpdate,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    autoResumeFirmwareUpdate = it
                                )
                            )
                        )
                    },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_foreground_service),
                    description = SettingsText(Res.string.settings_foreground_service_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    checked = coreConfig.androidForegroundServiceForWatchConnectionV2,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                androidForegroundServiceForWatchConnectionV2 = it,
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsForegroundService() },
                ),
                navBarNav?.let {basicSettingsActionItem(
                    title = SettingsText("Show Watch Onboarding"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    action = {
                        navBarNav.navigateTo(CommonRoutes.WatchOnboardingRoute)
                    },
                    show = { debugOptionsEnabled },
                ) },
                navBarNav?.let {basicSettingsActionItem(
                    title = SettingsText("Show Ring Onboarding"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.General,
                    action = {
                        navBarNav.navigateTo(CommonRoutes.RingOnboardingRoute)
                    },
                    show = { debugOptionsEnabled },
                ) },
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_download_in_background),
                    description = SettingsText(Res.string.settings_download_in_background_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    group = SettingsGroup.AppUpdates,
                    checked = libPebbleConfig.watchConfig.proactivelyDownloadAppUpdates,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    proactivelyDownloadAppUpdates = it
                                )
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_wifi_only),
                    description = SettingsText(Res.string.settings_wifi_only_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    group = SettingsGroup.AppUpdates,
                    checked = libPebbleConfig.watchConfig.appUpdateDownloadWifiOnly,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    appUpdateDownloadWifiOnly = it
                                )
                            )
                        )
                    },
                    show = { libPebbleConfig.watchConfig.proactivelyDownloadAppUpdates },
                ),
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_data_usage),
                    description = SettingsText(Res.string.settings_data_usage_desc),
                    keywords = "network mobile cellular data bandwidth",
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    group = SettingsGroup.AppUsage,
                    action = { nav.navigateTo(PebbleNavBarRoutes.AppDataUsageRoute) },
                ) },
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_app_activity),
                    description = SettingsText(Res.string.settings_app_activity_desc),
                    keywords = "audit log actions history",
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    group = SettingsGroup.AppUsage,
                    action = { nav.navigateTo(PebbleNavBarRoutes.AppActivityRoute) },
                ) },
                navBarNav?.let { nav -> basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_configure_appstore_sources),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    action = { nav.navigateTo(PebbleNavBarRoutes.AppstoreSettingsRoute()) },
                ) },
                basicSettingsActionItem(
                    title = SettingsText(Res.string.settings_restore_system_app_positions),
                    description = SettingsText(Res.string.settings_restore_system_app_positions_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    action = {
                        libPebble.restoreSystemAppOrder()
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_emulate_timeline),
                    description = SettingsText(Res.string.settings_emulate_timeline_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    checked = libPebbleConfig.watchConfig.emulateRemoteTimeline,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    emulateRemoteTimeline = it
                                )
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_intercept_pkjs_weather),
                    description = SettingsText(Res.string.settings_intercept_pkjs_weather_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Apps,
                    checked = coreConfig.interceptPKJSWeather,
                    onCheckChanged = {
                        coreConfigHolder.update(coreConfig.copy(interceptPKJSWeather = it))
                    },
                ),
                basicSettingsToggleItem(
                    title = SettingsText("Show watch connection debug info"),
                    description = SettingsText("Extra debug info on devices tab"),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Connectivity,
                    checked = coreConfig.showWatchConnectionDebugInfo,
                    onCheckChanged = {
                        coreConfigHolder.update(coreConfig.copy(showWatchConnectionDebugInfo = it))
                    },
                    isDebugSetting = true,
                ),
                basicSettingsToggleItem(
                    title = SettingsText(Res.string.settings_music_seek),
                    description = SettingsText(Res.string.settings_music_seek_desc),
                    topLevelType = TopLevelType.Phone,
                    section = Section.Music,
                    checked = libPebbleConfig.watchConfig.musicSeekWhenAvailable,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    musicSeekWhenAvailable = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsMusic() },
                ),
            ) + watchPrefs
        }

    return SettingsItemsState(
        rawSettingsItems = rawSettingsItems,
        debugOptionsEnabled = debugOptionsEnabled,
        anyWatchSupportsSettingsSync = anyWatchSupportsSettingsSync,
        coreConfig = coreConfig,
        healthTrackingEnabled = healthSettings.trackingEnabled,
    )
}

@Composable
fun WatchSettingsScreen(navBarNav: NavBarNav, topBarParams: TopBarParams) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val viewModel = koinViewModel<WatchSettingsScreenViewModel>()
        val platform = koinInject<Platform>()
        val state = rememberSettingsItemsState(navBarNav, topBarParams) ?: return

        val listState = rememberLazyListState()
        val settingsTitle = stringResource(Res.string.settings)

        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(viewModel.searchState)
            topBarParams.actions { }
            topBarParams.title(settingsTitle)
            launch {
                topBarParams.scrollToTop.collect {
                    if (listState.firstVisibleItemIndex > 0) {
                        listState.animateScrollToItem(0)
                    } else {
                        viewModel.selectedTopLevelType = TopLevelType.Phone
                    }
                }
            }
        }
        val libPebble = rememberLibPebble()
        val libPebbleConfig by libPebble.config.collectAsState()
        val settingsSyncEnabled = libPebbleConfig.watchConfig.enableWatchSettingsSync

        val availableTopLevelTypes = remember(state.anyWatchSupportsSettingsSync, state.coreConfig, settingsSyncEnabled) {
            TopLevelType.entries.filter {
                when (it) {
                    TopLevelType.Phone -> true
                    TopLevelType.Watch -> state.anyWatchSupportsSettingsSync && settingsSyncEnabled
                    TopLevelType.All -> state.coreConfig.showAllSettingsTab
                    TopLevelType.Notifications -> false
                }
            }
        }

        val validSettingsItems =
            remember(state.rawSettingsItems, viewModel.selectedTopLevelType, state.debugOptionsEnabled) {
                state.rawSettingsItems.filter {
                    viewModel.selectedTopLevelType.show(it.topLevelType) &&
                            (state.debugOptionsEnabled || !it.isDebugSetting)
                }
            }

        val searchQuery = viewModel.searchState.query
        val titleTexts = state.rawSettingsItems.associate { it.title.key to it.title.text() }
        val groupTitles = SettingsGroup.entries.associateWith { stringResource(it.title) }

        val filteredItems by remember(
            validSettingsItems,
            viewModel.searchState.query,
            titleTexts,
            groupTitles,
        ) {
            derivedStateOf {
                if (searchQuery.isEmpty()) {
                    validSettingsItems.filter { item -> item.show() }
                } else {
                    validSettingsItems.filter {
                        it.matches(searchQuery, titleTexts[it.title.key].orEmpty(), it.group?.let { group -> groupTitles[group] }) &&
                            it.show()
                    }
                }
            }
        }

        val sectionsToShowInList = remember(filteredItems) {
            Section.entries.filter { section ->
                filteredItems.any { it.section == section } || section.navigatesDirectlyTo() != null
            }
        }
        val groupedItemsToDisplay = remember(filteredItems) {
            filteredItems.groupBy { it.section }.entries.sortedBy { it.key.ordinal }
        }

        val isSearching = searchQuery.isNotEmpty()

        Scaffold {
            Column {
                // Only show tab buttons at top if there is more than one
                if (availableTopLevelTypes.size > 1) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SingleChoiceSegmentedButtonRow {
                            availableTopLevelTypes.forEachIndexed { index, type ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = availableTopLevelTypes.size
                                    ),
                                    selected = viewModel.selectedTopLevelType == type,
                                    onClick = { viewModel.selectedTopLevelType = type },
                                    icon = { },
                                    label = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                type.icon(platform),
                                                contentDescription = type.name,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                stringResource(type.displayName),
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp,
                                                modifier = Modifier.padding(top = 3.dp).widthIn(min = 55.dp),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                if (viewModel.selectedTopLevelType == TopLevelType.Notifications) {
                    NotificationsScreenContent(topBarParams, navBarNav)
                    return@Column
                }

                if (isSearching) {
                    // When searching, show all matching settings grouped by section (like before)
                    LazyColumn(state = listState) {
                        groupedItemsToDisplay.forEach { (section, items) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    Text(
                                        stringResource(section.title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp,
                                        )
                                    )
                                }
                            }
                            settingsItems(items)
                            item {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                } else {
                    // When not searching, show category list
                    LazyColumn(state = listState) {
                        items(
                            items = sectionsToShowInList,
                            key = { it.name },
                        ) { section ->
                            val sectionBadgeCount = filteredItems
                                .filter { it.section == section && it.badge != null && it.show() }
                                .sumOf { it.badge?.toIntOrNull() ?: 0 }
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        section.icon,
                                        contentDescription = null,
                                    )
                                },
                                headlineContent = { Text(stringResource(section.title)) },
                                trailingContent = if (sectionBadgeCount > 0) {
                                    {
                                        Badge {
                                            Text("$sectionBadgeCount")
                                        }
                                    }
                                } else null,
                                shadowElevation = ELEVATION,
                                modifier = Modifier.clickable {
                                    val navigateDirectlyTo = section.navigatesDirectlyTo()
                                    if (navigateDirectlyTo != null) {
                                        navBarNav.navigateTo(navigateDirectlyTo)
                                    } else {
                                        navBarNav.navigateTo(
                                            PebbleNavBarRoutes.WatchSettingsCategoryRoute(
                                                section = section.name,
                                                topLevelType = viewModel.selectedTopLevelType.name,
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchSettingsCategoryScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    section: Section,
    topLevelType: TopLevelType,
) {
    val focusManager = LocalFocusManager.current
    val dismissInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .imePadding()
            .clickable(
                interactionSource = dismissInteractionSource,
                indication = null,
            ) { focusManager.clearFocus() },
    ) {
        val state = rememberSettingsItemsState(navBarNav, topBarParams) ?: return
        val sectionTitle = stringResource(section.title)

        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(null)
            topBarParams.actions {}
            topBarParams.title(sectionTitle)
        }

        val filteredItems = remember(state.rawSettingsItems, topLevelType, state.debugOptionsEnabled) {
            state.rawSettingsItems.filter {
                it.section == section &&
                        topLevelType.show(it.topLevelType) &&
                        (state.debugOptionsEnabled || !it.isDebugSetting) &&
                        it.show()
            }
        }

        LazyColumn {
            settingsItems(filteredItems)
        }
    }
}

fun basicSettingsActionItem(
    title: SettingsText,
    topLevelType: TopLevelType,
    section: Section,
    group: SettingsGroup? = null,
    button: @Composable (() -> Unit)? = null,
    action: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    description: SettingsText? = null,
    keywords: String = "",
    show: () -> Boolean = { true },
    badge: String? = null,
    isDebugSetting: Boolean = false,
    onDisplayed: (@Composable () -> Unit)? = null
) = SettingsItem(
    title = title,
    topLevelType = topLevelType,
    section = section,
    group = group,
    keywords = keywords,
    show = show,
    badge = badge,
    isDebugSetting = isDebugSetting,
    item = {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (badge != null) {
                        Badge {
                            Text(text = badge)
                        }
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    if (button != null) {
                        button()
                    } else {
                        Text(title.text())
                    }
                }
            },
            supportingContent = {
                if (description != null) {
                    Text(description.text(), fontSize = 12.sp)
                }
            },
            trailingContent = {
                if (action != null) {
                    Icon(
                        imageVector = actionIcon ?: Icons.AutoMirrored.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            },
            shadowElevation = ELEVATION,
            modifier = Modifier.run() {
                if (action != null) {
                    clickable { action() }
                } else this
            },
        )
    },
    onDisplayed = onDisplayed,
)

fun basicSettingsToggleItem(
    id: String? = null,
    title: SettingsText,
    topLevelType: TopLevelType,
    section: Section,
    group: SettingsGroup? = null,
    checked: Boolean,
    onCheckChanged: (Boolean) -> Unit,
    description: SettingsText? = null,
    keywords: String = "",
    show: () -> Boolean = { true },
    isDebugSetting: Boolean = false,
) = SettingsItem(
    id = id,
    title = title,
    topLevelType = topLevelType,
    section = section,
    group = group,
    keywords = keywords,
    show = show,
    isDebugSetting = isDebugSetting,
    item = {
        ListItem(
            headlineContent = {
                Text(title.text())
            },
            leadingContent = {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckChanged,
                )
            },
            supportingContent = {
                if (description != null) {
                    Text(description.text(), fontSize = 11.sp)
                }
            },
            shadowElevation = ELEVATION,
        )
    },
)

fun basicSettingsNumberItem(
    id: String? = null,
    title: SettingsText,
    topLevelType: TopLevelType,
    section: Section,
    group: SettingsGroup? = null,
    value: Long,
    onValueChange: (Long) -> Unit,
    min: Int,
    max: Int,
    unit: SettingsText?,
    description: SettingsText? = null,
    keywords: String = "",
    show: () -> Boolean = { true },
    isDebugSetting: Boolean = false,
    defaultValue: Long? = null,
    valueFormatter: (@Composable (Long) -> String)? = null,
    steps: Int? = null,
) = SettingsItem(
    id = id,
    title = title,
    topLevelType = topLevelType,
    section = section,
    group = group,
    keywords = keywords,
    show = show,
    isDebugSetting = isDebugSetting,
    item = {
        ListItem(
            headlineContent = {
                Text(title.text())
            },
            supportingContent = {
                var sliderPosition by remember(value) { mutableLongStateOf(value) }
                Column {
                    if (description != null) {
                        Text(description.text(), fontSize = 11.sp)
                    }
                    val minF = remember(min) { min.toFloat() }
                    val maxF = remember(max) { max.toFloat() }
                    val resolvedSteps = steps ?: remember(max, min) {
                        val range = max - min
                        if (range in 1..100) range - 1 else 0
                    }
                    Slider(
                        value = sliderPosition.toFloat(),
                        onValueChange = { sliderPosition = it.roundToLong() },
                        valueRange = minF..maxF,
                        steps = resolvedSteps,
                        onValueChangeFinished = {
                            onValueChange(sliderPosition)
                        },
                    )
                    val unitText = unit?.text().orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = valueFormatter?.invoke(sliderPosition) ?: "$sliderPosition $unitText",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (defaultValue != null) {
                            TextButton(
                                onClick = {
                                    onValueChange(defaultValue)
                                },
                                enabled = value != defaultValue,
                            ) {
                                Text(
                                    text = stringResource(
                                        Res.string.settings_default_value,
                                        valueFormatter?.invoke(defaultValue) ?: "$defaultValue $unitText",
                                    ),
                                    modifier = Modifier.widthIn(max = 150.dp),
                                    maxLines = 1,
                                    lineHeight = 12.sp,
                                )
                            }
                        }
                    }
                }
            },
            shadowElevation = ELEVATION,
        )
    },
)

fun basicSettingsNumberFieldItem(
    id: String? = null,
    title: SettingsText,
    topLevelType: TopLevelType,
    section: Section,
    group: SettingsGroup? = null,
    value: Long,
    onValueChange: (Long) -> Unit,
    min: Int,
    max: Int,
    unit: SettingsText?,
    description: SettingsText? = null,
    keywords: String = "",
    show: () -> Boolean = { true },
    isDebugSetting: Boolean = false,
    defaultValue: Long? = null,
    valueFormatter: ((Long) -> String)? = null,
) = SettingsItem(
    id = id,
    title = title,
    topLevelType = topLevelType,
    section = section,
    group = group,
    keywords = keywords,
    show = show,
    isDebugSetting = isDebugSetting,
    item = {
        ListItem(
            headlineContent = {
                Text(title.text())
            },
            supportingContent = {
                val minL = min.toLong()
                val maxL = max.toLong()
                var textFieldValue by remember(value) {
                    val text = value.toString()
                    mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
                }
                var isFocused by remember { mutableStateOf(false) }
                LaunchedEffect(isFocused) {
                    if (isFocused) {
                        textFieldValue = textFieldValue.copy(
                            selection = TextRange(0, textFieldValue.text.length),
                        )
                    } else if (textFieldValue.text.toLongOrNull() != value) {
                        val text = value.toString()
                        textFieldValue = TextFieldValue(
                            text = text,
                            selection = TextRange(text.length),
                        )
                    }
                }
                Column {
                    if (description != null) {
                        Text(description.text(), fontSize = 11.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        IconButton(
                            onClick = {
                                val newVal = (value - 1).coerceIn(minL, maxL)
                                if (newVal != value) {
                                    onValueChange(newVal)
                                }
                            },
                            enabled = value > minL,
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.settings_decrease))
                        }
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = { input ->
                                val filtered = input.text.filter { it.isDigit() }.take(6)
                                val parsed = filtered.toLongOrNull()
                                val finalText = if (parsed != null && parsed > maxL) {
                                    maxL.toString()
                                } else {
                                    filtered
                                }
                                textFieldValue = input.copy(
                                    text = finalText,
                                    selection = TextRange(finalText.length),
                                )
                                val finalParsed = finalText.toLongOrNull()
                                if (finalParsed != null && finalParsed in minL..maxL && finalParsed != value) {
                                    onValueChange(finalParsed)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            suffix = { Text(unit?.text().orEmpty()) },
                            modifier = Modifier
                                .width(110.dp)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                },
                        )
                        IconButton(
                            onClick = {
                                val newVal = (value + 1).coerceIn(minL, maxL)
                                if (newVal != value) {
                                    onValueChange(newVal)
                                }
                            },
                            enabled = value < maxL,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.settings_increase))
                        }
                        if (valueFormatter != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = valueFormatter(value),
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (defaultValue != null) {
                            TextButton(
                                onClick = {
                                    onValueChange(defaultValue)
                                },
                                enabled = value != defaultValue,
                            ) {
                                Text(
                                    text = stringResource(Res.string.settings_default_value, defaultValue),
                                    modifier = Modifier.widthIn(max = 150.dp),
                                    maxLines = 1,
                                    lineHeight = 12.sp,
                                )
                            }
                        }
                    }
                }
            },
            shadowElevation = ELEVATION,
        )
    },
)

fun <T> basicSettingsDropdownItem(
    id: String? = null,
    title: SettingsText,
    description: SettingsText? = null,
    topLevelType: TopLevelType,
    section: Section,
    group: SettingsGroup? = null,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemText: @Composable (T) -> String,
    keywords: String = "",
    show: () -> Boolean = { true },
    isDebugSetting: Boolean = false,
    extraSupportingContent: (@Composable () -> Unit)? = null,
    /** Under the description rather than beside the title, for long values. */
    selectorBelow: Boolean = false,
    /** Shown in a popup from an info button beside the title. */
    details: (@Composable () -> Unit)? = null,
) = SettingsItem(
    id = id,
    title = title,
    topLevelType = topLevelType,
    section = section,
    group = group,
    keywords = keywords,
    show = show,
    isDebugSetting = isDebugSetting,
    item = {
        var expanded by remember { mutableStateOf(false) }
        val selector: @Composable () -> Unit = {
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
                    border = BorderStroke(1.dp, coreOrange),
                ) {
                    Text(
                        text = itemText(selectedItem),
                        modifier = if (selectorBelow) Modifier else Modifier.widthIn(max = 130.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    items.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(itemText(option)) },
                            onClick = {
                                onItemSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        var showingDetails by remember { mutableStateOf(false) }
        if (showingDetails && details != null) {
            AlertDialog(
                onDismissRequest = { showingDetails = false },
                title = { Text(title.text()) },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { details() } },
                confirmButton = { TextButton(onClick = { showingDetails = false }) { Text(stringResource(UtilRes.string.ok)) } },
            )
        }
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val titleText = title.text()
                    Text(titleText, modifier = Modifier.weight(1f, fill = false))
                    if (details != null) {
                        IconButton(onClick = { showingDetails = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.settings_about_item, titleText))
                        }
                    }
                }
            },
            trailingContent = if (selectorBelow) null else selector,
            supportingContent = {
                Column {
                    Row {
                        if (description != null) {
                            Text(description.text(), fontSize = 11.sp)
                        }
                        extraSupportingContent?.invoke()
                    }
                    if (selectorBelow) selector()
                }
            },
            shadowElevation = ELEVATION,
            modifier = Modifier.clickable { expanded = true },
        )
    }
)

@Composable
fun PKJSCopyTokenDialog(onDismissRequest: () -> Unit) {
    val libPebble = rememberLibPebble()
    val lockerEntriesFlow = remember { libPebble.getAllLockerBasicInfo() }
    val lockerEntries by lockerEntriesFlow.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(
                    "Copy Account Token",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    items(lockerEntries.size) {
                        val lockerEntry = lockerEntries[it]
                        Column {
                            ListItem(
                                headlineContent = {
                                    Text(lockerEntry.title)
                                },
                                supportingContent = {
                                    Text(lockerEntry.developerName)
                                },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val token =
                                            libPebble.getAccountToken(lockerEntry.id)
                                        if (token != null) {
                                            launch {
                                                clipboard.setClipEntry(makeTokenClipEntry(token))
                                            }
                                        } else {
                                            logger.e { "Failed to get account token for ${lockerEntry.id}" }
                                        }
                                        onDismissRequest()
                                    }
                                },
                                shadowElevation = ELEVATION,
                            )
                        }
                    }
                }
            }
        }
    }
}

expect fun getPlatformSTTLanguages(): List<Pair<String, String>>

@Composable
fun STTLanguageDialog(
    onLanguageSelected: (String?) -> Unit,
    onDismissRequest: () -> Unit,
    selectedLanguage: String?,
) {
    var targetLanguage by remember { mutableStateOf(selectedLanguage) }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Language, contentDescription = null) },
        title = { Text(stringResource(Res.string.settings_select_language)) },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(UtilRes.string.cancel))
            }
            TextButton(
                onClick = {
                    onLanguageSelected(targetLanguage)
                }
            ) {
                Text(stringResource(UtilRes.string.ok))
            }
        }
    ) {
        val supportedLanguages = remember { getPlatformSTTLanguages() }
        LazyColumn {
            items(supportedLanguages.size) {
                val (code, name) = supportedLanguages[it]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetLanguage = if (targetLanguage == code) {
                                null
                            } else {
                                code
                            }
                        }
                        .padding(16.dp)
                ) {
                    Checkbox(
                        checked = targetLanguage == code,
                        onCheckedChange = {
                            targetLanguage = if (it) {
                                code
                            } else {
                                null
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(name)
                }
            }
        }
    }
}

@Composable
fun SpokenLanguagePickerDialog(
    selectedCode: String?,
    onLanguageSelected: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val options = remember { SpokenLanguageOptions }
    val filtered = remember(query, options) {
        if (query.isBlank()) {
            options
        } else {
            val q = query.trim().lowercase()
            options.filter { (code, name) ->
                name.lowercase().contains(q) || code.lowercase().contains(q)
            }
        }
    }
    val automaticLabel = stringResource(UtilRes.string.automatic)
    val showAutomatic = query.isBlank() || automaticLabel.lowercase().contains(query.trim().lowercase())
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Language, contentDescription = null) },
        title = { Text(stringResource(Res.string.settings_spoken_language)) },
        buttons = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(UtilRes.string.cancel)) }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.settings_search_languages)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                if (showAutomatic) {
                    item(key = "__automatic__") {
                        SpokenLanguageRow(
                            label = automaticLabel,
                            selected = selectedCode == null,
                            onClick = { onLanguageSelected(null) },
                        )
                    }
                }
                items(filtered, key = { it.first }) { (code, name) ->
                    SpokenLanguageRow(
                        label = name,
                        selected = selectedCode == code,
                        onClick = { onLanguageSelected(code) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.settings_spoken_language_note),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun SpokenLanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

expect fun makeTokenClipEntry(token: String): ClipEntry

expect fun openGoogleFitApp(uiContext: PlatformUiContext?)

object SettingsKeys {
    const val KEY_ENABLE_MEMFAULT_UPLOADS = "enable_memfault_uploads"
    const val KEY_ENABLE_FIREBASE_UPLOADS = "enable_firebase_uploads"
    const val KEY_ENABLE_MIXPANEL_UPLOADS = "enable_mixpanel_uploads"
}

enum class RegularSyncInterval(
    val period: Duration,
    val displayName: StringResource,
) {
    SixHours(6.hours, Res.string.settings_interval_6_hours),
    TwelveHours(12.hours, Res.string.settings_interval_12_hours),
    TwentyFourHours(24.hours, Res.string.settings_interval_24_hours),
    ;

    companion object {
        fun from(period: Duration): RegularSyncInterval = entries.find { it.period == period } ?: SixHours
    }
}

enum class WeatherSyncInterval(
    val period: Duration,
    val displayName: StringResource,
) {
    FifteenMinutes(15.minutes, Res.string.settings_interval_15_minutes),
    ThirtyMinutes(30.minutes, Res.string.settings_interval_30_minutes),
    OneHour(1.hours, Res.string.settings_interval_1_hour),
    SixHours(6.hours, Res.string.settings_interval_6_hours),
    ;

    companion object {
        fun from(period: Duration): WeatherSyncInterval = entries.find { it.period == period } ?: OneHour
    }
}
