package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.ConfigurablePlugin
import io.rebble.libpebblecommon.connection.Plugins
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-wide registry of available plugins. Built-ins are injected via the Koin `Set<Plugin>`
 * binding; JS plugins [register] themselves once their manifest has been loaded.
 */
class PluginRegistry(
    builtIns: Set<Plugin>,
    private val watchConfig: WatchConfigFlow,
) : Plugins {
    private val registered = MutableStateFlow(builtIns.associateBy { it.pluginUuid })

    /** Every lookup goes through here, so disabling plugins hides them all. */
    private val plugins: Collection<Plugin>
        get() = if (watchConfig.value.enablePlugins) registered.value.values else emptyList()

    override fun configurablePlugins(): List<ConfigurablePlugin> = plugins.mapNotNull { plugin ->
        plugin.configPageUrl?.let {
            ConfigurablePlugin(plugin.pluginUuid.toString(), plugin.name, it)
        }
    }

    override fun configMessageTarget(pluginUuid: String): ConfigMessageTarget? =
        findPlugin(pluginUuid) as? ConfigMessageTarget

    override fun registerPlugin(plugin: Plugin) {
        registered.update { it + (plugin.pluginUuid to plugin) }
    }

    /**
     * Find a plugin serving the given (category, item) tuple. If [preferredPluginUuid] is set
     * and that plugin serves the tuple, prefer it; otherwise fall back to any plugin that does.
     * Sources are fungible — actions are not, so action lookup uses [findPlugin] by uuid.
     */
    fun findSourcePlugin(
        category: String,
        item: String,
        preferredPluginUuid: String? = null,
    ): Plugin? {
        val all = plugins
        if (preferredPluginUuid != null) {
            all.firstOrNull {
                it.pluginUuid.toString() == preferredPluginUuid && it.serves(category, item)
            }?.let { return it }
        }
        return all.firstOrNull { it.serves(category, item) }
    }

    /** Exact lookup. Actions must never fall back to a different plugin. */
    fun findPlugin(pluginUuid: String): Plugin? =
        plugins.firstOrNull { it.pluginUuid.toString() == pluginUuid }

    fun all(): List<Plugin> = plugins.toList()
}
