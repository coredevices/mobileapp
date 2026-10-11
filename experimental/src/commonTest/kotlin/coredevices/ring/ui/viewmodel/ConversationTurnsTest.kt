@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.MessageTag
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.client.LazyLoadingMcpSession
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.builtin_servlets.plugins.ReadSourceTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConversationTurnsTest {

    private var nextId = 1L

    private fun message(doc: ConversationMessageDocument) =
        ConversationMessageEntity(id = nextId++, recordingId = 1, document = doc)

    private fun user(text: String) = message(ConversationMessageDocument(MessageRole.user, text))

    private fun assistant(text: String? = null, vararg calls: Pair<String, String>) = message(
        ConversationMessageDocument(
            role = MessageRole.assistant,
            content = text,
            tool_calls = calls.map { (id, name) -> ToolCall(id, "function", FunctionToolCall(name, "{}")) },
        )
    )

    private fun answer(text: String) = message(
        ConversationMessageDocument(
            role = MessageRole.assistant,
            content = text,
            messageTags = listOf(MessageTag.AnswerResponse()),
        )
    )

    private fun toolResult(callId: String, result: SemanticResult? = null) = message(
        ConversationMessageDocument(role = MessageRole.tool, content = "", tool_call_id = callId, semantic_result = result)
    )

    private fun action(callId: String, result: SemanticResult) =
        RecordingAction(callId, result, label = "label", glyph = Icons.Filled.Menu, itemId = null)

    private fun assistantTurn(turns: List<ConversationTurn>, index: Int = turns.lastIndex) =
        assertIs<ConversationTurn.Assistant>(turns[index])

    @Test
    fun foldsConsecutiveAssistantMessagesIntoOneTurn() {
        val messages = listOf(
            user("add milk"),
            assistant("Let me check", "c1" to ReadSourceTool.TOOL_NAME),
            toolResult("c1", SemanticResult.SupportingData("sources")),
            assistant(null, "c2" to "create_list_item"),
            toolResult("c2", SemanticResult.ListItemCreation("milk")),
            assistant("Done!"),
        )
        val actions = mapOf<String?, RecordingAction>(
            "c1" to action("c1", SemanticResult.SupportingData("sources")),
            "c2" to action("c2", SemanticResult.ListItemCreation("milk")),
        )
        val turns = conversationTurns(messages, actions)

        assertEquals(2, turns.size)
        assertEquals(ConversationTurn.User("add milk"), turns[0])
        val turn = assistantTurn(turns)
        assertEquals(
            listOf(
                AssistantPart.Reply("Let me check"),
                AssistantPart.ToolCalls(listOf(ToolCallStep("c1", ReadSourceTool.TOOL_NAME, done = true))),
                AssistantPart.Actions(listOf(actions.getValue("c2"))),
                AssistantPart.Reply("Done!"),
            ),
            turn.parts,
        )
    }

    @Test
    fun pendingCallIsAnUnfinishedStep() {
        val turns = conversationTurns(
            listOf(user("hi"), assistant(null, "c1" to "search_contacts")),
            emptyMap(),
        )
        assertEquals(
            listOf(AssistantPart.ToolCalls(listOf(ToolCallStep("c1", "search_contacts", done = false)))),
            assistantTurn(turns).parts,
        )
    }

    @Test
    fun callWithResultButNoSemanticResultIsADoneStep() {
        val turns = conversationTurns(
            listOf(user("hi"), assistant(null, "c1" to "search_contacts"), toolResult("c1")),
            emptyMap(),
        )
        assertEquals(
            listOf(AssistantPart.ToolCalls(listOf(ToolCallStep("c1", "search_contacts", done = true)))),
            assistantTurn(turns).parts,
        )
    }

    @Test
    fun genericSuccessResultIsAStep() {
        val turns = conversationTurns(
            listOf(user("hi"), assistant(null, "c1" to "ha__get_overview"), toolResult("c1", SemanticResult.GenericSuccess)),
            mapOf("c1" to action("c1", SemanticResult.GenericSuccess)),
        )
        assertEquals(
            listOf(AssistantPart.ToolCalls(listOf(ToolCallStep("c1", "ha__get_overview", done = true)))),
            assistantTurn(turns).parts,
        )
    }

    @Test
    fun answersAndErrorsBecomeReplies() {
        val failure = SemanticResult.GenericFailure("Could not send")
        val turns = conversationTurns(
            listOf(
                user("time?"),
                assistant(null, "c2" to "send_message"),
                toolResult("c2", failure),
                answer("It is 5pm"),
            ),
            mapOf("c2" to action("c2", failure)),
        )
        val turn = assistantTurn(turns)
        assertEquals(listOf("Could not send", "It is 5pm"), turn.replies)
        assertEquals(
            listOf(AssistantPart.Actions(listOf(action("c2", failure)))),
            turn.parts.filterIsInstance<AssistantPart.Actions>(),
        )
        assertTrue(turn.parts.none { it is AssistantPart.ToolCalls })
    }

    @Test
    fun forcedToolResultWithoutCallGetsItsOwnTurn() {
        val result = SemanticResult.ListItemCreation("note")
        val turns = conversationTurns(
            listOf(user("note"), assistant("OK"), toolResult("forced", result)),
            mapOf("forced" to action("forced", result)),
        )
        val turn = assistantTurn(turns)
        assertEquals(
            listOf(AssistantPart.Reply("OK"), AssistantPart.Actions(listOf(action("forced", result)))),
            turn.parts,
        )
    }

    @Test
    fun consecutiveStepsMergeIntoOneGroupUntilInterrupted() {
        val note = SemanticResult.ListItemCreation("note")
        val turns = conversationTurns(
            listOf(
                user("hi"),
                assistant(null, "c1" to "a", "c2" to "b"),
                toolResult("c1"),
                toolResult("c2"),
                assistant(null, "c3" to "create_note"),
                toolResult("c3", note),
                assistant(null, "c4" to "c"),
            ),
            mapOf("c3" to action("c3", note)),
        )
        assertEquals(
            listOf(
                AssistantPart.ToolCalls(listOf(ToolCallStep("c1", "a", true), ToolCallStep("c2", "b", true))),
                AssistantPart.Actions(listOf(action("c3", note))),
                AssistantPart.ToolCalls(listOf(ToolCallStep("c4", "c", false))),
            ),
            assistantTurn(turns).parts,
        )
    }

    @Test
    fun userMessageStartsANewTurn() {
        val turns = conversationTurns(
            listOf(user("one"), assistant("a"), user("two"), assistant("b")),
            emptyMap(),
        )
        assertEquals(4, turns.size)
        assertEquals(listOf("a"), assistantTurn(turns, 1).replies)
        assertEquals(ConversationTurn.User("two"), turns[2])
        assertEquals(listOf("b"), assistantTurn(turns, 3).replies)
    }

    @Test
    fun customToolLabels() {
        assertEquals(ToolCallLabel("Reading data sources", "Read data sources"), toolCallLabel(ReadSourceTool.TOOL_NAME))
        assertEquals(ToolCallLabel("Loading MCP plugin", "Loaded MCP plugin"), toolCallLabel(LazyLoadingMcpSession.LOAD_TOOLS_NAME))
        assertEquals(
            ToolCallLabel("Loading MCP plugin", "Loaded MCP plugin"),
            toolCallLabel("${LazyLoadingMcpSession.LOADER_INTEGRATION_NAME}__${LazyLoadingMcpSession.LOAD_TOOLS_NAME}"),
        )
        assertEquals(ToolCallLabel("Calling search contacts", "Called search contacts"), toolCallLabel("search_contacts"))
        assertEquals(ToolCallLabel("Calling tool", "Called tool"), toolCallLabel(null))
    }

    @Test
    fun groupLabelUsesSingleStepLabelThenCount() {
        val one = listOf(ToolCallStep("c1", ReadSourceTool.TOOL_NAME, done = false))
        assertEquals("Reading data sources", toolCallsLabel(one, active = true))
        assertEquals("Read data sources", toolCallsLabel(one, active = false))

        val many = one + ToolCallStep("c2", "search_contacts", done = true) + ToolCallStep("c3", "get_time", done = false)
        assertEquals("Calling 3 tools", toolCallsLabel(many, active = true))
        assertEquals("Called 3 tools", toolCallsLabel(many, active = false))
    }

    @Test
    fun callAwaitingConfirmationIsAPrompt() {
        val request = SemanticResult.RequiresConfirmation(
            "Music", "clear_queue", JsonObject(mapOf("all" to JsonPrimitive(true))), destructive = true,
        )
        val turns = conversationTurns(
            listOf(user("clear the queue"), assistant(null, "c1" to "music__clear_queue"), toolResult("c1", request)),
            mapOf("c1" to action("c1", request)),
        )
        assertEquals(listOf(AssistantPart.Confirmation("c1", request)), assistantTurn(turns).parts)
    }

    @Test
    fun userTurnIsTranscribedOfflineWhenItsEntryFellBackToLocal() {
        val spoken = user("add milk")
        val entries = listOf(
            RecordingEntryEntity(id = 1, recordingId = 1, userMessageId = spoken.id, transcribedViaLocalFallback = true),
        )
        val turns = conversationTurns(listOf(spoken, user("again")), emptyMap(), entries)
        assertEquals(ConversationTurn.User("add milk", transcribedOffline = true), turns[0])
        assertEquals(ConversationTurn.User("again"), turns[1])
    }

    @Test
    fun assistantTurnIsProcessedOfflineWhenTheOnDeviceAgentReplied() {
        fun reply(model: String?) = message(
            ConversationMessageDocument(role = MessageRole.assistant, content = "Done", language_model_used = model)
        )
        assertTrue(assistantTurn(conversationTurns(listOf(user("hi"), reply("cactus-qwen")), emptyMap())).processedOffline)
        assertFalse(assistantTurn(conversationTurns(listOf(user("hi"), reply("gemini-flash")), emptyMap())).processedOffline)
    }
}
