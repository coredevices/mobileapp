package coredevices.ring.agent.integrations.providers

import coredevices.ring.agent.integrations.AgentIntegration
import coredevices.ring.agent.integrations.ListIntegration
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListIntegration

enum class IntegrationSupport { Note, Reminder, List, Agent }

enum class SupportedPlatform { Android, Ios }

/**
 * Somewhere the agent can send notes, reminders, list items and/or external agent messages.
 * Consumers look providers up through [IntegrationProviderRepository] rather than referencing
 * implementations directly.
 */
interface IntegrationProvider {
    /** Stable id, persisted in preferences, shortcuts and deep links. */
    val id: String
    val title: String
    val supports: Set<IntegrationSupport>
    val platforms: Set<SupportedPlatform> get() = SupportedPlatform.entries.toSet()

    /** Labels when offered as a notes / reminders / lists destination. Default to [title]. */
    val notesTitle: String get() = title
    val remindersTitle: String get() = title
    val listsTitle: String get() = title

    val isExternalPlugin: Boolean get() = false

    /**
     * True when created items live in the app's own feed. Other providers own the item remotely
     * and the feed only gets a "Sent to X" marker.
     */
    val storesItemsLocally: Boolean get() = false

    /** Store plugins stand in for the native options. */
    val isHiddenByPlugins: Boolean get() = !storesItemsLocally && !isExternalPlugin

    suspend fun isAuthorized(): Boolean = when {
        supportsNotes -> notes().isAuthorized()
        supportsReminders -> reminders().isAuthorized()
        supportsLists -> lists().isAuthorized()
        else -> agent().isAuthorized()
    }

    /** Whether the provider is ready for one kind of item; a provider may be set up for only one. */
    suspend fun isAuthorized(support: IntegrationSupport): Boolean = isAuthorized()

    /** The raw integration. Tools should use [IntegrationProviderRepository.noteIntegration] instead. */
    fun notes(): NoteIntegration = throw UnsupportedOperationException("$title does not support notes")

    fun reminders(): ReminderIntegration =
        throw UnsupportedOperationException("$title does not support reminders")

    /** Lists live with the provider's reminders unless it keeps them somewhere else. */
    fun lists(): ListIntegration = ReminderListIntegration(reminders())

    fun agent(): AgentIntegration =
        throw UnsupportedOperationException("$title does not support sending to an agent")
}

val IntegrationProvider.supportsNotes: Boolean get() = IntegrationSupport.Note in supports
val IntegrationProvider.supportsReminders: Boolean get() = IntegrationSupport.Reminder in supports
val IntegrationProvider.supportsLists: Boolean get() = IntegrationSupport.List in supports
val IntegrationProvider.supportsAgent: Boolean get() = IntegrationSupport.Agent in supports

fun IntegrationProvider.titleFor(support: IntegrationSupport): String = when (support) {
    IntegrationSupport.Note -> notesTitle
    IntegrationSupport.Reminder -> remindersTitle
    IntegrationSupport.List -> listsTitle
    IntegrationSupport.Agent -> title
}

/** Where an action's result was sent, for display; null when the item is stored locally. */
val IntegrationProvider.externalDestination: String? get() = title.takeUnless { storesItemsLocally }

fun List<IntegrationProvider>.visibleDestinations(): List<IntegrationProvider> = filterNot { it.isHiddenByPlugins }
