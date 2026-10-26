package coredevices.ring.agent.integrations.providers

import coredevices.util.flow.mapState
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/** Plugin destinations are only offered while the watch config has plugins switched on. */
fun LibPebble.pluginsEnabled(): StateFlow<Boolean> = config.mapState { it.watchConfig.enablePlugins }

/** The connected destinations for [support], recomputed whenever [refresh] ticks or [pluginsEnabled] changes. */
fun IntegrationProviderRepository.availableDestinations(
    support: IntegrationSupport,
    pluginsEnabled: Flow<Boolean>,
    refresh: Flow<*>,
): Flow<List<IntegrationProvider>> = combine(refresh, pluginsEnabled) { _, pluginsOn ->
    availableProviders(support).visibleDestinations(pluginsOn)
}
