package coredevices.ring.agent.integrations.providers

import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.database.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** Offer to make [provider] the default for [supports], which it is set up for but not yet selected as. */
data class DefaultDestinationPrompt(
    val provider: IntegrationProvider,
    val supports: Set<IntegrationSupport>,
) {
    /** "notes", "reminders" or "notes and reminders". */
    val kindsLabel: String
        get() = PROMPTED_SUPPORTS.filter { it in supports }.joinToString(" and ") {
            when (it) {
                IntegrationSupport.Note -> "notes"
                IntegrationSupport.Reminder -> "reminders"
                IntegrationSupport.Agent -> "agent"
            }
        }
}

/**
 * Asks, once per plugin, whether a plugin whose config page just closed with it configured should
 * become the default note/reminder destination. A plugin set up from a destination picker is
 * already the user's choice, so [pickedFromSheet] marks it asked instead.
 */
class DefaultDestinationPrompter(
    private val providers: IntegrationProviderRepository,
    private val preferences: Preferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val _prompt = MutableStateFlow<DefaultDestinationPrompt?>(null)
    val prompt: StateFlow<DefaultDestinationPrompt?> = _prompt.asStateFlow()

    fun pickedFromSheet(providerId: String) {
        preferences.markDefaultDestinationAsked(providerId)
    }

    fun configPageClosed(pluginUuid: Uuid) {
        scope.launch { offer(PblPluginIntegrationProvider.idFor(pluginUuid)) }
    }

    fun accept() {
        val prompt = _prompt.getAndUpdate { null } ?: return
        if (IntegrationSupport.Note in prompt.supports) providers.selectNoteProvider(prompt.provider)
        if (IntegrationSupport.Reminder in prompt.supports) providers.selectReminderProvider(prompt.provider)
    }

    fun dismiss() {
        _prompt.value = null
    }

    private suspend fun offer(providerId: String) {
        if (preferences.ringPaired.value == null) return
        if (providerId in preferences.defaultDestinationAsked) return
        val provider = providers.findProvider(providerId) ?: return
        val configured = PROMPTED_SUPPORTS
            .filter { it in provider.supports && selected(it) != provider }
            .filter { provider.isAuthorized(it) }
            .toSet()
        if (configured.isEmpty()) return
        preferences.markDefaultDestinationAsked(providerId)
        _prompt.value = DefaultDestinationPrompt(provider, configured)
    }

    private fun selected(support: IntegrationSupport): IntegrationProvider? = when (support) {
        IntegrationSupport.Note -> providers.selectedNoteProvider.value
        IntegrationSupport.Reminder -> providers.selectedReminderProvider.value
        IntegrationSupport.Agent -> null
    }
}

/** Agent destinations have no stored default to offer. */
private val PROMPTED_SUPPORTS = listOf(IntegrationSupport.Note, IntegrationSupport.Reminder)
