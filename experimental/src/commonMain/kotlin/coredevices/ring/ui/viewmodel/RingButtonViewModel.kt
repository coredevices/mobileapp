package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.libindex.di.LibIndexCoroutineScope
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.util.Platform
import coredevices.util.isAndroid
import coredevices.util.transcription.CactusModelPathProvider
import io.rebble.libpebblecommon.permissions.InstallReview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the Ring Button switchboard: per-gesture routes, their webhook copies and the agent plugins on offer. */
class RingButtonViewModel(
    private val gestureRouting: GestureRoutingPreferences,
    private val webhookPreferences: IndexWebhookPreferences,
    private val cactusModelProvider: CactusModelPathProvider,
    private val appScope: LibIndexCoroutineScope,
    platform: Platform,
    private val destinations: DestinationCatalog,
) : ViewModel() {
    val gestureRoutes = gestureRouting.routes
    val gestureCommands = gestureRouting.commands
    val webhookConfigs = webhookPreferences.configs
    val musicEnabled: Boolean = platform.isAndroid

    /** Every agent plugin a recording gesture could send to: installed ones, then the store's.
     *  Null until the first check completes, which can take a few seconds per unresponsive plugin. */
    val agentDestinations: StateFlow<List<DestinationItem>?> = destinations
        .items(IntegrationSupport.Agent)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Plugins connect from their own settings page, so re-check whenever the switchboard shows. */
    fun refreshAgentDestinations() {
        destinations.refresh()
    }

    suspend fun reviewInstall(plugin: StorePlugin): InstallReview? = destinations.reviewInstall(plugin)

    suspend fun installStorePlugin(plugin: StorePlugin, granted: Map<String, Boolean?>): Boolean =
        destinations.install(plugin, granted)

    fun setGestureRoute(gesture: RingGesture, destination: GestureDestination) {
        gestureRouting.setRoute(gesture, destination)
        if (destination == GestureDestination.LocalAgent || destination == GestureDestination.IndexAgent) {
            // Trigger LM model extraction, should be already integrated into assets so no DL
            appScope.launch { cactusModelProvider.getLMModelPath() }
        }
    }

    fun setGestureCommand(gesture: RingGesture, command: String) {
        gestureRouting.setCommand(gesture, command)
    }

    fun disableWebhook(gesture: RingGesture) {
        webhookPreferences.setEnabled(gesture, false)
    }
}
