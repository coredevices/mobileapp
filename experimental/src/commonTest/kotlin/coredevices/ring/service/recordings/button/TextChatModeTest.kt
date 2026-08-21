package coredevices.ring.service.recordings.button

import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.ring.agent.ChatMode
import coredevices.ring.service.button.GestureDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TextChatModeTest {

    @Test
    fun indexAgentRouteRunsTheNormalAgent() {
        assertEquals(
            ChatMode.Normal,
            textChatModeFor(GestureDestination.IndexAgent, sandboxGroup = null),
        )
    }

    @Test
    fun webSearchRouteRunsTheSearchAgent() {
        assertEquals(
            ChatMode.Search,
            textChatModeFor(GestureDestination.WebSearch, sandboxGroup = null),
        )
    }

    @Test
    fun sandboxRouteRunsThatGroup() {
        val group = McpSandboxGroupEntity(id = 7, title = "Work")

        val mode = textChatModeFor(GestureDestination.McpSandbox(7), sandboxGroup = group)

        assertIs<ChatMode.McpSandbox>(mode)
        assertEquals(group, mode.group)
    }

    @Test
    fun sandboxRouteFallsBackWhenTheGroupIsGone() {
        assertEquals(
            ChatMode.Normal,
            textChatModeFor(GestureDestination.McpSandbox(7), sandboxGroup = null),
        )
    }

    @Test
    fun disabledRouteStillAnswersTypedInput() {
        assertEquals(
            ChatMode.Normal,
            textChatModeFor(GestureDestination.Nothing, sandboxGroup = null),
        )
    }
}
