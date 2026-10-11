package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.Plugin
import kotlin.uuid.Uuid

/**
 * Action names reserved for note/reminder destinations (see [PblPluginIntegrationProvider]).
 * These are never exposed to the agent as MCP tools.
 */
val DESTINATION_ACTION_NAMES: Set<String> = setOf(
    PblPluginActionNoteIntegration.ACTION_NAME,
    PblPluginActionReminderIntegration.ACTION_NAME,
)

/** What a plugin can offer the Index agent; each is granted or withheld per plugin by the user. */
enum class PluginGrant { Actions, Sources }

/** The plugin's actions that become agent tools. */
fun Plugin.agentActions(): List<ActionDeclaration> = actions.filter { it.name !in DESTINATION_ACTION_NAMES }

/** The `category/item` paths the agent can read from this plugin. */
fun Plugin.agentSources(): List<String> =
    sources.flatMap { source -> source.items.map { "${source.category}/$it" } }

fun Plugin.offers(grant: PluginGrant): Boolean = when (grant) {
    PluginGrant.Actions -> agentActions().isNotEmpty()
    PluginGrant.Sources -> sources.isNotEmpty()
}

/** Plugins with something for the agent, in either form. */
fun List<Plugin>.forAgent(): List<Plugin> = filter { plugin -> PluginGrant.entries.any { plugin.offers(it) } }

fun List<Plugin>.withGrant(grant: PluginGrant, disabled: Set<Uuid>): List<Plugin> =
    filter { it.offers(grant) && it.pluginUuid !in disabled }
