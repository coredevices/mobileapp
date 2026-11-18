package coredevices.coreapp.evals

import androidx.test.platform.app.InstrumentationRegistry
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.mcp.client.McpIntegration
import coredevices.mcp.client.McpSession
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.NenyaSystemOneAgent
import coredevices.ring.agent.RecordingIntent
import coredevices.ring.agent.RecordingIntentClassifier
import coredevices.ring.api.ApiConfig
import coredevices.ring.api.NenyaClientImpl
import coredevices.util.CommonBuildKonfig
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A transcript, the tools and data sources the assistant has, and the intent it should be classified as. */
data class IntentEvalCase(
    val transcript: String,
    val expected: RecordingIntent,
    val tools: List<McpIntegration> = DEFAULT_TOOLS,
    val sources: List<PluginMetadata> = emptyList(),
) {
    override fun toString() = "$expected, ${tools.size} tool groups, ${sources.size} source plugins: $transcript"
}

private class EvalTool(name: String, description: String) : BuiltInMcpTool(
    definition = Tool(name = name, description = description, inputSchema = ToolSchema())
) {
    override suspend fun call(jsonInput: String, context: SessionContext) = ToolCallResult("unused", null)
}

private fun integration(name: String, vararg tools: Pair<String, String>): McpIntegration =
    BuiltInMcpIntegration(name, tools.map { (tool, description) -> EvalTool(tool, description) })

private val DEFAULT_TOOLS = listOf(
    integration(
        "calendar",
        "list_events" to "Lists the user's calendar events in a date range",
        "create_event" to "Creates a calendar event",
    ),
    integration(
        "reminders",
        "create_reminder" to "Creates a reminder at a time or place",
        "list_reminders" to "Lists the user's reminders",
        "create_list_item" to "Adds an item to one of the user's lists",
        "list_list_items" to "Lists the items on one of the user's lists",
    ),
    integration(
        "notes",
        "create_note" to "Saves a note",
        "search_notes" to "Searches the user's saved notes",
    ),
    integration(
        "clock",
        "set_timer" to "Starts a timer",
        "set_alarm" to "Sets an alarm",
    ),
    integration("messages", "send_message" to "Sends a text message to a contact"),
)

private val WEATHER_TOOLS = DEFAULT_TOOLS + integration(
    "weather",
    "get_forecast" to "Gets the current weather and forecast for a location",
)

@OptIn(ExperimentalUuidApi::class)
private val WEATHER_SOURCE = PluginMetadata(
    pluginUuid = Uuid.random(),
    name = "Weather",
    description = "Current conditions and forecasts for the user's saved locations",
    sources = listOf(SourceDeclaration(category = "weather", items = listOf("location"))),
    actions = emptyList(),
)

val INTENT_EVAL_CASES = listOf(
    IntentEvalCase("Jared is a size 10 ring.", RecordingIntent.Note),
    IntentEvalCase("The gate code for the storage unit is 1234.", RecordingIntent.Note),
    IntentEvalCase("Idea: call a liquor brand La Curious.", RecordingIntent.Note),
    IntentEvalCase("Book recommendations from Sam: Dune and Project Hail Mary.", RecordingIntent.Note),
    IntentEvalCase("6 wide by 4 long", RecordingIntent.Note),

    IntentEvalCase("What's on my calendar tomorrow?", RecordingIntent.ToolQuestion),
    IntentEvalCase("When is my next meeting with Tommy?", RecordingIntent.ToolQuestion),
    IntentEvalCase("What reminders do I have today?", RecordingIntent.ToolQuestion),
    IntentEvalCase("What's on my shopping list?", RecordingIntent.ToolQuestion),
    IntentEvalCase("What did I write down about the gate code?", RecordingIntent.ToolQuestion),
    IntentEvalCase("What's the weather in New York right now?", RecordingIntent.ToolQuestion, WEATHER_TOOLS),
    IntentEvalCase(
        "What's the weather in New York right now?",
        RecordingIntent.ToolQuestion,
        sources = listOf(WEATHER_SOURCE),
    ),
    IntentEvalCase("Add shrimp and cornstarch to my shopping list.", RecordingIntent.ToolQuestion),
    IntentEvalCase("Remind me in thirty minutes to send out more ring invites.", RecordingIntent.ToolQuestion),
    IntentEvalCase("Set a timer for fifteen minutes.", RecordingIntent.ToolQuestion),
    IntentEvalCase("Set an alarm for 7:50 a.m.", RecordingIntent.ToolQuestion),
    IntentEvalCase("Text Eric that we need raw shrimp.", RecordingIntent.ToolQuestion),

    IntentEvalCase("What's the weather in New York right now?", RecordingIntent.SearchQuestion),
    IntentEvalCase("Who won the Warriors game last night?", RecordingIntent.SearchQuestion),
    IntentEvalCase("How tall is the Eiffel Tower?", RecordingIntent.SearchQuestion),
    IntentEvalCase("What's the price of Bitcoin?", RecordingIntent.SearchQuestion),
    IntentEvalCase("What's the latest news about the Mars mission?", RecordingIntent.SearchQuestion),
    IntentEvalCase("Who won the Warriors game last night?", RecordingIntent.SearchQuestion, emptyList()),

    IntentEvalCase("Uh, um, the, uh.", RecordingIntent.Other),
)

/**
 * Runs [RecordingIntentClassifier] against the real nenya decision endpoint, one test per case.
 *
 * Requires the app installed and signed in on the device, as its Firebase auth is shared. Uses
 * nenya staging unless the `nenyaUrl` instrumentation argument names another deployment:
 * `-Pandroid.testInstrumentationRunnerArguments.nenyaUrl=http://10.0.2.2:8000`
 *
 * To add a case, add an [IntentEvalCase] to [INTENT_EVAL_CASES].
 */
@RunWith(Parameterized::class)
class RecordingIntentClassifierEvalTest(private val case: IntentEvalCase) {
    private lateinit var classifier: RecordingIntentClassifier

    @Before
    fun setUp() {
        stopKoin()
        Assume.assumeNotNull("Sign in to the app on the device first", Firebase.auth.currentUser)
        startKoin {
            modules(module { factory<HttpClientEngine> { OkHttp.create() } })
        }
        val nenyaUrl = InstrumentationRegistry.getArguments().getString(NENYA_URL_ARG) ?: STAGING_NENYA_URL
        val nenyaClient = NenyaClientImpl(
            ApiConfig(
                nenyaUrl = nenyaUrl,
                notionOAuthBackendUrl = "",
                notionApiUrl = "",
                bugUrl = CommonBuildKonfig.BUG_URL,
                version = CommonBuildKonfig.USER_AGENT_VERSION,
                tokenUrl = CommonBuildKonfig.TOKEN_URL,
            )
        )
        classifier = RecordingIntentClassifier(NenyaSystemOneAgent(nenyaClient))
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun classifiesAsExpected() = runBlocking {
        val intent = classifier.classify(case.transcript, McpSession(case.tools, this), case.sources)

        assertEquals("\"${case.transcript}\"", case.expected, intent)
    }

    companion object {
        private const val NENYA_URL_ARG = "nenyaUrl"
        private const val STAGING_NENYA_URL = "https://nenya-staging-460977838956.us-west1.run.app"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<IntentEvalCase> = INTENT_EVAL_CASES
    }
}
