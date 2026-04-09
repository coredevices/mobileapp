package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.time.HumanDateTimeParser
import coredevices.indexai.time.InterpretedDateTime
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.builtin_servlets.clock.SetTimerTool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

class ContextualActionPredictor(
    private val conversationMessageDao: ConversationMessageDao
) {
    companion object {
        private val logger = Logger.withTag("ContextualActionPredictor")
    }
    suspend fun getActions(recordingId: Long): Set<ContextualActionType> {
        val conversation = conversationMessageDao.getMessagesForRecording(recordingId).first()
        val toolResult = conversation.firstOrNull { it.document.semantic_result != null }
        val userMsg = conversation.firstOrNull { it.document.role == MessageRole.user }
        val now = Clock.System.now()
        return buildSet {
            if (userMsg != null) {
                val timeParser = HumanDateTimeParser()
                val parsedTime = timeParser.parseFromMessage(userMsg.document.content ?: "")
                logger.d { "Parsed time from user message: $parsedTime" }
                if (parsedTime != null) {
                    val tz = TimeZone.currentSystemDefault()
                    val fireTime = SetTimerTool.interpretedTimeToFireTime(parsedTime.dateTime, now, tz)
                    if (fireTime > now) {
                        logger.d { "Adding contextual action for time: $fireTime" }
                        add(ContextualActionType.Timer(fireTime))
                        if (parsedTime.dateTime !is InterpretedDateTime.Relative) {
                            add(ContextualActionType.TaskWithDeadline(fireTime, allDay = parsedTime.dateTime is InterpretedDateTime.AbsoluteDate))
                        }
                    } else {
                        logger.d { "Parsed time is in the past, not adding contextual action." }
                    }
                } else {
                    toolResult?.let {
                        val deadline = (it.document.semantic_result as? SemanticResult.TaskCreation)?.deadline
                        if (deadline != null && deadline > now) {
                            logger.d { "Adding contextual action for task result with deadline: $deadline" }
                            add(ContextualActionType.TaskWithDeadline(deadline, allDay = false))
                        }
                    }
                }
            }
        }
    }
}

sealed interface ContextualActionType {
    data class TaskWithDeadline(val deadline: Instant, val allDay: Boolean): ContextualActionType
    data class Timer(val fireTime: Instant): ContextualActionType
}