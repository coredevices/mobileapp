package coredevices.ring.agent.integrations.providers

import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
import coredevices.ring.agent.integrations.DelegatingNoteIntegration
import coredevices.ring.agent.integrations.DelegatingReminderIntegration
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.Preferences
import coredevices.util.Platform
import coredevices.util.flow.mapState
import coredevices.util.isAndroid
import io.rebble.libpebblecommon.connection.Plugins
import kotlinx.coroutines.flow.StateFlow

/**
 * The single place to find note/reminder providers: the built-in ones plus any installed plugin
 * that declares the matching actions. Owns which provider is selected for notes and for reminders,
 * and hands tools the integration to write through.
 */
class IntegrationProviderRepository(
    private val preferences: Preferences,
    private val plugins: Plugins,
    platform: Platform,
    private val delegatedItems: DelegatedIntegrationItems,
    builtIn: List<IntegrationProvider> = BuiltInIntegrationProviders.all,
) {
    private val platform = if (platform.isAndroid) SupportedPlatform.Android else SupportedPlatform.Ios
    private val builtIn = builtIn.filter { this.platform in it.platforms }

    // A stored selection can stop resolving (an uninstalled plugin); capture then falls back here.
    private val defaultProvider = this.builtIn.first { it.storesItemsLocally }

    val selectedNoteProvider: StateFlow<IntegrationProvider> =
        preferences.noteProviderId.mapState { resolve(it, IntegrationSupport.Note) }

    val selectedReminderProvider: StateFlow<IntegrationProvider> =
        preferences.reminderProviderId.mapState { resolve(it, IntegrationSupport.Reminder) }

    /** Every provider usable on this platform, connected or not. Plugins come and go, so not cached. */
    fun allProviders(): List<IntegrationProvider> =
        builtIn + plugins.getAllPlugins().mapNotNull(PblPluginIntegrationProvider::forPlugin)

    fun providers(support: IntegrationSupport): List<IntegrationProvider> =
        allProviders().filter { support in it.supports }

    /** Providers the user can pick right now: on this platform, supporting [support], and connected. */
    suspend fun availableProviders(support: IntegrationSupport): List<IntegrationProvider> =
        providers(support).filter { it.isAuthorized(support) }

    fun findProvider(id: String): IntegrationProvider? = allProviders().firstOrNull { it.id == id }

    fun selectNoteProvider(provider: IntegrationProvider) {
        require(provider.supportsNotes) { "${provider.id} does not support notes" }
        preferences.setNoteProviderId(provider.id)
    }

    fun selectReminderProvider(provider: IntegrationProvider) {
        require(provider.supportsReminders) { "${provider.id} does not support reminders" }
        preferences.setReminderProviderId(provider.id)
    }

    /** The integration tools write notes through, for [provider] or the selected one. */
    fun noteIntegration(provider: IntegrationProvider = selectedNoteProvider.value): NoteIntegration {
        logger.i { "Creating note integration for provider: ${provider.id}" }
        val integration = provider.notes()
        return if (provider.storesItemsLocally) {
            integration
        } else {
            DelegatingNoteIntegration(integration, provider.title, delegatedItems)
        }
    }

    fun reminderIntegration(
        provider: IntegrationProvider = selectedReminderProvider.value,
    ): ReminderIntegration {
        logger.i { "Creating reminder integration for provider: ${provider.id}" }
        val integration = provider.reminders()
        return if (provider.storesItemsLocally) {
            integration
        } else {
            DelegatingReminderIntegration(integration, provider.title, delegatedItems)
        }
    }

    /** Label of the provider a shortcut sends to, or null for [NoteShortcutType.SendToMe]. */
    fun shortcutDestinationTitle(shortcut: NoteShortcutType): String? = when (shortcut) {
        NoteShortcutType.SendToMe -> null
        is NoteShortcutType.SendToNoteProvider ->
            findProvider(shortcut.providerId)?.notesTitle ?: UNKNOWN_PROVIDER_TITLE
        is NoteShortcutType.SendToReminderProvider ->
            findProvider(shortcut.providerId)?.remindersTitle ?: UNKNOWN_PROVIDER_TITLE
    }

    private fun resolve(id: String, support: IntegrationSupport): IntegrationProvider =
        findProvider(id)?.takeIf { support in it.supports } ?: defaultProvider

    companion object {
        private val logger = Logger.withTag("IntegrationProviderRepository")
        private const val UNKNOWN_PROVIDER_TITLE = "Unavailable provider"
    }
}
