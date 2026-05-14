package coredevices.coreapp.evals

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.util.CommonBuildKonfig as CBK
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.AgentNenya
import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.api.ApiConfig
import coredevices.ring.api.NenyaClient
import coredevices.ring.api.NenyaClientImpl
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.database.room.dao.RecordingProcessingTaskDao
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.RecordingProcessingTaskRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.database.room.repository.RingTransferRepository
import coredevices.ring.encryption.DocumentEncryptor
import coredevices.ring.encryption.EncryptionKeyManager
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.recordings.RecordingPreprocessor
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.service.recordings.button.RecordingOperationFactory
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession
import coredevices.util.Platform
import coredevices.util.models.CactusSTTMode
import coredevices.util.queue.TaskStatus
import coredevices.util.transcription.CactusModelPathProvider
import coredevices.util.transcription.CactusTranscriptionService
import coredevices.util.transcription.NoOpInferenceBoost
import coredevices.util.transcription.TranscriptionService
import coredevices.util.transcription.WisprFlowTranscriptionService
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import coredevices.util.STTConfig
import coredevices.api.WisprFlowAuth
import coredevices.mcp.data.SemanticResult
import coredevices.ring.model.CactusModelProvider
import com.russhwolf.settings.SharedPreferencesSettings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import kotlin.time.Duration
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

// ---- Transcription Normalization ----

/**
 * Normalizes transcription text so that formatting differences between STT providers
 * (number words vs digits, punctuation, case) don't cause false failures.
 * Real transcription errors (wrong words) still get caught.
 */
private fun normalizeTranscription(text: String): String {
    var s = text.lowercase().trim()
    // Number words → digits
    val numberWords = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
        "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
        "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "thirty" to "30",
        "forty" to "40", "fifty" to "50",
    )
    for ((word, digit) in numberWords) {
        s = s.replace("\\b$word\\b".toRegex(), digit)
    }
    // Time normalization: "a.m." / "a.m" / "am" → "am", same for pm
    s = s.replace("a\\.m\\.?".toRegex(), "am")
    s = s.replace("p\\.m\\.?".toRegex(), "pm")
    // Strip colons in time-like patterns (7:50 → 7 50)
    s = s.replace("(\\d):(\\d)".toRegex(), "$1 $2")
    // Collapse space between number and am/pm (11 am → 11am)
    s = s.replace("(\\d) ?(am|pm)\\b".toRegex(), "$1$2")
    // Remove quotes and parentheses
    s = s.replace("\"", "").replace("'", "").replace("(", "").replace(")", "")
    // Normalize "period" → remove (it's a dictation artifact)
    s = s.replace("\\bperiod\\b".toRegex(), "")
    // Remove "note to self" / "note itself" prefix variations → "note"
    s = s.replace("\\bnote (to self|itself)\\b".toRegex(), "note")
    // Remove filler words: um, uh, like, yeah, and, also
    s = s.replace("\\b(um|uh|like|yeah)\\b".toRegex(), "")
    // Remove leading "let's" / "lets" (STT sometimes inserts it)
    s = s.replace("^lets? ".toRegex(), "")
    // Normalize comma vs period between sentences (both → space)
    s = s.replace("[,.]\\s+".toRegex(), " ")
    // Remove trailing punctuation
    s = s.trimEnd('.', ',', '?', '!', ':')
    // Collapse whitespace
    s = s.replace("\\s+".toRegex(), " ").trim()
    return s
}

// ---- Eval Case Definition ----

enum class SttMode { Local, Remote }

/**
 * Defines an end-to-end eval case: a real audio recording with expected outcomes
 * at each stage of the pipeline.
 */
data class EvalCase(
    /** Human-readable name for the test */
    val name: String,
    /** Asset filename (raw PCM 16kHz 16-bit mono) in androidMain/assets/ */
    val assetFile: String,
    /** Ground truth transcription (what was actually said) */
    val groundTruth: String,
    /** Expected Cactus (local) transcription output — verified correct */
    val expectedTranscriptionLocal: String,
    /** Expected Wispr Flow (remote) transcription output — verified correct */
    val expectedTranscriptionRemote: String,
    /** Expected MCP tool name (e.g. "set_timer", "create_note") */
    val expectedToolName: String,
    /** Expected tool arguments as key-value pairs */
    val expectedToolArgs: Map<String, String>,
    /** Validates the system effect via SemanticResult on the tool response message */
    val verifyEffect: (SemanticResult) -> Unit,
)

