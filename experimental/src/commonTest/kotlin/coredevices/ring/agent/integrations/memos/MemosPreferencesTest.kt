package coredevices.ring.agent.integrations.memos

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemosPreferencesTest {

    @Test
    fun defaultsToNoServer() {
        assertNull(MemosPreferences(MapSettings()).baseUrl.value)
    }

    @Test
    fun baseUrlRoundTripsThroughSettings() {
        val settings = MapSettings()
        MemosPreferences(settings).setBaseUrl("https://memos.example.com")
        assertEquals("https://memos.example.com", MemosPreferences(settings).baseUrl.value)
    }

    @Test
    fun baseUrlDropsTrailingSlashes() {
        val prefs = MemosPreferences(MapSettings())
        prefs.setBaseUrl("https://memos.example.com//")
        assertEquals("https://memos.example.com", prefs.baseUrl.value)
    }

    @Test
    fun blankBaseUrlIsStoredAsUnset() {
        val prefs = MemosPreferences(MapSettings())
        prefs.setBaseUrl("   ")
        assertNull(prefs.baseUrl.value)
    }

    @Test
    fun clearForgetsTheServer() {
        val prefs = MemosPreferences(MapSettings())
        prefs.setBaseUrl("https://memos.example.com")
        prefs.clear()
        assertNull(prefs.baseUrl.value)
    }
}
