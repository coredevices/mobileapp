package coredevices.coreapp.ring.queue

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.mcp.client.McpIntegration
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.AgentNenya
import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.util.Platform
import coredevices.ring.api.NenyaClient
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
import coredevices.ring.external.vermillion.VermillionApi
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.recordings.RecordingPreprocessor
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.service.recordings.button.RecordingOperationFactory
import coredevices.ring.storage.RecordingStorage
import coredevices.util.models.CactusSTTMode
import coredevices.util.queue.TaskStatus
import coredevices.util.transcription.TranscriptionService
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// region Fakes

class FakePreferences : Preferences {
    override val useCactusAgent: StateFlow<Boolean> = MutableStateFlow(false)
    override val useCactusTranscription: StateFlow<Boolean> = MutableStateFlow(false)
    override val cactusMode: CactusSTTMode = CactusSTTMode.fromId(0)
    override val ringPaired: StateFlow<String?> = MutableStateFlow(null)
    override val ringPairedOld: StateFlow<Boolean> = MutableStateFlow(false)
    override val musicControlMode: StateFlow<MusicControlMode> = MutableStateFlow(MusicControlMode.Disabled)
    override val lastSyncIndex: StateFlow<Int?> = MutableStateFlow(null)
    override val debugDetailsEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    override val approvedBeeperContacts: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override val secondaryMode: StateFlow<SecondaryMode> = MutableStateFlow(SecondaryMode.Disabled)
    override val reminderProvider: StateFlow<ReminderProvider> = MutableStateFlow(ReminderProvider.Native)
    override val noteProvider: StateFlow<NoteProvider> = MutableStateFlow(NoteProvider.Builtin)
    override val noteShortcut: StateFlow<NoteShortcutType> = MutableStateFlow(NoteShortcutType.SendToMe)

    override suspend fun setUseCactusAgent(useCactus: Boolean) {}
    override suspend fun setUseCactusTranscription(useCactus: Boolean) {}
    override fun setCactusMode(mode: CactusSTTMode) {}
    override fun setRingPaired(id: String?) {}
    override fun setMusicControlMode(mode: MusicControlMode) {}
    override suspend fun setLastSyncIndex(index: Int?) {}
    override fun setDebugDetailsEnabled(enabled: Boolean) {}
    override suspend fun setApprovedBeeperContacts(contacts: List<String>?) {}
    override fun setSecondaryMode(mode: SecondaryMode) {}
    override fun setReminderProvider(provider: ReminderProvider) {}
    override fun setNoteProvider(provider: NoteProvider) {}
    override fun setNoteShortcut(shortcut: NoteShortcutType) {}
}

class FakeServletRepository : ServletRepository {
    override fun getAllServlets(): List<McpServerDefinition> = emptyList()
    override fun resolveName(name: String): McpIntegration? = null
}

// endregion

class RecordingProcessingQueueTest {
    private lateinit var context: Context
    private lateinit var db: RingDatabase
    private lateinit var taskDao: RecordingProcessingTaskDao
    private lateinit var entryDao: RecordingEntryDao
    private lateinit var messageDao: ConversationMessageDao
    private lateinit var queue: RecordingProcessingQueue
    private lateinit var fakeNenya: FakeNenyaClient
    private lateinit var fakeTranscription: FakeTranscriptionService
    private lateinit var bgScopeJob: CompletableJob

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        stopKoin()

        fakeNenya = FakeNenyaClient()
        fakeTranscription = FakeTranscriptionService()
        bgScopeJob = SupervisorJob()

        startKoin {
            androidContext(context)
            modules(createTestModule())
        }

        val koin = GlobalContext.get()
        db = koin.get()
        taskDao = db.recordingProcessingTaskDao()
        entryDao = koin.get()
        messageDao = koin.get()
        queue = koin.get()

