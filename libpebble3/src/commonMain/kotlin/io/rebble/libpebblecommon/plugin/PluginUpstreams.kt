package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import io.rebble.libpebblecommon.connection.Plugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One plugin's subscriptions to other plugins' sources (`Pebble.subscribeSource`).
 *
 * Each is owned by the plugin's own subscribers whose runs declared it, and lasts while any of
 * them does. A run redeclares what it needs: whatever it didn't declare is dropped for the owners
 * it ran for. A change re-runs the owners, debounced.
 */
internal class PluginUpstreams(
    private val scope: CoroutineScope,
    private val open: (SourceRequest) -> Flow<SourceUpdate>,
    private val rerun: suspend (owners: Set<Int>, trigger: SourceRequest) -> Unit,
    private val debounce: Duration = DEBOUNCE,
    private val max: Int = MAX_SUBSCRIPTIONS,
) {
    private val logger = Logger.withTag("PluginUpstreams")

    private class Upstream(val latest: MutableStateFlow<SourceUpdate?>, var owners: Set<Int>) {
        var job: Job? = null
    }

    private val lock = SynchronizedObject()
    private val upstreams = mutableMapOf<SourceRequest, Upstream>()

    /** The run in progress: who it's for, and what it has declared so far. */
    private var runOwners: Set<Int>? = null
    private val runDeclared = mutableSetOf<SourceRequest>()

    fun beginRun(owners: Set<Int>) = synchronized(lock) {
        runOwners = owners
        runDeclared.clear()
    }

    /** False if it isn't kept: no run with owners is in progress, or there are too many already. */
    fun declare(request: SourceRequest): Boolean = synchronized(lock) {
        val owners = runOwners?.takeIf { it.isNotEmpty() } ?: return false
        runDeclared += request
        val existing = upstreams[request]
        if (existing != null) {
            existing.owners += owners
            return true
        }
        if (upstreams.size >= max) {
            logger.w { "subscription to ${request.key} refused: at most $max" }
            return false
        }
        upstreams[request] = Upstream(MutableStateFlow(null), owners).also { it.job = start(request, it.latest) }
        true
    }

    fun endRun() {
        val (owners, declared) = synchronized(lock) {
            val owners = runOwners ?: return
            runOwners = null
            owners to runDeclared.toSet()
        }
        if (owners.isNotEmpty()) drop(owners) { it !in declared }
    }

    /** The live value of [request], if subscribed to it; null otherwise. */
    suspend fun latest(request: SourceRequest, timeout: Duration = Plugins.DEFAULT_READ_TIMEOUT): SourceUpdate? {
        val upstream = synchronized(lock) { upstreams[request] } ?: return null
        return withTimeoutOrNull(timeout) { upstream.latest.filterNotNull().first() }
            ?: SourceUpdate.Error(PluginErrors.TIMEOUT, "no data from ${request.key} in $timeout")
    }

    fun ownerGone(owner: Int) = drop(setOf(owner)) { true }

    fun close() {
        val all = synchronized(lock) { upstreams.values.toList().also { upstreams.clear() } }
        all.forEach { it.job?.cancel() }
    }

    val subscribed: Set<SourceRequest> get() = synchronized(lock) { upstreams.keys.toSet() }

    @OptIn(FlowPreview::class)
    private fun start(request: SourceRequest, latest: MutableStateFlow<SourceUpdate?>): Job = scope.launch {
        open(request)
            .onEach { latest.value = it }
            // A JS source's `validUntilMs` moves on every poll; only what it says counts as a change.
            .distinctUntilChangedBy { it.content() }
            // The first value is what the declaring run reads; only changes after it re-run.
            .drop(1)
            .debounce(debounce)
            .collect {
                val owners = synchronized(lock) { upstreams[request]?.owners.orEmpty() }
                if (owners.isNotEmpty()) rerun(owners, request)
            }
    }

    /** Takes [owners] off the subscriptions [which] picks, ending those nobody else owns. */
    private fun drop(owners: Set<Int>, which: (SourceRequest) -> Boolean) {
        val ended = synchronized(lock) {
            upstreams.entries.filter { which(it.key) }.mapNotNull { (request, upstream) ->
                upstream.owners -= owners
                if (upstream.owners.isEmpty()) request to upstream else null
            }.onEach { (request, _) -> upstreams.remove(request) }.map { it.second }
        }
        ended.forEach { it.job?.cancel() }
    }

    private fun SourceUpdate.content(): Any = when (this) {
        is SourceUpdate.Data -> envelope.pluginUuid to envelope.instances
        is SourceUpdate.Error -> this
    }

    companion object {
        val DEBOUNCE = 1.seconds
        const val MAX_SUBSCRIPTIONS = 8
    }
}