val EVAL_CASES = listOf(
    EvalCase(
        name = "set_timer_15min",
        assetFile = "eval_set_timer_15min.raw",
        groundTruth = "Set a timer for fifteen minutes.",
        expectedTranscriptionLocal = "Set a timer for fifteen minutes",
        expectedTranscriptionRemote = "Let's set a timer for 15 minutes.",
        expectedToolName = "builtin_clock.set_timer",
        expectedToolArgs = mapOf("time_human" to "15 minutes"),
        verifyEffect = { result ->
            assertTrue("Expected TimerCreation, got: $result", result is SemanticResult.TimerCreation)
            val timer = result as SemanticResult.TimerCreation
            assertNotNull("Timer should have a duration", timer.requestedDuration)
            assertEquals("Timer duration should be 15 minutes", 15.minutes, timer.requestedDuration)
        }
    ),
    EvalCase(
        name = "set_alarm_750am",
        assetFile = "eval_set_alarm_750am.raw",
        groundTruth = "Set an alarm for 7:50 a.m.",
        expectedTranscriptionLocal = "Set an alarm for seven fifty AM.",
        expectedTranscriptionRemote = "Set an alarm for 7:50 a.m.",
        expectedToolName = "builtin_clock.set_alarm",
        expectedToolArgs = mapOf("time_hours" to "7", "time_minutes" to "50"),
        verifyEffect = { result ->
            assertTrue("Expected AlarmCreation, got: $result", result is SemanticResult.AlarmCreation)
            val alarm = result as SemanticResult.AlarmCreation
            assertEquals("Alarm time", LocalTime(7, 50), alarm.fireTime)
        }
    ),
    EvalCase(
        name = "text_eric_shrimp",
        assetFile = "eval_text_eric_shrimp.raw",
        groundTruth = "Text Eric, the kids have breaded shrimp. We need raw shrimp. Maybe the Argentinian one would be good.",
        expectedTranscriptionLocal = "Text Eric. The kids have breaded shrimp. Period. We need raw shrimp. Maybe the Argentinian one will be good.",
        expectedTranscriptionRemote = "Text Eric, the kids have breaded shrimp. We need raw shrimp. Maybe the Argentinian one would be good.",
        // Messaging tools not available on test emulator (no Beeper), so agent falls back to note
        expectedToolName = "builtin_note.create_note",
        expectedToolArgs = mapOf(),
        verifyEffect = { result ->
            assertTrue("Expected ListItemCreation, got: $result", result is SemanticResult.ListItemCreation)
        }
    ),
    EvalCase(
        name = "shopping_list_shrimp",
        assetFile = "eval_shopping_list_shrimp.raw",
        groundTruth = "Add shrimp and cornstarch to my shopping list.",
        expectedTranscriptionLocal = "Add print and cornstarch to my shopping list.",
        expectedTranscriptionRemote = "Add shrimp and cornstarch to my shopping list.",
        expectedToolName = "builtin_reminder.create_list_item",
        expectedToolArgs = mapOf("list_name" to "shopping"),
        verifyEffect = { result ->
            assertTrue("Expected ListItemCreation, got: $result", result is SemanticResult.ListItemCreation)
        }
    ),
    EvalCase(
        name = "reminder_11am_tomorrow",
        assetFile = "eval_reminder_11am_tomorrow.raw",
        groundTruth = "Remind me at 11am tomorrow to talk to Tommy about how Julian's going to Spanish school.",
        expectedTranscriptionLocal = "Remind me at eleven AM tomorrow to talk to Tommy about how Julian's going to Spanish school.",
        expectedTranscriptionRemote = "Remind me at 11 a.m. tomorrow to talk to Tommy about how Julian's going to Spanish school.",
        expectedToolName = "builtin_reminder.create_reminder",
        expectedToolArgs = mapOf(), // Args vary by transcription wording — LLM normalizes differently
        verifyEffect = { result ->
            assertTrue("Expected TaskCreation or ListItemCreation, got: $result",
                result is SemanticResult.TaskCreation || result is SemanticResult.ListItemCreation)
        }
    ),
    EvalCase(
        name = "reminder_30min",
        assetFile = "eval_reminder_30min.raw",
        groundTruth = "Remind me in thirty minutes to send out more ring invites.",
        expectedTranscriptionLocal = "Remind me in thirty minutes to send out more ring invites.",
        expectedTranscriptionRemote = "Remind me in 30 minutes to send out more ring invites.",
        expectedToolName = "builtin_reminder.create_reminder",
        expectedToolArgs = mapOf(
            "message" to "send out more ring invites"
        ),
        verifyEffect = { result ->
            assertTrue("Expected TaskCreation or ListItemCreation, got: $result",
                result is SemanticResult.TaskCreation || result is SemanticResult.ListItemCreation)
        }
    ),
    EvalCase(
        name = "note_jared_size10",
        assetFile = "eval_note_jared_size10.raw",
        groundTruth = "Jared is a size 10 ring.",
        expectedTranscriptionLocal = "Jared is a size ten ring.",
        expectedTranscriptionRemote = "Jared is a size 10 ring.",
        expectedToolName = "builtin_note.create_note",
        expectedToolArgs = mapOf(), // Text arg varies by STT provider ("ten" vs "10")
        verifyEffect = { result ->
            assertTrue("Expected ListItemCreation, got: $result", result is SemanticResult.ListItemCreation)
        }
    ),
    EvalCase(
        name = "note_long_half_sizes",
        assetFile = "eval_note_long_half_sizes.raw",
        groundTruth = "Note to self: half sizes on the rings, okay? Stretch your finger, and yeah. Make sure that there's somebody there, maybe a human being, to stretch out the rings. Also, maybe wine is not a good idea. Just try it. It's fine.",
        expectedTranscriptionLocal = "Note itself. Um sizes on the ring. Okay. Stretch the finger and um yeah. Uh make sure that there's somebody there to maybe a human being to dress out the rings and the the people. Ultimately wine is not a good idea. Just try it. Like just",
        expectedTranscriptionRemote = "Note to self: Half sizes on the rings, okay? Stretch the finger, and make sure that there's somebody there (maybe a human being) to stretch out the rings and the keyboards. Ultimately, wine is not a good idea. Just try it.",
        expectedToolName = "builtin_note.create_note",
        expectedToolArgs = mapOf(),
        verifyEffect = { result ->
            assertTrue("Expected ListItemCreation, got: $result", result is SemanticResult.ListItemCreation)
        }
    ),
    EvalCase(
        name = "note_danny_lacurious",
        assetFile = "eval_note_danny_lacurious.raw",
        groundTruth = "Remember to tell Danny about the idea to call a liquor brand La Curious.",
        expectedTranscriptionLocal = "Remember to tell Danny about the idea to call a liquor brand Lucurious?",
        expectedTranscriptionRemote = "Remember to tell Danny about the idea to call a liquor brand \"La Curious.\"",
        expectedToolName = "builtin_note.create_note",
        expectedToolArgs = mapOf(),
        verifyEffect = { result ->
            assertTrue("Expected ListItemCreation, got: $result", result is SemanticResult.ListItemCreation)
        }
    ),
)

