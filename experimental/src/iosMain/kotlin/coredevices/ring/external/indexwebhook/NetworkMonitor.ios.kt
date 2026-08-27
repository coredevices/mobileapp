package coredevices.ring.external.indexwebhook

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

class IosNetworkMonitor : NetworkMonitor {
    private val _state = MutableStateFlow(NetworkState(false))
    override val state: StateFlow<NetworkState> = _state.asStateFlow()
    private val monitor = nw_path_monitor_create()

    override fun start() {
        nw_path_monitor_set_update_handler(monitor) { path ->
            val available = nw_path_get_status(path) == nw_path_status_satisfied
            _state.update { it.withAvailability(available) }
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("IndexWebhookNetworkMonitor", null))
        nw_path_monitor_start(monitor)
    }
}
