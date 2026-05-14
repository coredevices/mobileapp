package coredevices.ring.service.recordings

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.Agent
import coredevices.util.AudioEncoding
import coredevices.util.CoreConfigFlow
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.util.transcription.TranscriptionService
import coredevices.util.transcription.TranscriptionSessionStatus
import coredevices.mcp.client.McpSession
import coredevices.mcp.data.ToolCallResult
import coredevices.util.transcription.STTLanguage
import coredevices.ring.agent.AgentNetworkException
import coredevices.ring.data.entity.room.TraceEventData
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.ring.service.indexfeed.RecordingSessionContext
import coredevices.ring.util.trace.RingTraceSession
import coredevices.util.queue.RecoverableTaskException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class RecordingProcessor(
    private val transcriptionService: TranscriptionService,
    private val conversationMessageDao: ConversationMessageDao,
    private val recordingEntryDao: RecordingEntryDao,
    private val trace: RingTraceSession,
    private val coreConfigFlow: CoreConfigFlow,
    private val itemRepo: ItemRepository,
    private val recordingRepo: RecordingRepository,
    private val itemFactory: ItemFactory,
) {
    sealed interface RecordingStatus {
        /**
         * Emitted when transcription has begun. (Audio stream open, etc.)
         */
        data object Transcribing: RecordingStatus

        /**
         * Emitted when a partial transcription is available for display.
         */
        data class Partial(val partial: String): RecordingStatus

        sealed interface TextProcessingStatus : RecordingStatus {
            /**
             * Emitted when transcription is complete and the agent is processing input.
             */
            data object AgentRunning: TextProcessingStatus

            /**
             * Emitted when recording is fully processed (flow will complete after this).
             */
            data class Complete(val entryId: Long): TextProcessingStatus
        }
    }

    companion object {
        private const val AUDIO_STREAM_BUFFER_SIZE = 1024
        private val TRANSCRIPTION_TIMEOUT = 2.minutes
        private val logger = Logger.withTag("RecordingProcessor")
    }

    private suspend fun updateConversation(localRecordingId: Long, conversation: List<ConversationMessageDocument>) {
        withContext(Dispatchers.IO) {
            val existingMessages = conversationMessageDao.getMessagesForRecording(localRecordingId).first()
            val newMessages = conversation.drop(existingMessages.size).map {
                ConversationMessageEntity(
                    recordingId = localRecordingId,
                    document = it
                )
            }
            if (newMessages.isNotEmpty()) {
                conversationMessageDao.insertMessages(newMessages)
            }
        }
    }

    private suspend fun transcriptionStep(
        transcriptionTimeout: Duration,
        audioStreamFlow: Flow<ByteArray>,
        sampleRate: Int,
        language: STTLanguage,
        encoding: AudioEncoding
    ) = transcriptionService.transcribe(
        audioStreamFlow,
        sampleRate,
        language = language,
        encoding = encoding,
        timeout = transcriptionTimeout
    ).flowOn(Dispatchers.IO)

    private suspend fun updateRecordingEntryMessage(entryId: Long, messageId: Long) {
        withContext(Dispatchers.IO) {
            recordingEntryDao.updateRecordingEntryMessage(entryId, messageId)
        }
    }

    private suspend fun linkUserMessageToEntry(recordingId: Long, recordingEntryId: Long?) {
        if (recordingEntryId == null) return
        val userMessageId = conversationMessageDao.getLastMessageForRecordingByRole(
            recordingId,
            MessageRole.user
        ).firstOrNull()?.id
        userMessageId?.let { updateRecordingEntryMessage(recordingEntryId, it) }
    }

    // Pre-seed the user message so the transcription stays visible if the agent fails
    // and we roll back its in-memory conversation to keep task retry idempotent.
    private suspend fun persistUserMessageIfAbsent(
        recordingId: Long,
        recordingEntryId: Long?,
        text: String,
        expectedDbSize: Int
    ) {
        withContext(Dispatchers.IO) {
            val existingDbSize = conversationMessageDao.getMessagesForRecording(recordingId).first().size
            if (existingDbSize != expectedDbSize) return@withContext
            val userMessageId = conversationMessageDao.insertMessage(
                ConversationMessageEntity(
                    recordingId = recordingId,
                    document = ConversationMessageDocument(
                        role = MessageRole.user,
                        content = text
                    )
                )
            )
            recordingEntryId?.let { updateRecordingEntryMessage(it, userMessageId) }
        }
    }

    private fun watchConversationUpdates(scope: CoroutineScope, agent: Agent, localRecordingId: Long, recordingEntryId: Long?): Job {
        var updatedMessageId = false
        return agent.conversation.drop(1).onEach { // Skip the initial value as this will be the same as what we have already stored, or invalid
            trace.markEvent("agent_conversation_update", TraceEventData.AgentConversationUpdate(
                recordingId = localRecordingId,
                recordingEntryId = recordingEntryId,
                messageCount = it.size
            ))
            logger.d { "Agent conversation updated, ${it.size} messages:\n${if (coreConfigFlow.value.obfuscateSensitiveLogs) "[content redacted]" else it.joinToString("\n") { it.role.toString() + ": " + it.content }}" }
            updateConversation(localRecordingId, it)
            if (recordingEntryId != null && !updatedMessageId) {
                val userMessageId = conversationMessageDao.getLastMessageForRecordingByRole(
                    localRecordingId,
                    MessageRole.user
                ).firstOrNull()?.id
                logger.d { "User message ID for recording entry update: $userMessageId\nconv: ${if (coreConfigFlow.value.obfuscateSensitiveLogs) "[content redacted]" else conversationMessageDao.getMessagesForRecording(localRecordingId)}" }
                userMessageId?.let {
                    updateRecordingEntryMessage(recordingEntryId, it)
                    updatedMessageId = true
                }
            }
        }.flowOn(Dispatchers.IO).launchIn(scope)
    }

    suspend fun transcribe(
        audioSource: Source,
        sampleRate: Int,
        encoding: AudioEncoding = AudioEncoding.PCM_16BIT
    ): Flow<TranscriptionSessionStatus> {
        val audioStreamFlow = flow {
            audioSource.use {
                val buffer = Buffer()
                while (true) {
                    val bytesRead = it.readAtMostTo(buffer, AUDIO_STREAM_BUFFER_SIZE.toLong())
                    if (bytesRead == -1L) {
                        break
                    }
                    val bytes = buffer.readByteArray()
                    emit(bytes)
                }
                logger.d { "Audio stream exhausted" }
            }
        }.flowOn(Dispatchers.IO)

        return transcriptionStep(
            transcriptionTimeout = TRANSCRIPTION_TIMEOUT,
            audioStreamFlow = audioStreamFlow,
            sampleRate = sampleRate,
            encoding = encoding,
            language = STTLanguage.fromCodeOrAutomatic(coreConfigFlow.value.sttConfig.spokenLanguage),
        )
    }

    suspend fun processText(
        recordingId: Long,
        recordingEntryId: Long?,
        mcpSession: McpSession,
        agent: Agent,
        forcedTool: (suspend () -> ToolCallResult)? = null,
        text: String
    ) {
        val rec = withContext(Dispatchers.IO) { recordingRepo.getRecording(recordingId) }
        val firestoreId = rec?.firestoreId
        val createdAt = rec?.localTimestamp ?: Clock.System.now()
        val sessionContext = firestoreId?.let { RecordingSessionContext(it, createdAt) }

        trace.markEvent("agent_processing_start",
            TraceEventData.AgentProcessingStart(
                recordingId = recordingId,
                recordingEntryId = recordingEntryId,
                forcedToolPresent = forcedTool != null,
                agent = agent.label,
            )
        )
        val convEndIdx = agent.conversation.first().size
        persistUserMessageIfAbsent(recordingId, recordingEntryId, text, convEndIdx)
        val convUpdJob = watchConversationUpdates(
            CoroutineScope(currentCoroutineContext()),
            agent,
            recordingId,
            recordingEntryId
        )
        try {
            withContext(if (sessionContext != null) currentCoroutineContext() + sessionContext else currentCoroutineContext()) {
                agent.send(text, mcpSession)
            }
        } catch (e: AgentNetworkException) {
            // Reset conversation to before processing so task retry works correctly
            logger.e(e) { "Error during agent processing" }
            convUpdJob.cancel()
            trace.markEvent("agent_processing_failed",
                TraceEventData.AgentProcessingFailed(
                    recordingId = recordingId,
                    recordingEntryId = recordingEntryId,
                    agent = agent.label,
                    reason = "Network error: ${e.message}"
                )
            )
            throw RecoverableTaskException("Network error during agent processing: ${e.message}", e)
        } catch (e: Throwable) {
            logger.e(e) { "Error during agent processing" }
        } finally {
            convUpdJob.cancelAndJoin()
            updateConversation(recordingId, agent.conversation.first().take(convEndIdx))
        }
        val conv = agent.conversation.firstOrNull() ?: emptyList()
        val noToolRan = conv.drop(convEndIdx).all { it.role != MessageRole.tool }
        if (forcedTool != null && noToolRan) {
            // Agent did not take any action, force tool
            val toolResult = forcedTool()
            logger.w { "Forcing tool call result into conversation" }
            agent.addMessage(
                ConversationMessageDocument(
                    role = MessageRole.tool,
                    content = toolResult.resultString,
                    semantic_result = toolResult.semanticResult,
                    tool_call_id = Uuid.random().toString(),
                    language_model_used = null,
                    is_forced_tool = true
                )
            )
        }
        updateConversation(recordingId, agent.conversation.first())
        linkUserMessageToEntry(recordingId, recordingEntryId)
        trace.markEvent("agent_processing_end",
            TraceEventData.AgentProcessingEnd(
                recordingId = recordingId,
                recordingEntryId = recordingEntryId,
                forcedToolUsed = forcedTool != null && noToolRan,
                agent = agent.label,
            )
        )
    }
}