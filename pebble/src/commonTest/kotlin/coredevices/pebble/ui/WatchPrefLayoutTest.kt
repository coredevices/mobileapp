package coredevices.pebble.ui

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
}
