package coredevices.ring.service.recordings.button

import coredevices.ring.agent.ChatMode
import coredevices.ring.service.button.GestureDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class TextChatModeTest {

    @Test
    fun indexAgentRouteRunsTheNormalAgent() {
        assertEquals(
            ChatMode.Normal,
            textChatModeFor(GestureDestination.IndexAgent),
        )
    }

    @Test
    fun localAgentRouteRunsTheOnDeviceAgent() {
        assertEquals(
            ChatMode.Local,
            textChatModeFor(GestureDestination.LocalAgent),
        )
    }

    @Test
    fun webSearchRouteRunsTheSearchAgent() {
        assertEquals(
            ChatMode.Search,
            textChatModeFor(GestureDestination.WebSearch),
        )
    }

    @Test
    fun agentPluginRouteFallsBackToTheNormalAgentWhenThePluginIsGone() {
        assertEquals(
            ChatMode.Normal,
            textChatModeFor(GestureDestination.AgentPlugin("plugin:claude")),
        )
    }

    @Test
    fun disabledRouteStillAnswersTypedInput() {
        assertEquals(
            ChatMode.Normal,
            textChatModeFor(GestureDestination.Nothing),
        )
    }
}
