package coredevices.ring.agent

import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notifications.NotificationReplyServlet
import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.agent.pebble_plugin.agentActions
import coredevices.ring.agent.pebble_plugin.agentSources
import coredevices.ring.agent.pebble_plugin.forAgent
import coredevices.ring.agent.pebble_plugin.forWatchPaired
import coredevices.ring.agent.pebble_plugin.offers
import coredevices.ring.agent.pebble_plugin.withGrant
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

const val LOCAL_MODEL_REASON = "Not supported by the Local agent"
const val LOCAL_MODEL_MCP_REASON = "Remote MCP servers need the Index agent"
const val NOT_CONNECTED_REASON = "Not connected"
const val NO_PEBBLE_REASON = "Connect a Pebble to enable this"

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
    private val installedPlugins: () -> List<PluginMetadata>,
    private val disabledPlugins: (PluginGrant) -> Flow<Set<Uuid>>,
    private val persistPluginGrantEnabled: suspend (pluginUuid: Uuid, grant: PluginGrant, enabled: Boolean) -> Unit,
    private val watchPaired: Flow<Boolean>,
) {
    // Plugins register without a flow to observe, so callers poke this when they may have changed.
    private val pluginsVersion = MutableStateFlow(0)

    /** Installed plugins with something for the agent, less the watch-bound ones while no watch is paired. */
    private val agentPlugins: Flow<List<PluginMetadata>> =
        combine(watchPaired, pluginsVersion) { paired, _ -> installedPlugins().forAgent().forWatchPaired(paired) }

    /** The plugins the agent may currently use for [grant]. */
    fun grantedPlugins(grant: PluginGrant): Flow<List<PluginMetadata>> =
        combine(agentPlugins, disabledPlugins(grant)) { plugins, disabled -> plugins.withGrant(grant, disabled) }

    val plugins: Flow<List<IndexPlugin>> =
        combine(
            agentPlugins,
            disabledPlugins(PluginGrant.Actions),
            disabledPlugins(PluginGrant.Sources),
            localAgentOnly,
        ) { plugins, disabledActions, disabledSources, localOnly ->
            plugins.map {
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
            watchPaired,
        ) { disabled, localOnly, calendar, beeper, paired ->
            servletRepository.getAllServlets()
                .map { it.toIndexAction(it.name !in disabled, localOnly, calendar, beeper, paired) }
        }

    /** Non-null while the assistant model cannot talk to remote MCP servers at all. */
    val httpMcpDisabledReason: Flow<String?> =
        localAgentOnly.map { LOCAL_MODEL_MCP_REASON.takeIf { _ -> it } }

    suspend fun setActionEnabled(name: String, enabled: Boolean) =
        setBuiltinEnabled(name, enabled)
}

private fun PluginMetadata.grantState(
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
    watchPaired: Boolean = true,
) = IndexAction(
    name = name,
    title = title,
    enabled = enabled && unavailableReason == null,
    disabledReason = unavailableReason
        ?: LOCAL_MODEL_REASON.takeIf { localAgentOnly && name in LOCAL_MODEL_UNSUPPORTED_SERVLETS }
        ?: NOT_CONNECTED_REASON.takeIf { name == CalendarServlet.NAME && !calendarConnected }
        ?: beeperUnavailable?.takeIf { name == MessagingServlet.name }
        ?: NO_PEBBLE_REASON.takeIf { name == NotificationReplyServlet.NAME && !watchPaired },
)
