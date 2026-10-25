package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.WatchSettingsPlugin
import io.rebble.libpebblecommon.plugin.WatchStatePlugin
import kotlin.uuid.Uuid

/**
 * Action names reserved for note/reminder destinations (see [PblPluginIntegrationProvider]).
 * These are never exposed to the agent as MCP tools; `send_to_agent` stays a tool as well.
 */
val DESTINATION_ACTION_NAMES: Set<String> = setOf(
    PblPluginActionNoteIntegration.ACTION_NAME,
    PblPluginActionReminderIntegration.ACTION_NAME,
)

/** What a plugin can offer the Index agent; each is granted or withheld per plugin by the user. */
enum class PluginGrant { Actions, Sources }

/** The plugin's actions that become agent tools. */
fun PluginMetadata.agentActions(): List<ActionDeclaration> = actions.filter { it.name !in DESTINATION_ACTION_NAMES }

/** The `category/item` paths the agent can read from this plugin. */
fun PluginMetadata.agentSources(): List<String> =
    sources.flatMap { source -> source.items.map { "${source.category}/$it" } }

fun PluginMetadata.offers(grant: PluginGrant): Boolean = when (grant) {
    PluginGrant.Actions -> agentActions().isNotEmpty()
    PluginGrant.Sources -> sources.isNotEmpty()
}

/** Built-in plugins that only do anything once a watch is paired. */
private val WATCH_PLUGIN_UUIDS: Set<Uuid> = setOf(
    WatchSettingsPlugin.BUILT_IN_WATCH_SETTINGS_UUID,
    WatchStatePlugin.BUILT_IN_WATCH_STATE_UUID,
)

fun PluginMetadata.needsWatch(): Boolean = pluginUuid in WATCH_PLUGIN_UUIDS

/** Plugins with something for the agent, in either form. */
fun List<PluginMetadata>.forAgent(): List<PluginMetadata> = filter { plugin -> PluginGrant.entries.any { plugin.offers(it) } }

/** Drops the watch-bound plugins while no watch is paired. */
fun List<PluginMetadata>.forWatchPaired(watchPaired: Boolean): List<PluginMetadata> =
    if (watchPaired) this else filterNot { it.needsWatch() }

fun List<PluginMetadata>.withGrant(grant: PluginGrant, disabled: Set<Uuid>): List<PluginMetadata> =
    filter { it.offers(grant) && it.pluginUuid !in disabled }
