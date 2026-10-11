package coredevices.ring.agent

import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.agent.pebble_plugin.agentActions
import coredevices.ring.agent.pebble_plugin.agentSources
import coredevices.ring.agent.pebble_plugin.forAgent
import coredevices.ring.agent.pebble_plugin.offers
import io.rebble.libpebblecommon.plugin.Plugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

const val LOCAL_MODEL_REASON = "Not supported by the Local agent"
const val LOCAL_MODEL_MCP_REASON = "Remote MCP servers need the Hybrid Index agent"
const val NOT_CONNECTED_REASON = "Not connected"

/** A built-in action as shown in the top level Index settings list. */
data class IndexAction(
    val name: String,
    val title: String,
    val enabled: Boolean,
    /** Non-null when the user cannot change [enabled]; the string explains why. */
    val disabledReason: String? = null,
)

/** One [PluginGrant] of an installed plugin: what it covers and whether the user allows it. */
data class PluginGrantState(
    val names: List<String>,
    val enabled: Boolean,
    /** Non-null when the user cannot change [enabled]; the string explains why. */
    val disabledReason: String? = null,
)

/** An installed plugin listed below the built-in actions, with a checkbox per grant it offers. */
data class IndexPlugin(
    val pluginUuid: Uuid,
    val title: String,
    /** Null when the plugin declares no actions for the agent. */
    val actions: PluginGrantState?,
    /** Null when the plugin declares no data sources. */
    val sources: PluginGrantState?,
) {
    val grants: Map<PluginGrant, PluginGrantState>
        get() = buildMap {
            actions?.let { put(PluginGrant.Actions, it) }
            sources?.let { put(PluginGrant.Sources, it) }
        }
}

/**
 * The built-in actions the Index agent runs, plus the installed plugins that add actions or data
 * sources to it.
 */
class IndexActionsRepository(
    private val servletRepository: ServletRepository,
    private val disabledBuiltins: () -> Flow<Set<String>>,
    private val setBuiltinEnabled: suspend (name: String, enabled: Boolean) -> Unit,
    /** True while gestures reach the Index agent only through its on-device model. */
    private val localAgentOnly: Flow<Boolean>,
    private val calendarConnected: Flow<Boolean>,
    private val beeperUnavailable: Flow<String?>,
    private val installedPlugins: () -> List<Plugin>,
    private val disabledPlugins: (PluginGrant) -> Flow<Set<Uuid>>,
    private val persistPluginGrantEnabled: suspend (pluginUuid: Uuid, grant: PluginGrant, enabled: Boolean) -> Unit,
) {
    // Plugins register without a flow to observe, so callers poke this when they may have changed.
    private val pluginsVersion = MutableStateFlow(0)

    val plugins: Flow<List<IndexPlugin>> =
        combine(
            disabledPlugins(PluginGrant.Actions),
            disabledPlugins(PluginGrant.Sources),
            localAgentOnly,
            pluginsVersion,
        ) { disabledActions, disabledSources, localOnly, _ ->
            installedPlugins().forAgent().map {
                IndexPlugin(
                    pluginUuid = it.pluginUuid,
                    title = it.name,
                    actions = it.grantState(
                        PluginGrant.Actions,
                        disabledActions,
                        LOCAL_MODEL_REASON.takeIf { localOnly },
                    ),
                    sources = it.grantState(PluginGrant.Sources, disabledSources, null),
                )
            }
        }

    fun refreshPlugins() = pluginsVersion.update { it + 1 }

    suspend fun setPluginGrantEnabled(pluginUuid: Uuid, grant: PluginGrant, enabled: Boolean) =
        persistPluginGrantEnabled(pluginUuid, grant, enabled)

    val actions: Flow<List<IndexAction>> =
        combine(
            disabledBuiltins(),
            localAgentOnly,
            calendarConnected,
            beeperUnavailable,
        ) { disabled, localOnly, calendar, beeper ->
            servletRepository.getAllServlets()
                .map { it.toIndexAction(it.name !in disabled, localOnly, calendar, beeper) }
        }

    /** Non-null while the assistant model cannot talk to remote MCP servers at all. */
    val httpMcpDisabledReason: Flow<String?> =
        localAgentOnly.map { LOCAL_MODEL_MCP_REASON.takeIf { _ -> it } }

    suspend fun setActionEnabled(name: String, enabled: Boolean) =
        setBuiltinEnabled(name, enabled)
}

private fun Plugin.grantState(
    grant: PluginGrant,
    disabled: Set<Uuid>,
    disabledReason: String?,
): PluginGrantState? {
    if (!offers(grant)) return null
    return PluginGrantState(
        names = when (grant) {
            PluginGrant.Actions -> agentActions().map { it.name }
            PluginGrant.Sources -> agentSources()
        },
        enabled = pluginUuid !in disabled,
        disabledReason = disabledReason,
    )
}

internal fun McpServerDefinition.toIndexAction(
    enabled: Boolean,
    localAgentOnly: Boolean,
    calendarConnected: Boolean,
    beeperUnavailable: String? = null,
) = IndexAction(
    name = name,
    title = title,
    enabled = enabled && unavailableReason == null,
    disabledReason = unavailableReason
        ?: LOCAL_MODEL_REASON.takeIf { localAgentOnly && name in LOCAL_MODEL_UNSUPPORTED_SERVLETS }
        ?: NOT_CONNECTED_REASON.takeIf { name == CalendarServlet.NAME && !calendarConnected }
        ?: beeperUnavailable?.takeIf { name == MessagingServlet.name },
)
