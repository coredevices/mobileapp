package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.connection.Plugins
import io.rebble.libpebblecommon.permissions.PermissionSubject
import io.rebble.libpebblecommon.permissions.coveringKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** What a plugin passes to `Pebble.readSource` / `Pebble.subscribeSource`. */
@Serializable
data class SourceRequest(
    val category: String,
    val item: String,
    val plugin: String? = null,
    val properties: List<String>? = null,
    val iconPixelSize: IconPixelSize? = null,
) {
    val key: String get() = "$category/$item"
}

/**
 * Sources one plugin reads from another, as a caller in its own right.
 *
 * Whatever a plugin reads can reach its own callers, so for each caller permission the source
 * requires, every one of the plugin's own sources and actions must require at least as much of
 * their callers as the plugin itself holds: `Notifications/<package>` if that's all it declared.
 * That holds along a chain (A reads B reads C), so nothing has to be passed through at runtime.
 */
class PluginSources(
    private val registry: PluginRegistry,
    private val subjectOf: suspend (Uuid) -> PermissionSubject?,
    /** What a plugin declared under a permission (`Notifications/<package>`), else the permission itself. */
    private val declaredKeys: suspend (Uuid, String) -> List<String>,
    private val observe: (PluginCaller, PluginMetadata, SourceRequest) -> Flow<SourceUpdate>,
) {
    /** Who is reading whom right now, so a loop can be refused before it deadlocks. */
    private val reading = MutableStateFlow<Map<Pair<Uuid, Uuid>, Int>>(emptyMap())

    /** [reader]'s view of [request], for as long as it's collected. */
    fun observe(reader: PluginMetadata, request: SourceRequest): Flow<SourceUpdate> = flow {
        val target = registry.findSourcePlugin(request.category, request.item, request.plugin, exclude = reader.pluginUuid)
        if (target == null) {
            emit(SourceUpdate.Error(PluginErrors.PLUGIN_UNAVAILABLE, "nothing else serves ${request.key}"))
            return@flow
        }
        val required = requiredOfCaller(target.sources.first { it.serves(request.category, request.item) })
        val held = required.flatMap { declaredKeys(reader.pluginUuid, it) }.distinct()
        val unrelayable = held.filterNot { everyCallerNeeds(reader, it) }
        if (unrelayable.isNotEmpty()) {
            emit(
                SourceUpdate.Error(
                    PluginErrors.PERMISSION_DENIED,
                    "every source and action of ${reader.name} must require ${unrelayable.joinToString()} of its callers",
                )
            )
            return@flow
        }
        val subject = subjectOf(reader.pluginUuid)
        if (subject == null) {
            emit(SourceUpdate.Error(PluginErrors.PERMISSION_DENIED, "${reader.name} isn't installed"))
            return@flow
        }
        val edge = reader.pluginUuid to target.pluginUuid
        if (!startReading(edge)) {
            emit(SourceUpdate.Error(PluginErrors.INVALID_REQUEST, "${target.name} already reads ${reader.name}, directly or not"))
            return@flow
        }
        try {
            emitAll(observe(PluginCaller.App(subject, reader.name), target.metadata, request))
        } finally {
            stopReading(edge)
        }
    }

    /** [reader]'s first view of [request]. */
    suspend fun read(reader: PluginMetadata, request: SourceRequest, timeout: Duration = Plugins.DEFAULT_READ_TIMEOUT): SourceUpdate =
        withTimeoutOrNull(timeout) { observe(reader, request).first() }
            ?: SourceUpdate.Error(PluginErrors.TIMEOUT, "no data from ${request.key} in $timeout")

    /** Refuses an edge from which the reader can be reached again. */
    private fun startReading(edge: Pair<Uuid, Uuid>): Boolean {
        var added = false
        reading.update { edges ->
            added = !reaches(edges.keys, from = edge.second, to = edge.first)
            if (added) edges + (edge to (edges[edge] ?: 0) + 1) else edges
        }
        return added
    }

    private fun stopReading(edge: Pair<Uuid, Uuid>) {
        reading.getAndUpdate { edges ->
            val count = (edges[edge] ?: 1) - 1
            if (count <= 0) edges - edge else edges + (edge to count)
        }
    }

    private fun reaches(edges: Set<Pair<Uuid, Uuid>>, from: Uuid, to: Uuid): Boolean {
        val seen = mutableSetOf<Uuid>()
        val next = ArrayDeque(listOf(from))
        while (next.isNotEmpty()) {
            val at = next.removeFirst()
            if (at == to) return true
            if (seen.add(at)) edges.filter { it.first == at }.forEach { next.add(it.second) }
        }
        return false
    }
}

/** Whether every one of [plugin]'s sources and actions requires [key] of its callers, or something covering it. */
internal fun everyCallerNeeds(plugin: PluginMetadata, key: String): Boolean {
    val each = plugin.sources.map(::requiredOfCaller) + plugin.actions.map(::requiredOfCaller)
    val covering = coveringKeys(key)
    return each.isNotEmpty() && each.all { required -> required.any { it in covering } }
}
