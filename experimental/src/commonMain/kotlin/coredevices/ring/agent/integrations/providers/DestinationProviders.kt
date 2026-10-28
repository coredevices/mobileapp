package coredevices.ring.agent.integrations.providers

import coredevices.util.flow.mapState
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.flow.StateFlow

/** Plugin destinations are only offered while the watch config has plugins switched on. */
fun LibPebble.pluginsEnabled(): StateFlow<Boolean> = config.mapState { it.watchConfig.enablePlugins }