// ---- Test Infrastructure ----

/**
 * End-to-end test that feeds real Ring recordings through the full production pipeline:
 * raw PCM audio → Cactus transcription → Nenya agent → MCP tool execution.
 *
 * Runs in the composeApp module so Firebase auto-initializes and shares auth state
 * with the installed app. Requirements:
 * - App installed and signed in on the emulator
 * - Cactus STT model (parakeet-tdt-0.6b-v3) downloaded
 *
 * To add a new eval case:
 * 1. Add the .raw audio file to composeApp/src/androidMain/assets/
 * 2. Add an EvalCase entry to EVAL_CASES above
 * 3. Add a @Test method that calls runEval("your_case_name")
 */
class RingRecordingE2ETest {
    private lateinit var context: Context
    private lateinit var db: RingDatabase
    private lateinit var taskDao: RecordingProcessingTaskDao
    private lateinit var entryDao: RecordingEntryDao
    private lateinit var messageDao: ConversationMessageDao
    private lateinit var queue: RecordingProcessingQueue
    private lateinit var bgScopeJob: CompletableJob

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        stopKoin()

        val currentUser = Firebase.auth.currentUser
        Assume.assumeNotNull("Sign in to the app on the emulator first", currentUser)

        bgScopeJob = SupervisorJob()

