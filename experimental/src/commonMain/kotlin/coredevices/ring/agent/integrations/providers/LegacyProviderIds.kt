package coredevices.ring.agent.integrations.providers

import coredevices.ring.data.NoteShortcutType

/**
 * Providers were once two enums (`NoteProvider` / `ReminderProvider`) stored as ints, and shortcuts
 * stored their enum names. Only used when reading persisted values written before that changed.
 */
internal object LegacyProviderIds {
    private val noteProviders = mapOf(
        1 to BuiltInIntegrationProviders.Index,
        2 to BuiltInIntegrationProviders.Notion,
        3 to BuiltInIntegrationProviders.Tasker,
        4 to BuiltInIntegrationProviders.Obsidian,
    )
    private val reminderProviders = mapOf(
        1 to BuiltInIntegrationProviders.Index,
        2 to BuiltInIntegrationProviders.GoogleTasks,
        3 to BuiltInIntegrationProviders.IosReminders,
        4 to BuiltInIntegrationProviders.Tasker,
    )
    private val enumNames = mapOf(
        "Builtin" to BuiltInIntegrationProviders.Index,
        "BuiltIn" to BuiltInIntegrationProviders.Index,
        "Native" to BuiltInIntegrationProviders.Index,
        "Notion" to BuiltInIntegrationProviders.Notion,
        "Obsidian" to BuiltInIntegrationProviders.Obsidian,
        "Tasker" to BuiltInIntegrationProviders.Tasker,
        "GoogleTasks" to BuiltInIntegrationProviders.GoogleTasks,
        "IOSReminders" to BuiltInIntegrationProviders.IosReminders,
    )

    fun noteProviderId(legacyId: Int): String? = noteProviders[legacyId]?.id

    fun reminderProviderId(legacyId: Int): String? = reminderProviders[legacyId]?.id

    /** Maps a stored enum name to its provider id; current ids pass through unchanged. */
    fun providerId(stored: String): String = enumNames[stored]?.id ?: stored

    fun migrate(shortcut: NoteShortcutType): NoteShortcutType = when (shortcut) {
        NoteShortcutType.SendToMe -> shortcut
        is NoteShortcutType.SendToNoteProvider ->
            shortcut.copy(providerId = providerId(shortcut.providerId))
        is NoteShortcutType.SendToReminderProvider ->
            shortcut.copy(providerId = providerId(shortcut.providerId))
    }
}
