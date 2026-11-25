package coredevices.pebble.ui

import com.russhwolf.settings.Settings

const val SHOW_DEBUG_OPTIONS_SETTINGS_KEY = "showDebugOptions"

fun Settings.showDebugOptions() = getBoolean(SHOW_DEBUG_OPTIONS_SETTINGS_KEY, false)
