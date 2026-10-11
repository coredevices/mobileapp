package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.Watches
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

/** Each watch's foreground app, and plugins with a running script or subscription. */
class RunningApps(private val watches: Watches) {
    private val subscriptions = MutableStateFlow<Map<Uuid, Int>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val running: Flow<Set<Uuid>> = combine(
        watches.watches.flatMapLatest { all ->
            val connected = all.filterIsInstance<ConnectedPebbleDevice>()
            if (connected.isEmpty()) {
                flowOf(emptySet())
            } else {
                combine(connected.map { it.runningApp }) { apps -> apps.filterNotNull().toSet() }
            }
        },
        subscriptions,
    ) { foreground, subscribed -> foreground + subscribed.keys }

    suspend fun <T> whileRunning(pluginUuid: Uuid, block: suspend () -> T): T {
        started(pluginUuid)
        try {
            return block()
        } finally {
            stopped(pluginUuid)
        }
    }

    internal fun started(pluginUuid: Uuid) {
        subscriptions.update { it + (pluginUuid to (it[pluginUuid] ?: 0) + 1) }
    }

    internal fun stopped(pluginUuid: Uuid) {
        subscriptions.update {
            val remaining = (it[pluginUuid] ?: 1) - 1
            if (remaining == 0) it - pluginUuid else it + (pluginUuid to remaining)
        }
    }
}
