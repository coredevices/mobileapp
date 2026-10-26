package coredevices.pebble.ui

import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.QuickLaunchSetting
import io.rebble.libpebblecommon.database.entity.QuicklaunchWatchPref
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class SwipeQuickLaunchPrefsTest {
    private val swipes = listOf(
        QuicklaunchWatchPref.QlSwipeUp,
        QuicklaunchWatchPref.QlSwipeDown,
        QuicklaunchWatchPref.QlSwipeLeft,
        QuicklaunchWatchPref.QlSwipeRight,
    )
    private val existing = listOf(
        WatchPreference(BoolWatchPref.Clock24h, true),
        WatchPreference(QuicklaunchWatchPref.QlUp, null),
    )
    private val settings = existing + swipes.map {
        WatchPreference(it, QuickLaunchSetting(true, Uuid.random()))
    }

    @Test
    fun noKnownWatchOrUnsupportedFirmwareHidesEvenPreviouslyConfiguredSwipes() {
        assertEquals(existing, settings.hideUnsupportedQuickLaunchPrefs(emptySet()))
        assertEquals(existing, settings.hideUnsupportedQuickLaunchPrefs(
            setOf(ProtocolCapsFlag.SupportsBlobDbVersion),
        ))
    }

    @Test
    fun supportingWatchShowsAllFourSwipesInQuickLaunch() {
        assertEquals(settings, settings.hideUnsupportedQuickLaunchPrefs(
            setOf(ProtocolCapsFlag.SupportsBlobDbVersion, ProtocolCapsFlag.SupportsSwipeShortcuts),
        ))
        for (pref in swipes) {
            assertEquals(Section.QuickLaunch, pref.section())
            assertEquals(TopLevelType.Watch, pref.topLevelType())
        }
    }
}
