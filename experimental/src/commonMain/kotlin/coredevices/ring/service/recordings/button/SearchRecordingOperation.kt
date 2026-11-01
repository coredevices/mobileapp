package coredevices.ring.service.recordings.button

import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.ChatMode
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.util.trace.RingTraceSession
import org.koin.core.component.KoinComponent

class SearchRecordingOperation(
    agentFactory: AgentFactory,
    mcpSessionFactory: McpSessionFactory,
    trace: RingTraceSession,
    localRecordingId: Long,
    transferId: Long?,
    fileId: String,
) : DefaultRecordingOperation(
    mcpSessionFactory = mcpSessionFactory,
    chatAgent = agentFactory.createForChatMode(
        mode = ChatMode.Search
    ),
    recordingId = localRecordingId,
    transferId = transferId,
    fileId = fileId,
    forcedTool = null,
    trace = trace
)