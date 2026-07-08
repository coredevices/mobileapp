package coredevices.ring.agent.integrations.obsidian

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ObsidianPreferencesTest {

    @Test
    fun defaultsAreEmptyWithSensibleSubfolder() {
        val prefs = ObsidianPreferences(MapSettings())
        assertNull(prefs.vaultHandle.value)
        assertNull(prefs.vaultName.value)
        assertEquals(ObsidianMode.TIMESTAMPED_FILES, prefs.mode.value)
        assertEquals("", prefs.targetNote.value)
        assertEquals("Index Inbox", prefs.subfolder.value)
    }

    @Test
    fun vaultRoundTripsThroughSettings() {
        val settings = MapSettings()
        ObsidianPreferences(settings).setVault(handle = "tree-uri-123", name = "obsidian")

        val reloaded = ObsidianPreferences(settings)
        assertEquals("tree-uri-123", reloaded.vaultHandle.value)
        assertEquals("obsidian", reloaded.vaultName.value)
    }

    @Test
    fun configRoundTripsThroughSettings() {
        val settings = MapSettings()
        val prefs = ObsidianPreferences(settings)
        prefs.setMode(ObsidianMode.NAMED_NOTE)
        prefs.setTargetNote("Daily.md")
        prefs.setSubfolder("Capture")

        val reloaded = ObsidianPreferences(settings)
        assertEquals(ObsidianMode.NAMED_NOTE, reloaded.mode.value)
        assertEquals("Daily.md", reloaded.targetNote.value)
        assertEquals("Capture", reloaded.subfolder.value)
    }

    @Test
    fun clearRemovesVaultAndResetsConfig() {
        val settings = MapSettings()
        val prefs = ObsidianPreferences(settings)
        prefs.setVault("h", "n")
        prefs.setMode(ObsidianMode.MAIN_NOTE)

        prefs.clear()

        assertNull(prefs.vaultHandle.value)
        assertFalse(settings.hasKey("obsidian_vault_handle"))
        assertEquals(ObsidianMode.TIMESTAMPED_FILES, prefs.mode.value)
    }
}
