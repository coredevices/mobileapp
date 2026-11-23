package coredevices.pebble.ui

import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.RgbColorWatchPref
import io.rebble.libpebblecommon.database.entity.WatchPref
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchPrefLayoutTest {
    /**
     * This test replaces the `when` statement that we used to have ensuring that all watch prefs are
     * mapped in the UI. If this fails, you forgot to add a mapping in WatchPrefsScreen. Do not get
     * rid of this test!
     */
    @Test
    fun everyWatchPrefIsLaidOutOnce() {
        val laidOut = WATCH_PREF_LAYOUT.map { it.pref }
        assertEquals(laidOut.distinct(), laidOut, "listed more than once")
        assertEquals(WatchPref.enumeratePrefs().toSet() - laidOut.toSet(), emptySet(), "missing from WATCH_PREF_LAYOUT")
    }

    @Test
    fun eachGroupIsListedTogether() {
        val runs = WATCH_PREF_LAYOUT.mapNotNull { it.group }.fold(emptyList<SettingsGroup>()) { runs, group ->
            if (runs.lastOrNull() == group) runs else runs + group
        }
        assertEquals(runs.distinct(), runs, "a group's prefs are split up")
    }

    @Test
    fun everyOptionIsLabelled() {
        val unitSymbols = setOf("km/h", "mph", "m/s", "°C", "°F")
        val options = EnumWatchPref.entries.flatMap { it.options }.map { it.displayName }.toSet() - unitSymbols
        assertEquals(emptySet(), options - WATCH_PREF_OPTION_LABELS.keys, "missing from WATCH_PREF_OPTION_LABELS")
        val presets = RgbColorWatchPref.entries.flatMap { it.presets }.map { it.displayName }.toSet()
        assertEquals(emptySet(), presets - WATCH_PREF_COLOR_LABELS.keys, "missing from WATCH_PREF_COLOR_LABELS")
    }
}
