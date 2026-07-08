package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.AgentToolCall
import coredevices.indexai.agent.ToolCallingAgent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.SemanticResult
import coredevices.ring.api.NenyaClient
import coredevices.ring.api.NenyaModel
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import org.koin.core.component.KoinComponent

/**
 * Online agent backed by the Nenya Search model. A single inference produces an
 * answer that is emitted as a tool-result ([SemanticResult.SupportingData]) and
 * persisted as an answer item. It has no tool-calling loop, so it inherits
 * [ToolCallingAgent] directly rather than [coredevices.indexai.agent.IterativeAgent].
 */
class SearchAgentNenya(
    private val nenyaClient: NenyaClient,
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
    conversation: List<ConversationMessageDocument>,
) : KoinComponent, ToolCallingAgent(conversation) {
    override val label = "Nenya"

    override val logger: Logger = Logger.withTag("SearchAgentNenya")

    companion object {
        private val AGENT_CONTEXT = """
            Provide a concise answer to the query after searching the internet, to be shown on a small display.
            The answer should have no additional commentary or markdown formatting.
            If the user asks for something general, provide a brief summary of the most relevant information you found, e.g. news, weather, sports scores, etc.
            If the user asks for something specific, try to find a specific answer to their question, e.g. "What's the weather in New York?" -> "75 fahrenheit and sunny as of 5pm".

            You are the search mode of the user's Index 01 device, started by double clicking and holding the button on Index 01. Search mode only answers questions and has no tools, so it cannot take notes, set reminders, set timers or alarms, create calendar events, or send messages. All of those work in normal mode, started by a single click and hold of the button on Index 01. Always call the device "Index 01", never "the ring".
            ONLY if you are very certain the query is an attempt to perform one of those actions rather than a question to answer (e.g. "remind me to call mom tomorrow", "set a timer for 10 minutes", "take a note that the gate code is 1234"), respond with a single short sentence telling the user to single click and hold the button on Index 01 and repeat the request, e.g. "Search mode can't set reminders - single click and hold the button on Index 01 and ask again.". Never recommend other apps or assistants.
            In every other case, even if the query merely mentions notes, reminders, timers or similar, treat it as a question and answer it normally.
        """.trimIndent()
    }

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean,
    ) {
        emit(ConversationMessageDocument(role = MessageRole.user, content = input))
        val resp = try {
            nenyaClient.run(
                conversationHistory = currentConversation(),
                toolSpecs = emptyList(),
                additionalContext = AGENT_CONTEXT,
                model = NenyaModel.Search
            )
        } catch (e: IOException) {
            throw AgentNetworkException("Network error when running agent: ${e.message}", e)
        }
        if (!resp.statusCode.isSuccess()) {
            if (resp.statusCode.value in 501..504) {
                throw AgentNetworkException("Network error at gateway when running agent: ${resp.statusCode} (${resp.response?.message})")
            } else {
                throw Exception("Failed to run agent: ${resp.statusCode} (${resp.response?.message})")
            }
        }
        val text = resp.response?.conversation?.last()!!.toConversationMessage(resp.response.language_model_used).content
            ?.replace("**", "") // remove markdown bolding
        emit(
            ConversationMessageDocument(
                role = MessageRole.tool,
                content = "",
                semantic_result = SemanticResult.SupportingData(text ?: "No results", assistiveOnly = false, question = input)
            )
        )
    }

    // Search does not use the iterative tool-calling contract.
    override suspend fun runInference(
        input: String,
        history: List<ConversationMessageDocument>,
        tools: List<McpSessionTool>,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>
    ): ConversationMessageDocument =
        throw UnsupportedOperationException("SearchAgentNenya does not use the inference loop")

    override fun decodeToolCalls(
        assistantMessage: ConversationMessageDocument
    ): List<AgentToolCall> = emptyList()
}
