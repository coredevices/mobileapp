package coredevices.ring.external.indexwebhook

import kotlinx.coroutines.flow.StateFlow

data class NetworkState(
    val available: Boolean,
    val outageCount: Long = 0,
)

internal fun NetworkState.withAvailability(available: Boolean) = copy(
    available = available,
    outageCount = outageCount + if (this.available && !available) 1 else 0,
)

interface NetworkMonitor {
    val state: StateFlow<NetworkState>
    fun start()
}
