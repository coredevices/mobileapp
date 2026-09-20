package coredevices.mcp.client

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LazyLoadingMcpSessionTest {

    private class FakeTool(name: String, description: String?) : BuiltInMcpTool(
        definition = Tool(name = name, description = description, inputSchema = ToolSchema())
    ) {
        override suspend fun call(jsonInput: String, context: SessionContext) = ToolCallResult("called ${definition.name}", null)
    }

    private fun integration(name: String, vararg tools: Pair<String, String?>) =
        BuiltInMcpIntegration(name, tools.map { (tool, description) -> FakeTool(tool, description) })

    private fun integration(name: String, toolCount: Int) =
        integration(name, *Array(toolCount) { "tool_$it" to "Does thing $it" })

    private class ToggleableIntegration(name: String, toolCount: Int) : BuiltInMcpIntegration(
        name, List(toolCount) { FakeTool("tool_$it", "Does thing $it") }
    ) {
        var disabled = emptySet<String>()
        override suspend fun getDisabledTools() = disabled.toList()
    }

    private fun sessionContext() = SessionContext(timeBase = null, userMessageText = CompletableDeferred("test"))

    private fun List<McpSessionTool>.names() = map { "${it.integrationName}__${it.tool.definition.name}" }

    private fun List<McpSessionTool>.loader() = single { it.integrationName == LazyLoadingMcpSession.LOADER_INTEGRATION_NAME }

    @Test
    fun smallToolSetsAreListedInFull() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("notes", 2), integration("remote", 5)), this, eagerIntegrations = setOf("notes"), maxEagerTools = 7
        )
        val tools = session.listTools()
        assertEquals(7, tools.size)
        assertFalse(tools.any { it.integrationName == LazyLoadingMcpSession.LOADER_INTEGRATION_NAME })
        assertFalse(session.getExtraContext(null).orEmpty().contains(LazyLoadingMcpSession.LOAD_TOOLS_NAME))
    }

    @Test
    fun largeToolSetsListEagerToolsAndTheLoader() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("notes", 2), integration("crm", 5), integration("wiki", 4)), this, eagerIntegrations = setOf("notes"), maxEagerTools = 10
        )
        val tools = session.listTools()
        assertEquals(listOf("notes__tool_0", "notes__tool_1", "tool_loader__load_tools"), tools.names())

        val loader = tools.loader().tool.definition
        assertContains(loader.description!!, "- crm (5 tools): tool_0 (Does thing 0), tool_1 (Does thing 1), tool_2 (Does thing 2) and 2 more")
        assertContains(loader.description!!, "- wiki (4 tools): tool_0 (Does thing 0), tool_1 (Does thing 1), tool_2 (Does thing 2) and 1 more")
        val groupEnum = loader.inputSchema.properties!!["group"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("crm", "wiki"), groupEnum)
        assertContains(session.getExtraContext(null)!!, "load_tools")
    }

    @Test
    fun loadingAGroupExposesItsToolsForTheRestOfTheSession() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("notes", 2), integration("crm", 5), integration("wiki", 4)), this, eagerIntegrations = setOf("notes"), maxEagerTools = 10
        )
        session.listTools()
        val result = session.callTool(
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME, LazyLoadingMcpSession.LOAD_TOOLS_NAME,
            mapOf("group" to JsonPrimitive("wiki")), sessionContext()
        )
        val supporting = assertIs<SemanticResult.SupportingData>(result.semanticResult)
        assertTrue(supporting.assistiveOnly)
        assertContains(result.resultString, "Loaded 4 tools from 'wiki'")

        val tools = session.listTools()
        assertEquals(
            listOf("notes__tool_0", "notes__tool_1", "wiki__tool_0", "wiki__tool_1", "wiki__tool_2", "wiki__tool_3", "tool_loader__load_tools"),
            tools.names()
        )
        assertEquals("called tool_3", session.callTool("wiki", "tool_3", emptyMap(), sessionContext()).resultString)

        session.closeSession()
        session.openSession()
        assertTrue(session.listTools().names().contains("wiki__tool_0"))
    }

    @Test
    fun loaderIsReusedUntilTheDeferrableToolsChange() = runBlocking {
        val crm = ToggleableIntegration("crm", 5)
        val session = LazyLoadingMcpSession(
            listOf(integration("notes", 2), crm), this, eagerIntegrations = setOf("notes"), maxEagerTools = 3
        )
        val first = session.listTools().loader().tool
        assertSame(first, session.listTools().loader().tool)

        crm.disabled = setOf("tool_4")
        val rebuilt = session.listTools().loader().tool
        assertNotSame(first, rebuilt)
        assertContains(rebuilt.definition.description!!, "- crm (4 tools)")
    }

    @Test
    fun unknownGroupIsARecoverableFailure() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("crm", 5)), this, eagerIntegrations = emptySet(), maxEagerTools = 1
        )
        session.listTools()
        val result = session.callTool(
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME, LazyLoadingMcpSession.LOAD_TOOLS_NAME,
            mapOf("group" to JsonPrimitive("nope")), sessionContext()
        )
        val failure = assertIs<SemanticResult.GenericFailure>(result.semanticResult)
        assertTrue(failure.llmRecoverable)
        assertContains(result.resultString, "Available groups: crm")
    }

    @Test
    fun unlistedToolsAreStillServedWhenCalledDirectly() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("crm", 5)), this, eagerIntegrations = emptySet(), maxEagerTools = 1
        )
        session.listTools()
        assertEquals("called tool_0", session.callTool("crm", "tool_0", emptyMap(), sessionContext()).resultString)
    }

    @Test
    fun samplesSpanNamespacesAndTrimDescriptions() {
        val long = "A description that is definitely longer than fifty characters in total"
        val catalog = ToolCatalog(
            listOf(
                "issues__create" to long,
                "issues__list" to "List issues",
                "issues__close" to "Close",
                "pulls__merge" to null,
                "pulls__list" to "List pulls",
                "repos__get" to "  Get a repo  ",
            ).map { (name, description) -> McpSessionTool("github", FakeTool(name, description)) }
        )
        assertEquals(
            "- github (6 tools): issues__create (A description that is definitely longer than fift…), pulls__merge, repos__get (Get a repo) and 3 more",
            catalog.describe()
        )
    }
}