        startKoin {
            androidContext(context)
            modules(createE2EModule())
        }

        val koin = GlobalContext.get()
        db = koin.get()
        taskDao = db.recordingProcessingTaskDao()
        entryDao = koin.get()
        messageDao = koin.get()
        queue = koin.get()

        runBlocking {
            koin.get<McpSandboxRepository>().seedDatabase()
        }
    }

    @After
    fun tearDown() {
        if (::queue.isInitialized) queue.close()
        // Close trace session before DB to flush pending async writes
        try {
            val koin = GlobalContext.getOrNull()
            if (koin != null) {
                runBlocking { koin.get<RingTraceSession>().close() }
            }
        } catch (_: Exception) {}
        if (::bgScopeJob.isInitialized) bgScopeJob.cancel()
        if (::db.isInitialized) db.close()
        stopKoin()
        if (::context.isInitialized) File(context.cacheDir, "recordings").deleteRecursively()
    }

    // ---- Eval Runner ----

    private fun runEval(caseName: String, mode: SttMode = SttMode.Local) = runBlocking {
        val eval = EVAL_CASES.first { it.name == caseName }

        // Update STT config for the requested mode
        val configFlow = GlobalContext.get().get<CoreConfigFlow>()
        (configFlow.flow as MutableStateFlow).value = when (mode) {
            SttMode.Local -> CoreConfig(sttConfig = STTConfig(
                mode = CactusSTTMode.LocalOnly,
                modelName = "parakeet-tdt-0.6b-v3"
            ))
            SttMode.Remote -> CoreConfig(sttConfig = STTConfig(
                mode = CactusSTTMode.RemoteOnly,
                modelName = null
            ))
        }

        val fileId = "e2e-${eval.name}-${mode.name.lowercase()}-${System.currentTimeMillis()}"
        loadAudioAsset(eval.assetFile, fileId)

        queue.queueLocalAudioProcessing(fileId)
        // Task ID is 1 because we use a fresh in-memory DB per test run
        val taskId = 1L
        val recordingId = 1L
        awaitTaskDone(taskId = taskId)

        val task = taskDao.getTaskById(taskId)!!
        val entries = entryDao.getEntriesForRecording(recordingId).first()
        val messages = messageDao.getMessagesForRecording(recordingId).first()

        // Diagnostic info for failures
        val entryInfo = entries.firstOrNull()?.let {
            "status=${it.status}, transcription='${it.transcription}', error='${it.error}'"
        } ?: "no entries"
        val msgInfo = messages.joinToString("; ") { "${it.document.role}: ${it.document.content?.take(80)}" }

        // 1. Task succeeded
        assertEquals(
            "[$mode] Task should succeed. Entry: [$entryInfo]. Messages: [$msgInfo]. Attempts: ${task.attempts}",
            TaskStatus.Success, task.status
        )

        // 2. Exact transcription match (per STT mode)
        val expectedTranscription = when (mode) {
            SttMode.Local -> eval.expectedTranscriptionLocal
            SttMode.Remote -> eval.expectedTranscriptionRemote
        }
        assertEquals("[$mode] Expected 1 recording entry", 1, entries.size)
        assertEquals("[$mode] Entry should be completed", RecordingEntryStatus.completed, entries[0].status)
        assertEquals("[$mode] Transcription mismatch", expectedTranscription, entries[0].transcription)

        // 3. Exact tool call match
        val toolCalls = messages
            .filter { it.document.role == MessageRole.assistant }
            .flatMap { it.document.tool_calls ?: emptyList() }
        assertTrue("Expected at least one tool call, got none. Messages: [$msgInfo]", toolCalls.isNotEmpty())

        val matchingCall = toolCalls.firstOrNull { it.function?.name == eval.expectedToolName }
        assertNotNull(
            "Expected tool '${eval.expectedToolName}', got: ${toolCalls.map { it.function?.name }}",
            matchingCall
        )

        // 4. Exact tool arguments match
        val actualArgs = json.decodeFromString<JsonObject>(matchingCall!!.function!!.arguments)
        for ((key, expectedValue) in eval.expectedToolArgs) {
            val actualValue = actualArgs[key]?.jsonPrimitive?.content
            assertEquals("Tool arg '$key' mismatch", expectedValue, actualValue)
        }

        // 5. System effect verification via SemanticResult
        // Match tool response by tool_call_id to the correct tool call
        val toolResponse = messages
            .filter { it.document.role == MessageRole.tool }
            .firstOrNull { it.document.tool_call_id == matchingCall.id }
            ?: messages.firstOrNull { it.document.role == MessageRole.tool } // fallback to first
        assertNotNull("Expected tool response message for call ${matchingCall.id}", toolResponse)
        val semanticResult = toolResponse!!.document.semantic_result
        assertNotNull("Expected semantic result on tool response", semanticResult)
        eval.verifyEffect(semanticResult!!)
    }

    // ---- Test Cases ----
    // Each test method maps to an EvalCase by name.
    // To add a new eval: add an EvalCase to EVAL_CASES, drop the .raw file in assets, add a @Test method.

    // Local (Cactus) evals
    @Test fun eval_set_timer_15min_local() = runEval("set_timer_15min", SttMode.Local)
    @Test fun eval_set_alarm_750am_local() = runEval("set_alarm_750am", SttMode.Local)
    @Test fun eval_text_eric_shrimp_local() = runEval("text_eric_shrimp", SttMode.Local)
    @Test fun eval_shopping_list_shrimp_local() = runEval("shopping_list_shrimp", SttMode.Local)
    @Test fun eval_reminder_11am_tomorrow_local() = runEval("reminder_11am_tomorrow", SttMode.Local)
    @Test fun eval_reminder_30min_local() = runEval("reminder_30min", SttMode.Local)
    @Test fun eval_note_jared_size10_local() = runEval("note_jared_size10", SttMode.Local)
    @Test fun eval_note_long_half_sizes_local() = runEval("note_long_half_sizes", SttMode.Local)
    @Test fun eval_note_danny_lacurious_local() = runEval("note_danny_lacurious", SttMode.Local)

    // Remote (Wispr Flow) evals
    @Test fun eval_set_timer_15min_remote() = runEval("set_timer_15min", SttMode.Remote)
    @Test fun eval_set_alarm_750am_remote() = runEval("set_alarm_750am", SttMode.Remote)
    @Test fun eval_text_eric_shrimp_remote() = runEval("text_eric_shrimp", SttMode.Remote)
    @Test fun eval_shopping_list_shrimp_remote() = runEval("shopping_list_shrimp", SttMode.Remote)
    @Test fun eval_reminder_11am_tomorrow_remote() = runEval("reminder_11am_tomorrow", SttMode.Remote)
    @Test fun eval_reminder_30min_remote() = runEval("reminder_30min", SttMode.Remote)
    @Test fun eval_note_jared_size10_remote() = runEval("note_jared_size10", SttMode.Remote)
    @Test fun eval_note_long_half_sizes_remote() = runEval("note_long_half_sizes", SttMode.Remote)
    @Test fun eval_note_danny_lacurious_remote() = runEval("note_danny_lacurious", SttMode.Remote)

    // ---- Helpers ----

    private fun loadAudioAsset(assetName: String, fileId: String) {
        val cacheDir = File(context.cacheDir, "recordings")
        cacheDir.mkdirs()
        val audioBytes = context.assets.open(assetName).use { it.readBytes() }
        File(cacheDir, fileId).writeBytes(audioBytes)
        runBlocking {
            db.cachedRecordingMetadataDao().insert(
                CachedRecordingMetadata(id = fileId, sampleRate = 16000, mimeType = "audio/pcm")
            )
        }
    }

    private suspend fun awaitTaskDone(taskId: Long, timeout: Duration = 60.seconds) {
        withTimeout(timeout) {
            taskDao.getTaskByIdFlow(taskId).first { it != null && it.status != TaskStatus.Pending }
        }
    }

    // ---- Koin Module ----

    private fun createE2EModule() = module {
        // Database (in-memory)
        single {
            Room.inMemoryDatabaseBuilder<RingDatabase>(context = context.applicationContext)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }

        // DAOs
        single { get<RingDatabase>().localReminderDao() }
        single { get<RingDatabase>().cachedRecordingMetadataDao() }
        single { get<RingDatabase>().ringDebugTransferDao() }
        single { get<RingDatabase>().localRecordingDao() }
        single { get<RingDatabase>().recordingEntryDao() }
        single { get<RingDatabase>().conversationMessageDao() }
        single { get<RingDatabase>().ringTransferDao() }
        single { get<RingDatabase>().builtinMcpGroupAssociationDao() }
        single { get<RingDatabase>().httpMcpGroupAssociationDao() }
        single { get<RingDatabase>().httpMcpServerDao() }
        single { get<RingDatabase>().mcpSandboxGroupDao() }
        single { get<RingDatabase>().recordingProcessingTaskDao() }
        single { get<RingDatabase>().traceSessionDao() }
        single { get<RingDatabase>().traceEntryDao() }

        // Repositories
        singleOf(::RecordingProcessingTaskRepository)
        singleOf(::RecordingRepository)
        singleOf(::RingTransferRepository)
        singleOf(::McpSandboxRepository)

        // HTTP engine
        factory<HttpClientEngine> { params ->
            OkHttp.create {
                config {
                    readTimeout(params.getOrNull<Duration>()?.toJavaDuration() ?: java.time.Duration.ofSeconds(30))
                }
            }
        }

        // API config
        single {
            ApiConfig(
                nenyaUrl = "https://nenya-staging-460977838956.us-west1.run.app",
                notionOAuthBackendUrl = "",
                notionApiUrl = "",
                bugUrl = CBK.BUG_URL,
                version = CBK.USER_AGENT_VERSION,
                tokenUrl = CBK.TOKEN_URL,
            )
        }

        // Real API clients
        singleOf(::NenyaClientImpl) bind NenyaClient::class
        singleOf(::WisprFlowAuth)
        singleOf(::WisprFlowTranscriptionService)

        // Cactus local transcription
        single { CactusModelProvider() }
        single<CactusModelPathProvider> { get<CactusModelProvider>() }
        // STT config — starts as LocalOnly, runEval() updates the flow for Remote mode
        single {
            CoreConfigFlow(MutableStateFlow(
                CoreConfig(sttConfig = STTConfig(
                    mode = CactusSTTMode.LocalOnly,
                    modelName = "parakeet-tdt-0.6b-v3"
                ))
            ))
        }
        single {
            CactusTranscriptionService(get(), get(), get<CactusModelPathProvider>(), NoOpInferenceBoost())
        } bind TranscriptionService::class

        // MCP tools
        singleOf(::BuiltinServletRepository) bind ServletRepository::class
        singleOf(::McpSessionFactory)
        factory { coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool(get()) }
        factory { coredevices.ring.agent.integrations.NotionIntegration(get(), get(), get()) }
        factory { coredevices.ring.agent.builtin_servlets.notes.LocalNoteClient() }
        single { coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory(get(), get()) }
        single<coredevices.firestore.UsersDao> {
            object : coredevices.firestore.UsersDao {
                override val user = kotlinx.coroutines.flow.flowOf<coredevices.firestore.PebbleUser?>(null)
                override suspend fun updateNotionToken(notionToken: String?) {}
                override suspend fun updateMcpRunToken(mcpRunToken: String?) {}
                override suspend fun updateTodoBlockId(todoBlockId: String) {}
                override suspend fun initUserDevToken(rebbleUserToken: String?) {}
                override suspend fun updateLastConnectedWatch(serial: String) {}
                override fun init() {}
            }
        }
        single { coredevices.ring.api.NotionApi(get()) }
        single<coredevices.util.integrations.IntegrationTokenStorage> {
            object : coredevices.util.integrations.IntegrationTokenStorage {
                override suspend fun saveToken(key: String, token: String) {}
                override suspend fun getToken(key: String): String? = null
                override suspend fun deleteToken(key: String) {}
            }
        }

        // Encryption (disabled)
        single { EncryptionKeyManager(context.applicationContext) }
        singleOf(::DocumentEncryptor)

        // Processing pipeline
        singleOf(::RecordingStorage)
        singleOf(::RecordingPreprocessor)
        singleOf(::RecordingProcessor)
        singleOf(::RingTraceSession)

        // Agent
        factory { p -> AgentNenya(get(), p.getOrNull() ?: emptyList(), p.getOrNull() ?: false) }
        singleOf(::AgentFactory)
        singleOf(::RecordingOperationFactory)

        // Preferences
        single<Preferences> { E2EPreferences() }

        // Platform
        single<Platform> {
            object : Platform {
                override val name = "Android"
                override suspend fun openUrl(url: String) {}
                override suspend fun runWithBgTask(name: String, task: suspend () -> Unit) { task() }
            }
        }

        // Webhook (disabled)
        single {
            object : IndexWebhookApi {
                override fun uploadIfEnabled(samples: ShortArray?, sampleRate: Int, recordingId: String, transcription: String?) {}
                override val isEnabled: StateFlow<Boolean> = MutableStateFlow(false)
            }
        } bind IndexWebhookApi::class
        single<com.russhwolf.settings.Settings> {
            SharedPreferencesSettings(context.getSharedPreferences("e2e_test_prefs", Context.MODE_PRIVATE))
        }
        singleOf(::IndexWebhookPreferences)

        // Queue
        single { RecordingBackgroundScope(CoroutineScope(Dispatchers.Default + bgScopeJob)) }
        single { RecordingProcessingQueue(get(), get(), get(), get(), get(), get(), get(), get(), rescheduleDelay = 100.milliseconds) }
    }
}

