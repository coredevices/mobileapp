package coredevices.ring.database

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsePendingIntentScanPreferenceTest {

    @Test
    fun defaultsToTrue() {
        assertTrue(PreferencesImpl(MapSettings()).usePendingIntentScan.value)
    }

    @Test
    fun followsPlatformSupport() {
        val settings = MapSettings("use_pending_intent_scan_2" to true)
        val preferences = PreferencesImpl(settings)

        assertEquals(pendingIntentScanSupported, preferences.usePendingIntentScan.value)

        if (pendingIntentScanSupported) {
            preferences.setUsePendingIntentScan(false)
            assertFalse(preferences.usePendingIntentScan.value)
            assertFalse(PreferencesImpl(settings).usePendingIntentScan.value)
        } else {
            assertFailsWith<UnsupportedOperationException> { preferences.setUsePendingIntentScan(true) }
            assertFalse(preferences.usePendingIntentScan.value)
        }
    }
}
