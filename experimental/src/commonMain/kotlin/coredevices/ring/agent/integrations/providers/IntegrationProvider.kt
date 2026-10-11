package coredevices.ring.agent.integrations.providers

import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration

enum class IntegrationSupport { Note, Reminder }

enum class SupportedPlatform { Android, Ios }

/**
 * Somewhere the agent can send notes and/or reminders: a service that ships with the app
 * ([BuiltInIntegrationProviders]) or an installed plugin
 * ([coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider]). Consumers look providers
 * up through [IntegrationProviderRepository] rather than referencing implementations directly.
 */
interface IntegrationProvider {
    /** Stable id, persisted in preferences, shortcuts and deep links. */
    val id: String
    val title: String
    val supports: Set<IntegrationSupport>
    val platforms: Set<SupportedPlatform> get() = SupportedPlatform.entries.toSet()

    /** Labels when offered as a notes / reminders destination. Default to [title]. */
    val notesTitle: String get() = title
    val remindersTitle: String get() = title

    val isExternalPlugin: Boolean get() = false

    /**
     * True when created items live in the app's own feed. Other providers own the item remotely
     * and the feed only gets a "Sent to X" marker.
     */
    val storesItemsLocally: Boolean get() = false

    suspend fun isAuthorized(): Boolean = when {
        supportsNotes -> notes().isAuthorized()
        else -> reminders().isAuthorized()
    }

    /** Whether the provider is ready for one kind of item; a provider may be set up for only one. */
    suspend fun isAuthorized(support: IntegrationSupport): Boolean = isAuthorized()

    /** The raw integration. Tools should use [IntegrationProviderRepository.noteIntegration] instead. */
    fun notes(): NoteIntegration = throw UnsupportedOperationException("$title does not support notes")

    fun reminders(): ReminderIntegration =
        throw UnsupportedOperationException("$title does not support reminders")
}

val IntegrationProvider.supportsNotes: Boolean get() = IntegrationSupport.Note in supports
val IntegrationProvider.supportsReminders: Boolean get() = IntegrationSupport.Reminder in supports

/** Where an action's result was sent, for display; null when the item is stored locally. */
val IntegrationProvider.externalDestination: String? get() = title.takeUnless { storesItemsLocally }