private class E2EPreferences : Preferences {
    override val useCactusAgent: StateFlow<Boolean> = MutableStateFlow(false)
    override val useCactusTranscription: StateFlow<Boolean> = MutableStateFlow(false)
    override val cactusMode: CactusSTTMode = CactusSTTMode.fromId(0)
    override val ringPaired: StateFlow<String?> = MutableStateFlow(null)
    override val ringPairedName: StateFlow<String?> = MutableStateFlow(null)
    override val ringPairedOld: StateFlow<Boolean> = MutableStateFlow(false)
    override val musicControlMode: StateFlow<MusicControlMode> = MutableStateFlow(MusicControlMode.Disabled)
    override val lastSyncIndex: StateFlow<Int?> = MutableStateFlow(null)
    override val debugDetailsEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    override val approvedBeeperContacts: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override val secondaryMode: StateFlow<SecondaryMode> = MutableStateFlow(SecondaryMode.Disabled)
    override val reminderProvider: StateFlow<ReminderProvider> = MutableStateFlow(ReminderProvider.Native)
    override val noteProvider: StateFlow<NoteProvider> = MutableStateFlow(NoteProvider.Builtin)
    override val noteShortcut: StateFlow<NoteShortcutType> = MutableStateFlow(NoteShortcutType.SendToMe)
    override val backupEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    override val useEncryption: StateFlow<Boolean> = MutableStateFlow(false)
    override val encryptionKeyFingerprint: StateFlow<String?> = MutableStateFlow(null)
    override val lastWipedRing: StateFlow<String?> = MutableStateFlow(null)

    override suspend fun setUseCactusAgent(useCactus: Boolean) {}
    override suspend fun setUseCactusTranscription(useCactus: Boolean) {}
    override fun setCactusMode(mode: CactusSTTMode) {}
    override fun setRingPaired(id: String?) {}
    override fun setRingPairedName(name: String?) {}
    override fun setMusicControlMode(mode: MusicControlMode) {}
    override suspend fun setLastSyncIndex(index: Int?) {}
    override fun setDebugDetailsEnabled(enabled: Boolean) {}
    override suspend fun setApprovedBeeperContacts(contacts: List<String>?) {}
    override fun setSecondaryMode(mode: SecondaryMode) {}
    override fun setReminderProvider(provider: ReminderProvider) {}
    override fun setNoteProvider(provider: NoteProvider) {}
    override fun setNoteShortcut(shortcut: NoteShortcutType) {}
    override fun setBackupEnabled(enabled: Boolean) {}
    override fun setUseEncryption(enabled: Boolean) {}
    override fun setEncryptionKeyFingerprint(fingerprint: String?) {}
    override fun setLastWipedRing(id: String?) {}
}
