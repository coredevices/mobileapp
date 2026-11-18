package io.rebble.libpebblecommon.connection.devconnection

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.connection.endpointmanager.CompanionAppLifecycleManager
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.plugin.PluginLogs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Watches with an active dev connection (value = forceLan), resumed on reconnection. */
class DevConnectionResumer {
    val active = MutableStateFlow<Map<PebbleIdentifier, Boolean>>(emptyMap())
}

class DevConnectionManager(
    private val transport: Flow<DevConnectionTransport>,
    private val lanServer: DevConnectionServer,
    private val identifier: PebbleIdentifier,
    private val protocolHandler: PebbleProtocolHandler,
    private val companionAppLifecycleManager: CompanionAppLifecycleManager,
    private val pluginLogs: PluginLogs,
    private val resumer: DevConnectionResumer,
    private val scope: ConnectionCoroutineScope
): ConnectedPebble.DevConnection {
    private val logger = Logger.withTag("DevConnectionManager")
    private val job: MutableStateFlow<Job?> = MutableStateFlow(null)
    override val devConnectionActive: StateFlow<Boolean> =
        job.map { it?.isActive == true }.stateIn(
            scope,
            SharingStarted.Companion.Eagerly,
            false
        )
    private val _devConnectionError = MutableStateFlow<String?>(null)
    override val devConnectionError: StateFlow<String?> = _devConnectionError.asStateFlow()

    fun init() {
        val forceLan = resumer.active.value[identifier] ?: return
        start(forceLan)
    }

    override suspend fun startDevConnection(forceLan: Boolean) {
        resumer.active.update { it + (identifier to forceLan) }
        start(forceLan)
    }

    private fun start(forceLan: Boolean) {
        // Must never complete: onCompletion below stops the transport, so a finite flow would
        // tear the server down as soon as it emitted.
        val transport = if (forceLan) MutableStateFlow(lanServer) else transport
        val inboundPKJSLogs = merge(
            companionAppLifecycleManager.currentPKJSSession.flatMapLatest { it?.logMessages?.receiveAsFlow() ?: emptyFlow() },
            pluginLogs.messages,
        )
        _devConnectionError.value = null
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Dev connection failed" }
                _devConnectionError.value = e.message ?: e::class.simpleName
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
        _devConnectionError.value = null
        resumer.active.update { it - identifier }
        job.value?.cancel()
    }
}
