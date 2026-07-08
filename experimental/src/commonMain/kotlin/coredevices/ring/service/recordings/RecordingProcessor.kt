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
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.util.transcription.STTLanguage
import coredevices.ring.agent.AgentNetworkException
import coredevices.ring.data.entity.room.TraceEventData
import coredevices.libindex.database.repository.RingTransferRepository
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.ring.util.trace.RingTraceSession
import coredevices.util.queue.RecoverableTaskException
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RecordingProcessor(
    private val transcriptionService: TranscriptionService,
    private val conversationMessageDao: ConversationMessageDao,
    private val recordingEntryDao: RecordingEntryDao,
    private val trace: RingTraceSession,
    private val coreConfigFlow: CoreConfigFlow,
    private val itemRepo: ItemRepository,
    private val recordingRepo: RecordingRepository,
    private val transferRepository: RingTransferRepository,
    private val itemFactory: ItemFactory,
    private val localReminderDao: LocalReminderDao,
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
        internal val TRANSCRIPTION_TIMEOUT = 45.seconds
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

    // transcriptionService.transcribe(...) returns a cold flow; the timeout that bounds the
    // (blocking) transcription is applied by the caller around collection (see RecordingOperation),
    // because a timeout wrapped around building the cold flow expires instantly and bounds nothing.
    private suspend fun transcriptionStep(
        audioStreamFlow: Flow<ByteArray>,
        sampleRate: Int,
        language: STTLanguage,
        encoding: AudioEncoding,
        dictionaryContext: List<String>? = null,
    ) = transcriptionService.transcribe(
        audioStreamFlow,
        sampleRate,
        language = language,
        encoding = encoding,
        dictionaryContext = dictionaryContext,
        initialTimeout = TRANSCRIPTION_TIMEOUT,
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
        encoding: AudioEncoding = AudioEncoding.PCM_16BIT,
        dictionaryContext: List<String>? = null
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
            audioStreamFlow = audioStreamFlow,
            sampleRate = sampleRate,
            encoding = encoding,
            dictionaryContext = dictionaryContext,
            language = STTLanguage.fromCodeOrAutomatic(coreConfigFlow.value.sttConfig.spokenLanguage),
        )
    }

    suspend fun processText(
        recordingId: Long,
        recordingEntryId: Long?,
        mcpSession: McpSession,
        agent: Agent,
        forcedTool: (suspend (assistantMessage: String?, sessionContext: SessionContext) -> ToolCallResult)? = null,
        text: String
    ) {
        val rec = withContext(Dispatchers.IO) { recordingRepo.getRecording(recordingId) }
        val firestoreId = rec?.firestoreId
        val createdAt = rec?.localTimestamp ?: Clock.System.now()
        // Ring recordings only have a true recording time when the ring reported the button
        // press; other sources (chat, phone mic) stamp the recording at capture time.
        val ringTransfer = withContext(Dispatchers.IO) {
            transferRepository.getTransfersByRecordingId(recordingId).firstOrNull()
        }
        val timeBase = if (ringTransfer != null) {
            ringTransfer.transferInfo?.buttonReleased?.let { Instant.fromEpochMilliseconds(it) }
        } else {
            createdAt
        }
        val sessionContext = SessionContext(
            timeBase = timeBase,
            userMessageText = CompletableDeferred(text),
            recordingFirestoreId = firestoreId,
        )

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
            agent.send(text, mcpSession, sessionContext)
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
        // No tool messages
        val noToolRan = conv.drop(convEndIdx).none {
            it.role == MessageRole.tool
        }
        // Last tool message indicates fallback was requested
        val toolRequestedFallback = conv.drop(convEndIdx).lastOrNull { it.role == MessageRole.tool }?.let {
            it.semantic_result is SemanticResult.GenericFailure && (it.semantic_result as SemanticResult.GenericFailure).forceFallbackTool
        } ?: false
        if (forcedTool != null && (noToolRan || toolRequestedFallback)) {
            val lastAssistantMessage = conv.drop(convEndIdx)
                .lastOrNull { it.role == MessageRole.assistant }
                ?.content
            // Agent did not take any action, force tool
            val toolResult = forcedTool(lastAssistantMessage, sessionContext)
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
        // Create feed items from this turn's tool messages, stamping each with the tool_call_id
        // that produced it. Done centrally (rather than in each tool) because only the tool message
        // carries the tool_call_id.
        if (firestoreId != null) {
            agent.conversation.first().drop(convEndIdx)
                .filter { it.role == MessageRole.tool }
                .forEach { msg ->
                    val item = msg.semantic_result?.let {
                        itemFactory.createFromSemanticResult(it, firestoreId, createdAt, msg.tool_call_id)
                    } ?: return@forEach
                    val itemId = itemFactory.simpleUid()
                    runCatching { itemRepo.setItem(itemId, item) }
                        .onFailure { logger.e(it) { "Failed to persist item for tool_call ${msg.tool_call_id}" } }
                }
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