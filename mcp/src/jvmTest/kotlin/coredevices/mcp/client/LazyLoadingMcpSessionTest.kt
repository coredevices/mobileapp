package coredevices.mcp.client

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
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

    private fun List<McpSessionTool>.loader() = single { it.tool.definition.name == LazyLoadingMcpSession.LOAD_TOOLS_NAME }

    private suspend fun LazyLoadingMcpSession.search(vararg keywords: String) = callTool(
        LazyLoadingMcpSession.LOADER_INTEGRATION_NAME, LazyLoadingMcpSession.SEARCH_TOOLS_NAME,
        mapOf("keywords" to JsonArray(keywords.map { JsonPrimitive(it) })), sessionContext()
    )

    @Test
    fun smallToolSetsAreListedInFull() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("notes", 2), integration("remote", 5)), this, eagerIntegrations = setOf("notes"), lazyLoadThreshold = 8
        )
        val tools = session.listTools()
        assertEquals(7, tools.size)
        assertFalse(tools.any { it.integrationName == LazyLoadingMcpSession.LOADER_INTEGRATION_NAME })
        assertFalse(session.getExtraContext(null).orEmpty().contains(LazyLoadingMcpSession.LOAD_TOOLS_NAME))
    }

    @Test
    fun largeToolSetsListEagerToolsAndTheLoader() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("notes", 2), integration("crm", 5), integration("wiki", 4)), this, eagerIntegrations = setOf("notes"), lazyLoadThreshold = 11
        )
        val tools = session.listTools()
        assertEquals(listOf("notes__tool_0", "notes__tool_1", "tool_loader__load_tools", "tool_loader__search_tools"), tools.names())

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
            listOf(integration("notes", 2), integration("crm", 5), integration("wiki", 4)), this, eagerIntegrations = setOf("notes"), lazyLoadThreshold = 11
        )
        session.listTools()
        val result = session.callTool(
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME, LazyLoadingMcpSession.LOAD_TOOLS_NAME,
            mapOf("group" to JsonPrimitive("wiki")), sessionContext()
        )
        val supporting = assertIs<SemanticResult.SupportingData>(result.semanticResult)
        assertContains(result.resultString, "Loaded 4 tools from 'wiki'")

        val tools = session.listTools()
        assertEquals(
            listOf(
                "notes__tool_0", "notes__tool_1", "wiki__tool_0", "wiki__tool_1", "wiki__tool_2", "wiki__tool_3",
                "tool_loader__load_tools", "tool_loader__search_tools"
            ),
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
            listOf(integration("notes", 2), crm), this, eagerIntegrations = setOf("notes"), lazyLoadThreshold = 4
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
            listOf(integration("crm", 5)), this, eagerIntegrations = emptySet(), lazyLoadThreshold = 2
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
            listOf(integration("crm", 5)), this, eagerIntegrations = emptySet(), lazyLoadThreshold = 2
        )
        session.listTools()
        assertEquals("called tool_0", session.callTool("crm", "tool_0", emptyMap(), sessionContext()).resultString)
    }

    @Test
    fun describedGroupsLeadWithTheDescriptionAndSampleToolNamesOnly() = runBlocking {
        val todoist = object : BuiltInMcpIntegration(
            "todoist", listOf(FakeTool("create_reminder", "Create a Todoist task."))
        ) {
            override val description = "Creates Todoist tasks as reminders. "
        }
        val session = LazyLoadingMcpSession(
            listOf(integration("notes", 2), todoist, integration("crm", 5)), this, eagerIntegrations = setOf("notes"), lazyLoadThreshold = 5
        )

        val loader = session.listTools().loader().tool.definition
        assertContains(loader.description!!, "- todoist (1 tools): Creates Todoist tasks as reminders. Tools: create_reminder\n")
        assertFalse(loader.description!!.contains("Create a Todoist task."))
        assertContains(loader.description!!, "- crm (5 tools): tool_0 (Does thing 0)")
    }

    @Test
    fun longGroupDescriptionsAreShortened() = runBlocking {
        val wordy = object : BuiltInMcpIntegration("wordy", listOf(FakeTool("do_it", "Does it"))) {
            override val description = "x".repeat(300)
        }
        val session = LazyLoadingMcpSession(listOf(wordy, integration("crm", 5)), this, eagerIntegrations = emptySet(), lazyLoadThreshold = 2)
        val loader = session.listTools().loader().tool.definition
        assertContains(loader.description!!, "- wordy (1 tools): " + "x".repeat(199) + "…. Tools: do_it")
    }

    @Test
    fun searchNamesTheGroupToLoadForMatchingTools() = runBlocking {
        val home = integration(
            "mcphub",
            "announce" to "Play an announcement on the speakers",
            "kidsnet_status" to "Current KidsNet mode for each child's devices",
            "kidsnet_set_mode" to "Set KidsNet mode for a child",
            "run_routine" to "Run a named home routine",
        )
        val session = LazyLoadingMcpSession(
            listOf(integration("notes", 2), home, integration("crm", 5)), this, eagerIntegrations = setOf("notes"), lazyLoadThreshold = 5
        )
        session.listTools()
        assertTrue(session.listTools().names().contains("tool_loader__search_tools"))

        val result = session.search("kidsnet", "status")
        assertIs<SemanticResult.SupportingData>(result.semanticResult)
        val lines = result.resultString.lines()
        assertEquals("Matching tools by group:", lines[0])
        assertTrue(lines[1].startsWith("- mcphub (load with load_tools group='mcphub'): kidsnet_status (Current KidsNet mode"), lines[1])
        assertContains(lines[1], "kidsnet_set_mode")
        assertFalse(result.resultString.contains("crm"))

        session.callTool(
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME, LazyLoadingMcpSession.LOAD_TOOLS_NAME,
            mapOf("group" to JsonPrimitive("mcphub")), sessionContext()
        )
        assertContains(session.search("routine").resultString, "- mcphub (already loaded): run_routine")
    }

    @Test
    fun searchToleratesTyposAndCommaSeparatedKeywords() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("lights", "switchDevices" to "Turn devices on or off"), integration("crm", 5)),
            this, eagerIntegrations = emptySet(), lazyLoadThreshold = 2
        )
        session.listTools()
        assertContains(session.search("swich", "devises").resultString, "- lights (load with load_tools group='lights'): switchDevices")

        val commaSeparated = session.callTool(
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME, LazyLoadingMcpSession.SEARCH_TOOLS_NAME,
            mapOf("keywords" to JsonPrimitive("turn, off")), sessionContext()
        )
        assertContains(commaSeparated.resultString, "switchDevices")
    }

    @Test
    fun searchWithoutMatchesOrKeywordsIsRecoverable() = runBlocking {
        val session = LazyLoadingMcpSession(
            listOf(integration("crm", 5), integration("wiki", 2)), this, eagerIntegrations = emptySet(), lazyLoadThreshold = 2
        )
        session.listTools()
        val none = session.search("zebra")
        assertIs<SemanticResult.SupportingData>(none.semanticResult)
        assertEquals("No tools match zebra. Available groups: crm, wiki", none.resultString)

        val empty = session.search()
        assertTrue(assertIs<SemanticResult.GenericFailure>(empty.semanticResult).llmRecoverable)
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
