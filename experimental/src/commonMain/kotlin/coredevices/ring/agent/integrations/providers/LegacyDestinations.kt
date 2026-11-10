package coredevices.ring.agent.integrations.providers

import coredevices.pebble.ui.IndexTabBadge
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** A native provider hidden by plugins that a setting still points at. */
data class LegacyDestination(val provider: IntegrationProvider, val setting: Setting) {
    enum class Setting { Notes, Reminders, Shortcut }
}

/**
 * Native destinations are hidden once plugins are on, but the app may still be configured for
 * one. Tracks those so the user can be steered to the plugin version.
 */
class LegacyDestinations(
    private val providers: IntegrationProviderRepository,
    preferences: Preferences,
    private val pluginsEnabled: StateFlow<Boolean>,
) : IndexTabBadge {
    val pending: Flow<List<LegacyDestination>> = combine(
        pluginsEnabled,
        providers.selectedNoteProvider,
        providers.selectedReminderProvider,
        preferences.noteShortcut,
    ) { pluginsOn, note, reminder, shortcut ->
        if (!pluginsOn) return@combine emptyList()
        listOfNotNull(
            LegacyDestination(note, LegacyDestination.Setting.Notes),
            LegacyDestination(reminder, LegacyDestination.Setting.Reminders),
            shortcutProvider(shortcut)?.let { LegacyDestination(it, LegacyDestination.Setting.Shortcut) },
        ).filter { it.provider.isHiddenByPlugins }
    }

    override val count: Flow<Int> = pending.map { it.size }

    /** Writes through a provider the user should have relinked by now. */
    val legacyUse: Flow<IntegrationProvider> = providers.integrationUsed.filter { isLegacy(it) }

    fun isLegacy(provider: IntegrationProvider): Boolean = pluginsEnabled.value && provider.isHiddenByPlugins

    private fun shortcutProvider(shortcut: NoteShortcutType): IntegrationProvider? = when (shortcut) {
        NoteShortcutType.SendToMe -> null
        is NoteShortcutType.SendToNoteProvider -> providers.findProvider(shortcut.providerId)
        is NoteShortcutType.SendToReminderProvider -> providers.findProvider(shortcut.providerId)
    }
}
