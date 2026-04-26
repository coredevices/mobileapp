package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.data.entity.room.TraceEventData
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.libindex.database.repository.RingTransferRepository
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessingStage
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession
import coredevices.util.queue.RecoverableTaskException
import coredevices.util.transcription.TranscriptionException
import coredevices.util.transcription.TranscriptionSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

interface RecordingOperation {
    suspend fun run(handle: RecordingProcessingQueue.TaskHandle? = null)
}

open class DefaultRecordingOperation(
    private val mcpSandboxRepository: McpSandboxRepository,
    private val mcpSessionFactory: McpSessionFactory,
    private val chatAgent: Agent,
    private val recordingId: Long,
    private val transferId: Long?,
    private val fileId: String,
    private val trace: RingTraceSession,
    private val forcedTool: (suspend (messageText: String) -> ToolCallResult)?,
    private val runAgent: Boolean = true,
) : RecordingOperation, KoinComponent {
    companion object {
        private val logger = Logger.withTag("DefaultRecordingOperation")
    }

    private val recordingStorage: RecordingStorage by inject()
    private val recordingEntryDao: RecordingEntryDao by inject()
    private val recordingProcessor: RecordingProcessor by inject()
    private val ringTransferRepository: RingTransferRepository by inject()
    private val recordingBackgroundScope: RecordingBackgroundScope by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        val entryId = withContext(Dispatchers.IO) {
            if (handle?.stage is RecordingProcessingStage.RecordingEntryCreated) {
                val id = (handle.stage as RecordingProcessingStage.RecordingEntryCreated).recordingEntryId
                trace.markEvent(
                    "recording_entry_reused",
                    TraceEventData.RecordingEntryInfo(id, recordingId, transferId ?: -1)
                )
                id
            } else {
                val newId = recordingEntryDao.insertRecordingEntry(
                    RecordingEntryEntity(
                        recordingId = recordingId,
                        fileName = fileId
                    )
                )
                trace.markEvent(
                    "recording_entry_created",
                    TraceEventData.RecordingEntryInfo(newId, recordingId, transferId ?: -1)
                )
                handle?.updateStage(RecordingProcessingStage.RecordingEntryCreated(
                    recordingEntryId = newId,
                    previous = handle.stage!! as RecordingProcessingStage.RecordingEntityCreated)
                )
                newId
            }
        }
        transferId?.let {
            ringTransferRepository.linkRecordingEntryToTransfer(
                transferId,
                entryId
            )
        }
        val (source, meta) = recordingStorage.openRecordingSource(fileId)
        // Fire-and-forget Firebase upload on the long-lived background scope. The local
        // cache file is already fully written by the time we get here, so the rest of the
        // operation (transcription / agent) doesn't depend on this completing, and a
        // failure should not block — or be cancelled by — the processing queue slot.
        recordingBackgroundScope.launch(Dispatchers.IO) {
            try {
                trace.markEvent("persist_recording_start", TraceEventData.PersistRecordingStart(
                    recordingId = recordingId,
                    transferId = transferId,
                    fileId = fileId
                ))
                recordingStorage.persistRecording(fileId)
                trace.markEvent("persist_recording_end", TraceEventData.PersistRecordingStart(
                    recordingId = recordingId,
                    transferId = transferId,
                    fileId = fileId
                ))
            } catch (e: Exception) {
                //TODO: Better sync handling, e.g. retry later
                logger.e(e) { "Error persisting recording $fileId" }
                trace.markEvent("persist_recording_fail", TraceEventData.PersistRecordingStart(
                    recordingId = recordingId,
                    transferId = transferId,
                    fileId = fileId
                ))
            }
        }
        coroutineScope {
            val transcription = try {
                trace.markEvent("transcription_start", TraceEventData.TranscriptionStart(
                    recordingId = recordingId,
                    recordingEntryId = entryId,
                    transferId = transferId ?: -1
                ))
                val txt = recordingProcessor.transcribe(
                    audioSource = source,
                    sampleRate = meta.cachedMetadata.sampleRate,
                ).flowOn(Dispatchers.IO)
                    .first { it is TranscriptionSessionStatus.Transcription } as TranscriptionSessionStatus.Transcription
                trace.markEvent("transcription_end", TraceEventData.TranscriptionEnd(
                    recordingId = recordingId,
                    recordingEntryId = entryId,
                    transferId = transferId ?: -1,
                    transcriptLength = txt.text.length,
                    modelUsed = txt.modelUsed,
                ))
                txt
            } catch (e: TranscriptionException.TranscriptionNetworkError) {
                trace.markEvent("transcription_fail", TraceEventData.TranscriptionFail(
                    recordingId = recordingId,
                    recordingEntryId = entryId,
                    transferId = transferId ?: -1,
                    modelUsed = e.modelUsed,
                    reason = "Network error: ${e.message}"
                ))
                recordingEntryDao.updateRecordingEntryStatus(
                    entryId,
                    status = RecordingEntryStatus.transcription_error,
                    error = "Network error during transcription: ${e.message}"
                )
                recordingEntryDao.updateRecordingEntryTranscription(
                    entryId,
                    transcription = null,
                    modelUsed = e.modelUsed
                )
                throw RecoverableTaskException("Network error during transcription", e)
            } catch (e: Exception) {
                trace.markEvent("transcription_fail", TraceEventData.TranscriptionFail(
                    recordingId = recordingId,
                    recordingEntryId = entryId,
                    transferId = transferId ?: -1,
                    modelUsed = (e as? TranscriptionException)?.modelUsed,
                    reason = e.message ?: "Unknown"
                ))
                recordingEntryDao.updateRecordingEntryStatus(
                    entryId,
                    status = RecordingEntryStatus.transcription_error,
                    error = e.message
                )
                if (e is TranscriptionException) {
                    recordingEntryDao.updateRecordingEntryTranscription(
                        entryId,
                        transcription = null,
                        modelUsed = e.modelUsed
                    )
                }
                throw e
            } finally {
                source.close()
            }
            recordingEntryDao.updateRecordingEntryTranscription(
                entryId,
                transcription.text,
                transcription.modelUsed
            )

            if (runAgent) {
                val mcpSession = mcpSessionFactory.createForSandboxGroup(
                    mcpSandboxRepository.getDefaultGroupId(),
                    this
                )
                try {
                    trace.markEvent("mcp_session_open_start", TraceEventData.RecordingEntryInfo(
                        entryId,
                        recordingId,
                        transferId ?: -1
                    ))
                    mcpSession.openSession()
                    trace.markEvent("mcp_session_open_end", TraceEventData.RecordingEntryInfo(
                        entryId,
                        recordingId,
                        transferId ?: -1
                    ))
                    logger.d { "Agent running..." }
                    recordingEntryDao.updateRecordingEntryStatus(
                        entryId,
                        status = RecordingEntryStatus.agent_processing
                    )
                    recordingProcessor.processText(
                        recordingId = recordingId,
                        recordingEntryId = entryId,
                        mcpSession = mcpSession,
                        agent = chatAgent,
                        forcedTool = forcedTool?.let { { it(transcription.text) } },
                        text = transcription.text
                    )
                    logger.d { "Processing complete." }
                    recordingEntryDao.updateRecordingEntryStatus(
                        entryId,
                        status = RecordingEntryStatus.completed
                    )
                } catch (e: Exception) {
                    recordingEntryDao.updateRecordingEntryStatus(
                        entryId,
                        status = RecordingEntryStatus.agent_error,
                        error = e.message
                    )
                    throw e
                } finally {
                    withTimeout(3.seconds) {
                        mcpSession.closeSession()
                    }
                }
            } else {
                logger.d { "Skipping agent (runAgent=false); marking entry completed." }
                trace.markEvent("agent_skipped", TraceEventData.RecordingEntryInfo(
                    entryId,
                    recordingId,
                    transferId ?: -1
                ))
                recordingEntryDao.updateRecordingEntryStatus(
                    entryId,
                    status = RecordingEntryStatus.completed
                )
            }
        }
    }
}