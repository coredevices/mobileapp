package io.rebble.libpebblecommon.connection.devconnection

import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.connection.endpointmanager.CompanionAppLifecycleManager
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevConnectionManager(
    private val transport: Flow<DevConnectionTransport>,
    private val identifier: PebbleIdentifier,
    private val protocolHandler: PebbleProtocolHandler,
    private val companionAppLifecycleManager: CompanionAppLifecycleManager,
    private val scope: ConnectionCoroutineScope
): ConnectedPebble.DevConnection {
    private val job: MutableStateFlow<Job?> = MutableStateFlow(null)
    override val devConnectionActive: StateFlow<Boolean> =
        job.map { it?.isActive == true }.stateIn(
            scope,
            SharingStarted.Companion.Eagerly,
            false
        )
    override suspend fun startDevConnection() {
        val inboundPKJSLogs = companionAppLifecycleManager.currentPKJSSession.flatMapLatest { it?.logMessages?.receiveAsFlow() ?: emptyFlow() }
        job.value = scope.launch {
            var last: DevConnectionTransport? = null
            try {
                transport.onCompletion {
                    last?.stop()
                }.collectLatest {
                    last?.stop()
                    // Assign before start(): start() suspends until cancelled, so the finally
                    // below would otherwise always see `last` as null.
                    last = it
                    it.start(identifier, inboundPKJSLogs, protocolHandler.rawInboundMessages) { message ->
                        protocolHandler.send(
                            message
                        )
                    }
                }
            } finally {
                // The LAN server is a process-wide singleton; coroutine cancellation alone won't
                // release its socket, so stop() explicitly to free port 9000.
                last?.stop()
            }
        }.apply {
            invokeOnCompletion {
                job.value = null
            }
        }
    }

    override suspend fun stopDevConnection() {
        job.value?.cancel()
    }
}