        // Seed MCP sandbox so getDefaultGroupId() works
        runBlocking {
            koin.get<McpSandboxRepository>().seedDatabase()
        }
    }

    @After
    fun tearDown() {
        queue.close()
        bgScopeJob.cancel()
        db.close()
        stopKoin()
        // Clean up fake audio files
        File(context.cacheDir, "recordings").deleteRecursively()
    }

    private fun createTestModule() = module {
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

        // Repositories
        singleOf(::RecordingProcessingTaskRepository)
        singleOf(::RecordingRepository)
        singleOf(::RingTransferRepository)
        singleOf(::McpSandboxRepository)
        singleOf(::RecordingStorage)

        // Fakes
        single<NenyaClient> { fakeNenya }
        single<TranscriptionService> { fakeTranscription }
        single<Preferences> { FakePreferences() }
        single<ServletRepository> { FakeServletRepository() }
        single<Platform> {
            object : Platform {
                override val name = "Android"
                override suspend fun openUrl(url: String) {}
            }
        }
        // Real BuiltinServletRepository needed by McpSessionFactory (never actually resolves tools
        // because FakeServletRepository
        // seeds no builtins)
        single { BuiltinServletRepository() }

        // Agent (uses FakeNenyaClient via Koin)
        factory { p -> AgentNenya(get(), p.getOrNull() ?: emptyList(), p.getOrNull() ?: false) }
        singleOf(::AgentFactory)
        singleOf(::McpSessionFactory)

        single {
            object : VermillionApi {
                override fun uploadIfEnabled(samples: ShortArray, sampleRate: Int, recordingId: String) {}
                override val isEnabled: StateFlow<Boolean> = MutableStateFlow(false)
            }
        } bind VermillionApi::class

        singleOf(::RecordingOperationFactory)
        singleOf(::RecordingProcessor)
        singleOf(::RecordingPreprocessor)

        // Background scope with short reschedule delay
        single { RecordingBackgroundScope(CoroutineScope(Dispatchers.Default + bgScopeJob)) }
        single { RecordingProcessingQueue(get(), get(), get(), get(), get(), get(), get(), rescheduleDelay = 100.milliseconds) }
    }

    private fun createFakeAudioFile(fileId: String) {
        val cacheDir = File(context.cacheDir, "recordings")
        cacheDir.mkdirs()
        val file = File(cacheDir, fileId)
        file.writeBytes(ByteArray(1024)) // Dummy PCM data
        runBlocking {
            db.cachedRecordingMetadataDao().insert(
                CachedRecordingMetadata(id = fileId, sampleRate = 16000, mimeType = "audio/pcm")
            )
        }
    }

    private suspend fun awaitTaskDone(taskId: Long, timeout: kotlin.time.Duration = 15.seconds) {
        withTimeout(timeout) {
            taskDao.getTaskByIdFlow(taskId).first { it != null && it.status != TaskStatus.Pending }
        }
    }

    private suspend fun awaitAttempts(taskId: Long, minAttempts: Int, timeout: kotlin.time.Duration = 15.seconds) {
        withTimeout(timeout) {
            queue.activeTaskIds.first { taskId in it }
            queue.activeTaskIds.first { taskId !in it } // Wait for task to finish processing attempt
            taskDao.getTaskByIdFlow(taskId).first { it != null && it.attempts >= minAttempts }
        }
    }

    /**
     * Creates a new [RecordingProcessingQueue] using the existing Koin dependencies but a fresh
     * [RecordingBackgroundScope] tied to [bgScopeJob].
     */
    private fun createQueue(rescheduleDelay: kotlin.time.Duration = 100.milliseconds): RecordingProcessingQueue {
        val koin = GlobalContext.get()
        return RecordingProcessingQueue(
            recordingStorage = koin.get(),
            transferRepository = koin.get(),
            recordingRepository = koin.get(),
            queueTaskRepository = koin.get(),
            recordingOperationFactory = koin.get(),
            scope = RecordingBackgroundScope(CoroutineScope(Dispatchers.Default + bgScopeJob)),
            recordingPreprocessor = koin.get(),
            rescheduleDelay = rescheduleDelay
        )
    }

    /**
     * Simulates an app restart by closing the current queue, cancelling the scope, and creating
     * an entirely new queue object backed by the same database.
     */
    private fun simulateRestart() {
        queue.close()
        bgScopeJob.cancel()
        bgScopeJob = SupervisorJob()
        queue = createQueue()
    }

    // ---- Test Cases ----

    @Test
    fun textProcessing_basicFlow_succeeds() = runBlocking {
        // Agent: first call returns tool_calls, second call returns final response
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls,
            FakeNenyaClient.NenyaResponse.SuccessFinal
        )

        queue.queueTextProcessing("Remember to buy groceries")
        awaitTaskDone(taskId = 1)

        val task = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Success, task.status)
        assertEquals(1, task.attempts)

        // Verify a LocalRecording was created
        val recording = db.localRecordingDao().getRecording(1)
        assertNotNull(recording)

        // Verify conversation messages were persisted
        val messages = messageDao.getMessagesForRecording(1).first()
        assertTrue("Expected conversation messages", messages.isNotEmpty())
        assertTrue(
            "Expected user message",
            messages.any { it.document.role == MessageRole.user }
        )
        assertTrue(
            "Expected assistant message",
            messages.any { it.document.role == MessageRole.assistant }
        )
    }

    @Test
    fun localAudioProcessing_basicFlow_succeeds() = runBlocking {
        val fileId = "test-audio-basic"
        createFakeAudioFile(fileId)

        fakeTranscription.enqueue(FakeTranscriptionService.Behavior.Success("Hello world"))
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls,
            FakeNenyaClient.NenyaResponse.SuccessFinal
        )

        queue.queueLocalAudioProcessing(fileId)
        awaitTaskDone(taskId = 1)

        val task = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Success, task.status)
        assertEquals(1, task.attempts)

        // Verify recording entry was created with correct status and transcription
        val recording = db.localRecordingDao().getRecording(1)
        assertNotNull(recording)
        val entries = entryDao.getEntriesForRecording(1).first()
        assertEquals(1, entries.size)
        assertEquals(RecordingEntryStatus.completed, entries[0].status)
        assertEquals("Hello world", entries[0].transcription)

        // Verify conversation messages
        val messages = messageDao.getMessagesForRecording(1).first()
        assertTrue("Expected conversation messages", messages.isNotEmpty())
    }

    @Test
    fun localAudioProcessing_transcriptionNetworkError_retriesAndSucceeds() = runBlocking {
        val fileId = "test-audio-txn-net"
        createFakeAudioFile(fileId)

        // First attempt: transcription network error → RecoverableTaskException → retry
        // Second attempt: transcription succeeds
        fakeTranscription.enqueue(
            FakeTranscriptionService.Behavior.NetworkError,
            FakeTranscriptionService.Behavior.Success("Retried transcription")
        )
        // Agent responses for second attempt (first attempt never reaches agent)
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls,
            FakeNenyaClient.NenyaResponse.SuccessFinal
        )

        queue.queueLocalAudioProcessing(fileId)
        awaitTaskDone(taskId = 1)

        val task = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Success, task.status)
        assertTrue("Expected at least 2 attempts", task.attempts >= 2)

        val entries = entryDao.getEntriesForRecording(1).first()
        assertEquals(1, entries.size)
        assertEquals(RecordingEntryStatus.completed, entries[0].status)
        assertEquals("Retried transcription", entries[0].transcription)
    }

    @Test
    fun localAudioProcessing_noSpeechDetected_taskFails() = runBlocking {
        val fileId = "test-audio-no-speech"
        createFakeAudioFile(fileId)

        fakeTranscription.enqueue(FakeTranscriptionService.Behavior.NoSpeech)

        queue.queueLocalAudioProcessing(fileId)
        awaitTaskDone(taskId = 1)

        val task = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Failed, task.status)

        // Entry should exist with transcription_error status
        val entries = entryDao.getEntriesForRecording(1).first()
        assertEquals(1, entries.size)
        assertEquals(RecordingEntryStatus.transcription_error, entries[0].status)
        assertNotNull(entries[0].error)
    }

    @Test
    fun localAudioProcessing_agentNetworkError_retriesAndSucceeds() = runBlocking {
        val fileId = "test-audio-agent-net"
        createFakeAudioFile(fileId)

        // Transcription always succeeds
        fakeTranscription.enqueue(
            FakeTranscriptionService.Behavior.Success("Agent retry test"),
            FakeTranscriptionService.Behavior.Success("Agent retry test")
        )
        // First attempt: tool_calls → IOException in tool loop → AgentNetworkException → retry
        // Second attempt: full success
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls, // 1st attempt, 1st call
            FakeNenyaClient.NenyaResponse.ThrowIOException,     // 1st attempt, 2nd call (tool loop) → AgentNetworkException
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls, // 2nd attempt, 1st call
            FakeNenyaClient.NenyaResponse.SuccessFinal          // 2nd attempt, 2nd call
        )

        queue.queueLocalAudioProcessing(fileId)
        awaitTaskDone(taskId = 1)

        val task = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Success, task.status)
        assertTrue("Expected at least 2 attempts", task.attempts >= 2)

        val entries = entryDao.getEntriesForRecording(1).first()
        assertEquals(1, entries.size)
        assertEquals(RecordingEntryStatus.completed, entries[0].status)
    }

    @Test
    fun localAudioProcessing_agentFatalError_completesSuccessfully() = runBlocking {
        // Tests that a fatal agent error (HTTP 500 in tool loop) is swallowed by
        // RecordingProcessor.processText, resulting in a "completed" entry and successful task.
        // This documents the current production behavior.
        val fileId = "test-audio-agent-fatal"
        createFakeAudioFile(fileId)

        fakeTranscription.enqueue(FakeTranscriptionService.Behavior.Success("Fatal error test"))
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls, // 1st call → tool_calls
            FakeNenyaClient.NenyaResponse.ServerError500        // 2nd call → 500, agent throws Exception, processText swallows
        )

        queue.queueLocalAudioProcessing(fileId)
        awaitTaskDone(taskId = 1)

        val task = taskDao.getTaskById(1)!!
        // Error is swallowed by processText's catch(Throwable) — task succeeds
        assertEquals(TaskStatus.Success, task.status)
        assertEquals(1, task.attempts)

        val entries = entryDao.getEntriesForRecording(1).first()
        assertEquals(1, entries.size)
        // Entry is marked completed because processText returned normally
        assertEquals(RecordingEntryStatus.completed, entries[0].status)
    }

    // ---- Restart / Resume Test Cases ----

    @Test
    fun textProcessing_resumeAfterRestart_succeeds() = runBlocking {
        // Use a queue with a long retry delay so the automatic retry doesn't fire
        // before we can simulate the restart
        queue.close()
        bgScopeJob.cancel()
        bgScopeJob = SupervisorJob()
        queue = createQueue(rescheduleDelay = 60.seconds)

        // First attempt: agent network error → RecoverableTaskException → task stays Pending
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls, // 1st call → tool_calls
            FakeNenyaClient.NenyaResponse.ThrowIOException      // 2nd call → AgentNetworkException → RecoverableTaskException
        )

        queue.queueTextProcessing("Remember to buy groceries")
        awaitAttempts(taskId = 1, minAttempts = 1)

        // Verify task is still pending after the recoverable failure
        val taskBeforeRestart = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Pending, taskBeforeRestart.status)

        // Simulate app restart: destroy old queue, create a completely new one
        simulateRestart()

        // Set up agent for the resumed attempt
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls,
            FakeNenyaClient.NenyaResponse.SuccessFinal
        )

        // Resume pending tasks (like app startup would do)
        queue.resumePendingTasks()
        awaitTaskDone(taskId = 1)

        val task = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Success, task.status)
        assertTrue("Expected at least 2 attempts", task.attempts >= 2)

        // Verify conversation messages were persisted by the resumed attempt
        val messages = messageDao.getMessagesForRecording(1).first()
        assertTrue("Expected conversation messages", messages.isNotEmpty())
    }

    @Test
    fun localAudioProcessing_resumeAfterRestart_succeeds() = runBlocking {
        val fileId = "test-audio-restart"
        createFakeAudioFile(fileId)

        // Use a queue with a long retry delay
        queue.close()
        bgScopeJob.cancel()
        bgScopeJob = SupervisorJob()
        queue = createQueue(rescheduleDelay = 60.seconds)

        // First attempt: transcription network error → RecoverableTaskException
        fakeTranscription.enqueue(FakeTranscriptionService.Behavior.NetworkError)

        queue.queueLocalAudioProcessing(fileId)
        awaitAttempts(taskId = 1, minAttempts = 1)

        // Verify task is still pending after the recoverable failure
        assertEquals(TaskStatus.Pending, taskDao.getTaskById(1)!!.status)

        // Simulate app restart
        simulateRestart()

        // Set up for successful retry after restart
        fakeTranscription.enqueue(FakeTranscriptionService.Behavior.Success("Restarted transcription"))
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls,
            FakeNenyaClient.NenyaResponse.SuccessFinal
        )

        queue.resumePendingTasks()
        awaitTaskDone(taskId = 1)

        val task = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Success, task.status)
        assertTrue("Expected at least 2 attempts", task.attempts >= 2)

        // Verify the recording entry was reused (not duplicated) and completed correctly
        val entries = entryDao.getEntriesForRecording(1).first()
        assertEquals(1, entries.size)
        assertEquals(RecordingEntryStatus.completed, entries[0].status)
        assertEquals("Restarted transcription", entries[0].transcription)
    }

    @Test
    fun resumeAfterRestart_completedTaskNotReprocessed() = runBlocking {
        // Process a text task to completion
        fakeNenya.enqueue(
            FakeNenyaClient.NenyaResponse.SuccessWithToolCalls,
            FakeNenyaClient.NenyaResponse.SuccessFinal
        )

        queue.queueTextProcessing("Already done")
        awaitTaskDone(taskId = 1)

        val taskBefore = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Success, taskBefore.status)
        val attemptsBefore = taskBefore.attempts

        // Simulate app restart
        simulateRestart()
        queue.resumePendingTasks()

        // Wait to confirm no reprocessing occurs
        delay(1.seconds)

        val taskAfter = taskDao.getTaskById(1)!!
        assertEquals(TaskStatus.Success, taskAfter.status)
        assertEquals(attemptsBefore, taskAfter.attempts)
    }
}
