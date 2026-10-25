package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.libindex.di.LibIndexCoroutineScope
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.integrations.providers.availableDestinations
import coredevices.ring.agent.integrations.providers.visibleDestinations
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.util.Platform
import coredevices.util.isAndroid
import coredevices.util.transcription.CactusModelPathProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the Ring Button switchboard: per-gesture routes, their webhook copies and the agent plugins on offer. */
class RingButtonViewModel(
    private val gestureRouting: GestureRoutingPreferences,
    private val webhookPreferences: IndexWebhookPreferences,
    private val cactusModelProvider: CactusModelPathProvider,
    private val appScope: LibIndexCoroutineScope,
    platform: Platform,
    private val providers: IntegrationProviderRepository,
    pluginsEnabled: StateFlow<Boolean>,
) : ViewModel() {
    val gestureRoutes = gestureRouting.routes
    val webhookConfigs = webhookPreferences.configs
    val musicEnabled: Boolean = platform.isAndroid

    private val providerRefresh = MutableStateFlow(0)

    /** Every installed agent plugin a recording gesture could send to, connected or not. */
    val agentProviders: StateFlow<List<IntegrationProvider>> =
        combine(providerRefresh, pluginsEnabled) { _, pluginsOn ->
            providers.providers(IntegrationSupport.Agent).visibleDestinations(pluginsOn)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** The subset of [agentProviders] that is set up and can be picked. */
    val connectedAgentProviders: StateFlow<List<IntegrationProvider>> = providers
        .availableDestinations(IntegrationSupport.Agent, pluginsEnabled, providerRefresh)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Plugins connect from their own settings page, so re-check whenever the switchboard shows. */
    fun refreshAgentDestinations() {
        providerRefresh.value++
    }

    fun setGestureRoute(gesture: RingGesture, destination: GestureDestination) {
        gestureRouting.setRoute(gesture, destination)
        if (destination == GestureDestination.LocalAgent || destination == GestureDestination.IndexAgent) {
            // Trigger LM model extraction, should be already integrated into assets so no DL
            appScope.launch { cactusModelProvider.getLMModelPath() }
        }
    }

    fun disableWebhook(gesture: RingGesture) {
        webhookPreferences.setEnabled(gesture, false)
    }
}
