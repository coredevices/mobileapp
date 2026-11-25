package coredevices.ring.agent.builtin_servlets.bugreport

import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreateBugReportToolTest {

    private val context = SessionContext(
        timeBase = null,
        userMessageText = CompletableDeferred("create a bug report for watch it froze"),
    )

    @Test
    fun sendsTheDescriptionForTheNamedDevice() = runTest {
        val reports = mutableListOf<Pair<String, Boolean>>()
        val tool = CreateBugReportTool { description, watch ->
            reports += description to watch
            Result.success(Unit)
        }

        val watch = tool.call("""{"device": "watch", "description": " it froze "}""", context)
        tool.call("""{"device": "index", "description": "the light stayed off"}""", context)

        assertEquals(listOf("it froze" to true, "the light stayed off" to false), reports)
        assertEquals("Watch bug report sent", assertIs<SemanticResult.ActionLogged>(watch.semanticResult).title)
    }

    @Test
    fun reportsFailures() = runTest {
        val tool = CreateBugReportTool { _, _ -> Result.failure(Exception("Please sign in")) }

        val result = tool.call("""{"device": "index", "description": "x"}""", context)

        assertEquals("Please sign in", assertIs<SemanticResult.GenericFailure>(result.semanticResult).userErrorMessage)
    }
}
