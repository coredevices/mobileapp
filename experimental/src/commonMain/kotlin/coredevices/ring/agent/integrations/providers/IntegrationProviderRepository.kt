package coredevices.ring.agent.integrations.providers

import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.AgentIntegration
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
import coredevices.ring.agent.integrations.DelegatingAgentIntegration
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The single place to find note/reminder/agent providers: the built-in ones plus any installed
 * plugin that declares the matching actions. Owns which provider is selected for notes and for
 * reminders, and hands tools the integration to write through.
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

    private val _integrationUsed = MutableSharedFlow<IntegrationProvider>(extraBufferCapacity = 16)
    /** The provider each time an integration is handed out to write through. */
    val integrationUsed: SharedFlow<IntegrationProvider> = _integrationUsed.asSharedFlow()

    /** Every provider usable on this platform, connected or not. Plugins come and go, so not cached. */
    fun allProviders(): List<IntegrationProvider> =
        builtIn + plugins.getAllPlugins().mapNotNull { PblPluginIntegrationProvider.forPlugin(it, plugins) }

    fun providers(support: IntegrationSupport): List<IntegrationProvider> =
        allProviders().filter { support in it.supports }

    /** Providers the user can pick right now: on this platform, supporting [support], and connected. */
    suspend fun availableProviders(support: IntegrationSupport): List<IntegrationProvider> =
        providers(support).filter { it.isAuthorized(support) }

    fun findProvider(id: String): IntegrationProvider? = allProviders().firstOrNull { it.id == id }

    /** The provider named [destination], or the selected one when null. Null for an unknown name. */
    fun destinationProvider(destination: String?, support: IntegrationSupport): IntegrationProvider? =
        if (destination == null) selected(support) else findProviderByName(destination, support)

    /** The provider for [support] the user would call [name], ignoring case and spacing. */
    fun findProviderByName(name: String, support: IntegrationSupport): IntegrationProvider? {
        val wanted = normalizeProviderName(name)
        return providers(support).firstOrNull {
            normalizeProviderName(it.titleFor(support)) == wanted || normalizeProviderName(it.title) == wanted
        }
    }

    /**
     * Connected providers for [support], other than the selected one, that [text] names.
     */
    suspend fun mentionedProviders(text: String, support: IntegrationSupport): List<IntegrationProvider> {
        val selected = selected(support)
        return providers(support)
            .filter { it != selected && text.mentionsProviderName(it.titleFor(support)) }
            .filter { it.isAuthorized(support) }
    }

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
        _integrationUsed.tryEmit(provider)
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
        _integrationUsed.tryEmit(provider)
        val integration = provider.reminders()
        return if (provider.storesItemsLocally) {
            integration
        } else {
            DelegatingReminderIntegration(integration, provider.title, delegatedItems)
        }
    }

    /** The integration tools send agent messages through. Agent providers have no stored selection. */
    fun agentIntegration(provider: IntegrationProvider): AgentIntegration {
        logger.i { "Creating agent integration for provider: ${provider.id}" }
        _integrationUsed.tryEmit(provider)
        val integration = provider.agent()
        return if (provider.storesItemsLocally) {
            integration
        } else {
            DelegatingAgentIntegration(integration, provider.title, delegatedItems)
        }
    }

    /** The agent plugin a gesture routes to, or null when it is gone or no longer offers it. */
    fun agentProvider(id: String): IntegrationProvider? = findProvider(id)?.takeIf { it.supportsAgent }

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

    private fun selected(support: IntegrationSupport): IntegrationProvider? = when (support) {
        IntegrationSupport.Note -> selectedNoteProvider.value
        IntegrationSupport.Reminder -> selectedReminderProvider.value
        IntegrationSupport.Agent -> providers(support).firstOrNull()
    }

    companion object {
        private val logger = Logger.withTag("IntegrationProviderRepository")
        private const val UNKNOWN_PROVIDER_TITLE = "Unavailable provider"
    }
}
