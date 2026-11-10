package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.connection.Plugins
import io.rebble.libpebblecommon.plugin.ActionResult
import io.rebble.libpebblecommon.plugin.ConfigMessageTarget
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.NativePlugin
import io.rebble.libpebblecommon.plugin.Plugin
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** Calls the plugins it was given directly, recording who called. */
internal class FakePlugins(vararg plugins: Plugin) : Plugins {
    private val plugins = plugins.associateBy { it.pluginUuid }.toMutableMap()
    val callers = mutableListOf<PluginCaller>()

    fun install(plugin: Plugin): PluginMetadata {
        plugins[plugin.pluginUuid] = plugin
        return plugin.metadata
    }

    override fun registerPlugin(plugin: NativePlugin) {
        install(plugin)
    }

    override val disabledBuiltInPlugins: StateFlow<Set<Uuid>> = MutableStateFlow(emptySet())

    override fun setBuiltInPluginEnabled(pluginUuid: Uuid, enabled: Boolean) = Unit

    override fun nativePlugins(): Flow<List<PluginMetadata>> = flowOf(emptyList())

    override fun configMessageTarget(pluginUuid: String): ConfigMessageTarget? = null

    override val preferredSourcePlugins: StateFlow<Map<String, Uuid>> = MutableStateFlow(emptyMap())

    override fun setPreferredSourcePlugin(category: String, item: String, pluginUuid: Uuid?) = Unit

    override fun sourcePlugin(category: String, item: String): PluginMetadata? =
        getAllPlugins().firstOrNull { it.serves(category, item) }

    override fun getAllPlugins(): List<PluginMetadata> = plugins.values.map { it.metadata }

    override fun observe(
        caller: PluginCaller,
        plugin: PluginMetadata,
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceUpdate> {
        callers += caller
        val target = plugins[plugin.pluginUuid]
            ?: return flowOf(SourceUpdate.Error(PluginErrors.PLUGIN_UNAVAILABLE, "not installed"))
        return target.observe(category, item, properties, iconPixelSize, !caller.probe)
    }

    override suspend fun read(
        caller: PluginCaller,
        plugin: PluginMetadata,
        category: String,
        item: String,
        properties: List<String>?,
        timeout: Duration,
    ): SourceUpdate = withTimeoutOrNull(timeout) {
        observe(caller, plugin, category, item, properties).firstOrNull()
            ?: SourceUpdate.Error(PluginErrors.UNKNOWN, "no data")
    } ?: SourceUpdate.Error(PluginErrors.TIMEOUT, "timed out")

    override suspend fun invoke(
        caller: PluginCaller,
        plugin: PluginMetadata,
        action: String,
        args: JsonObject,
        timeout: Duration?,
    ): ActionResult {
        callers += caller
        val target = plugins[plugin.pluginUuid]
            ?: return ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "not installed")
        return target.invoke(action, args, !caller.probe)
    }
}
