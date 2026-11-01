package coredevices.ring.agent

import coredevices.mcp.SessionContext
import coredevices.mcp.client.LazyLoadingMcpSession
import coredevices.mcp.client.compositeName
import coredevices.ring.agent.pebble_plugin.FakePlugin
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.agent.pebble_plugin.PblPluginMcpIntegration
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LazyLoadingToolNamesTest {
    private val plugins = FakePlugins()
    private val context = SessionContext(timeBase = null, userMessageText = CompletableDeferred(null))

    private fun integration(name: String, action: String, uuid: String) = PblPluginMcpIntegration(
        FakePlugin(actions = listOf(ActionDeclaration(action)), name = name, pluginUuid = Uuid.parse(uuid)).metadata,
        plugins,
    )

    @Test
    fun loadingAGroupNamesItsToolsAsTheModelMustCallThem() = runTest {
        val session = LazyLoadingMcpSession(
            integrations = listOf(
                integration("Claude", "send_to_agent", "00000000-0000-0000-0002-000000000001"),
                integration("Plugin Demo: Dashboard", "clear_message", "00000000-0000-0000-0002-000000000002"),
            ),
            scope = this,
            eagerIntegrations = emptySet(),
            lazyLoadThreshold = 1,
        )
        session.listTools()

        val claude = session.callTool(
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME,
            LazyLoadingMcpSession.LOAD_TOOLS_NAME,
            mapOf("group" to JsonPrimitive("Claude")),
            context,
        )
        assertTrue(claude.resultString.endsWith("Claude__send_to_agent"), claude.resultString)

        val demo = session.callTool(
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME,
            LazyLoadingMcpSession.LOAD_TOOLS_NAME,
            mapOf("group" to JsonPrimitive("Plugin Demo: Dashboard")),
            context,
        )
        assertTrue(demo.resultString.endsWith("Plugin_Demo__Dashboard__clear_message"), demo.resultString)

        val loaded = session.listTools().filter { it.integrationName != LazyLoadingMcpSession.LOADER_INTEGRATION_NAME }
        assertEquals(
            listOf("Claude__send_to_agent", "Plugin_Demo__Dashboard__clear_message"),
            loaded.map { it.compositeName() },
        )
    }
}
