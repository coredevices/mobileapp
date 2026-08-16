package coredevices.pebble.ui

import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BacklightPresetMode
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.NumberWatchPref
import io.rebble.libpebblecommon.database.entity.RgbColorWatchPref
import kotlin.test.Test
import kotlin.test.assertEquals

class BacklightPresetPrefsTest {
    private fun prefs(preset: BacklightPresetMode?): List<WatchPreference<*>> = listOf(
        WatchPreference(EnumWatchPref.BacklightPreset, preset),
        WatchPreference(BoolWatchPref.AmbientLightSensor, true),
        WatchPreference(BoolWatchPref.BacklightMotion, true),
        WatchPreference(EnumWatchPref.BacklightIntensity, null),
        WatchPreference(EnumWatchPref.DynamicBacklightMode, null),
        WatchPreference(EnumWatchPref.BacklightTouch, null),
        WatchPreference(NumberWatchPref.BacklightTimeoutMs, null),
        WatchPreference(BoolWatchPref.Backlight, true),
        WatchPreference(RgbColorWatchPref.BacklightColor, null),
    )

    @Test
    fun advancedKeepsEverything() {
        val filtered = prefs(BacklightPresetMode.Advanced).hidePresetManagedBacklightPrefs()
        assertEquals(prefs(BacklightPresetMode.Advanced), filtered)
    }

    @Test
    fun presetHidesManagedPrefsOnly() {
        val filtered = prefs(BacklightPresetMode.Standard).hidePresetManagedBacklightPrefs()
        assertEquals(
            listOf(
                EnumWatchPref.BacklightPreset,
                BoolWatchPref.Backlight,
                RgbColorWatchPref.BacklightColor,
            ),
            filtered.map { it.pref },
        )
    }

    @Test
    fun unsetPresetFallsBackToDefault() {
        val filtered = prefs(null).hidePresetManagedBacklightPrefs()
        assertEquals(
            prefs(BacklightPresetMode.Standard).hidePresetManagedBacklightPrefs().map { it.pref },
            filtered.map { it.pref },
        )
    }
}
