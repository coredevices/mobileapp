package coredevices.ring.agent

import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.database.room.repository.McpServerEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

const val LOCAL_MODEL_REASON = "Not supported by the Local LLM"
const val LOCAL_MODEL_MCP_REASON = "Remote MCP servers need the Cloud LLM"
const val NOT_CONNECTED_REASON = "Not connected"

/** A built-in action as shown in the top level Index settings list. */
data class IndexAction(
    val name: String,
    val title: String,
    val enabled: Boolean,
    /** Non-null when the user cannot change [enabled]; the string explains why. */
    val disabledReason: String? = null,
)

/**
 * The built-in actions of the default MCP sandbox group, which is what the Index agent runs.
 */
class IndexActionsRepository(
    private val servletRepository: ServletRepository,
    private val defaultGroupEntries: () -> Flow<List<McpServerEntry>>,
    private val setEnabledInDefaultGroup: suspend (name: String, enabled: Boolean) -> Unit,
    private val llmMode: StateFlow<LlmMode>,
    private val calendarConnected: Flow<Boolean>,
    private val beeperUnavailable: Flow<String?>,
) {
    val actions: Flow<List<IndexAction>> =
        combine(
            defaultGroupEntries(),
            llmMode,
            calendarConnected,
            beeperUnavailable,
        ) { entries, mode, calendar, beeper ->
            val enabled = entries
                .filterIsInstance<McpServerEntry.BuiltinMcpEntry>()
                .mapTo(mutableSetOf()) { it.builtinMcpName }
            servletRepository.getAllServlets()
                .map { it.toIndexAction(it.name in enabled, mode, calendar, beeper) }
        }

    /** Non-null while the assistant model cannot talk to remote MCP servers at all. */
    val httpMcpDisabledReason: Flow<String?> =
        llmMode.map { LOCAL_MODEL_MCP_REASON.takeIf { _ -> it == LlmMode.LocalOnly } }

    suspend fun setActionEnabled(name: String, enabled: Boolean) =
        setEnabledInDefaultGroup(name, enabled)
}

internal fun McpServerDefinition.toIndexAction(
    enabled: Boolean,
    llmMode: LlmMode,
    calendarConnected: Boolean,
    beeperUnavailable: String? = null,
) = IndexAction(
    name = name,
    title = title,
    enabled = enabled && unavailableReason == null,
    disabledReason = unavailableReason
        ?: LOCAL_MODEL_REASON.takeIf {
            llmMode == LlmMode.LocalOnly && name in LOCAL_MODEL_UNSUPPORTED_SERVLETS
        }
        ?: NOT_CONNECTED_REASON.takeIf { name == CalendarServlet.NAME && !calendarConnected }
        ?: beeperUnavailable?.takeIf { name == MessagingServlet.name },
)
