package io.rebble.libpebblecommon.plugin

import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.WatchConfigFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * App-wide registry of available plugins. Built-ins are injected via the Koin `Set<NativePlugin>`
 * binding; JS plugins [register] themselves once their manifest has been loaded.
 */
class PluginRegistry(
    builtIns: Set<NativePlugin>,
    private val watchConfig: WatchConfigFlow,
    private val settings: Settings,
) {
    private val natives = MutableStateFlow<Map<Uuid, NativePlugin>>(builtIns.associateBy { it.pluginUuid })

    private val _disabledBuiltIns by lazy {
        val stored = settings.getStringOrNull(DISABLED_BUILT_INS_KEY)
        MutableStateFlow(stored?.split(',')?.filter { it.isNotEmpty() }?.mapTo(HashSet(), Uuid::parse).orEmpty())
    }
    val disabledBuiltIns: StateFlow<Set<Uuid>> get() = _disabledBuiltIns

    private val _preferredSources by lazy {
        val stored = settings.getStringOrNull(PREFERRED_SOURCES_KEY)?.let {
            try {
                Json.decodeFromString<Map<String, String>>(it)
            } catch (e: SerializationException) {
                null
            }
        }
        MutableStateFlow(stored.orEmpty().mapValues { Uuid.parse(it.value) })
    }

    /** The user's choice of plugin for a source several plugins serve, keyed by [sourceKey]. */
    val preferredSources: StateFlow<Map<String, Uuid>> get() = _preferredSources

    /** Null goes back to the default order. */
    fun setPreferredSource(category: String, item: String, pluginUuid: Uuid?) {
        val key = sourceKey(category, item)
        val preferred = _preferredSources.updateAndGet { if (pluginUuid == null) it - key else it + (key to pluginUuid) }
        settings.putString(PREFERRED_SOURCES_KEY, Json.encodeToString(preferred.mapValues { it.value.toString() }))
    }

    /** Disabled built-ins are left out, so no caller can reach them. */
    private val registered by lazy {
        MutableStateFlow<Map<Uuid, Plugin>>(natives.value.filterKeys { it !in _disabledBuiltIns.value })
    }

    /** Every lookup goes through here, so disabling plugins hides them all. */
    private val plugins: Collection<Plugin>
        get() = if (watchConfig.value.enablePlugins) registered.value.values else emptyList()

    /** Every built-in, including disabled ones. */
    fun nativePlugins(): Flow<List<PluginMetadata>> = natives.map { all -> all.values.map { it.metadata } }

    fun setBuiltInEnabled(pluginUuid: Uuid, enabled: Boolean) {
        val plugin = natives.value[pluginUuid] ?: return
        val disabled = _disabledBuiltIns.updateAndGet { if (enabled) it - pluginUuid else it + pluginUuid }
        settings.putString(DISABLED_BUILT_INS_KEY, disabled.joinToString(","))
        registered.update { if (enabled) it + (pluginUuid to plugin) else it - pluginUuid }
    }

    fun configMessageTarget(pluginUuid: String): ConfigMessageTarget? =
        findPlugin(pluginUuid) as? ConfigMessageTarget

    fun registerPlugin(plugin: Plugin) {
        if (plugin is NativePlugin) {
            natives.update { it + (plugin.pluginUuid to plugin) }
            if (plugin.pluginUuid in _disabledBuiltIns.value) return
        }
        registered.update { it + (plugin.pluginUuid to plugin) }
    }

    fun unregisterPlugin(pluginUuid: Uuid): Plugin? {
        val plugin = registered.value[pluginUuid]
        registered.update { it - pluginUuid }
        return plugin
    }

    /**
     * Find a plugin serving the given (category, item) tuple: the caller's [preferredPluginUuid]
     * if it serves it, else the user's [preferredSources] choice, else built-ins first, then by name.
     * Sources are fungible — actions are not, so action lookup uses [findPlugin] by uuid.
     */
    fun findSourcePlugin(
        category: String,
        item: String,
        preferredPluginUuid: String? = null,
        /** A plugin reading another's source can't be answered by itself. */
        exclude: Uuid? = null,
    ): Plugin? {
        val serving = plugins.filter { it.serves(category, item) && it.pluginUuid != exclude }
        val userChoice = _preferredSources.value[sourceKey(category, item)]
        return serving.firstOrNull { it.pluginUuid.toString() == preferredPluginUuid }
            ?: serving.firstOrNull { it.pluginUuid == userChoice }
            ?: serving.minWithOrNull(compareBy({ it !is NativePlugin }, { it.name.lowercase() }))
    }

    /** Exact lookup. Actions must never fall back to a different plugin. */
    fun findPlugin(pluginUuid: String): Plugin? =
        plugins.firstOrNull { it.pluginUuid.toString() == pluginUuid }

    fun getAllPlugins(): List<PluginMetadata> = plugins.map { it.metadata }

    companion object {
        private const val DISABLED_BUILT_INS_KEY = "disabled_built_in_plugins"
        private const val PREFERRED_SOURCES_KEY = "preferred_source_plugins"

        fun sourceKey(category: String, item: String) = "$category/$item"
    }
}
