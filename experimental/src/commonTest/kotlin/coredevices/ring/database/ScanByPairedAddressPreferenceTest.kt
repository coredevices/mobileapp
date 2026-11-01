package coredevices.ring.database

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanByPairedAddressPreferenceTest {

    @Test
    fun defaultsToTrue() {
        assertTrue(PreferencesImpl(MapSettings()).scanByPairedAddress.value)
    }

    @Test
    fun persists() {
        val settings = MapSettings()
        val preferences = PreferencesImpl(settings)
        preferences.setScanByPairedAddress(false)
        assertFalse(preferences.scanByPairedAddress.value)
        assertFalse(PreferencesImpl(settings).scanByPairedAddress.value)
    }
}
