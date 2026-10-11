package coredevices.ring.agent.integrations.providers

import com.russhwolf.settings.MapSettings
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.PreferencesImpl
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyProviderIdsTest {

    @Test
    fun legacyIntIdsMigrateToProviderIds() {
        val settings = MapSettings("note_provider" to 2, "reminder_provider" to 2)

        val preferences = PreferencesImpl(settings)

        assertEquals(BuiltInIntegrationProviders.Notion.id, preferences.noteProviderId.value)
        assertEquals(BuiltInIntegrationProviders.GoogleTasks.id, preferences.reminderProviderId.value)
    }

    @Test
    fun theSameLegacyIntMeansDifferentProvidersForNotesAndReminders() {
        val settings = MapSettings("note_provider" to 3, "reminder_provider" to 3)

        val preferences = PreferencesImpl(settings)

        assertEquals(BuiltInIntegrationProviders.Tasker.id, preferences.noteProviderId.value)
        assertEquals(BuiltInIntegrationProviders.IosReminders.id, preferences.reminderProviderId.value)
    }

    @Test
    fun newIdsWinOverLegacyOnes() {
        val settings = MapSettings("note_provider" to 2, "note_provider_id" to "obsidian")

        assertEquals("obsidian", PreferencesImpl(settings).noteProviderId.value)
    }

    @Test
    fun missingSelectionDefaultsToIndex() {
        val preferences = PreferencesImpl(MapSettings())

        assertEquals(BuiltInIntegrationProviders.Index.id, preferences.noteProviderId.value)
        assertEquals(BuiltInIntegrationProviders.Index.id, preferences.reminderProviderId.value)
    }

    @Test
    fun selectionsRoundTrip() {
        val settings = MapSettings()

        PreferencesImpl(settings).setNoteProviderId("plugin:abc")
        PreferencesImpl(settings).setReminderProviderId("google_tasks")

        assertEquals("plugin:abc", PreferencesImpl(settings).noteProviderId.value)
        assertEquals("google_tasks", PreferencesImpl(settings).reminderProviderId.value)
    }

    @Test
    fun shortcutsStoredWithEnumNamesMigrate() {
        val stored = """{"type":"coredevices.ring.data.NoteShortcutType.SendToReminderProvider","provider":"Native"}"""
        val settings = MapSettings("note_shortcut" to stored)

        assertEquals(
            NoteShortcutType.SendToReminderProvider(BuiltInIntegrationProviders.Index.id),
            PreferencesImpl(settings).noteShortcut.value,
        )
    }

    @Test
    fun shortcutsWithCurrentIdsPassThrough() {
        val shortcut = NoteShortcutType.SendToNoteProvider("plugin:abc")
        val settings = MapSettings()

        PreferencesImpl(settings).setNoteShortcut(shortcut)

        assertEquals(shortcut, PreferencesImpl(settings).noteShortcut.value)
    }

    @Test
    fun enumNamesMapToTheirProviders() {
        assertEquals("index", LegacyProviderIds.providerId("Builtin"))
        assertEquals("index", LegacyProviderIds.providerId("BuiltIn"))
        assertEquals("obsidian", LegacyProviderIds.providerId("Obsidian"))
        assertEquals("ios_reminders", LegacyProviderIds.providerId("IOSReminders"))
        assertEquals("plugin:abc", LegacyProviderIds.providerId("plugin:abc"))
    }
}
