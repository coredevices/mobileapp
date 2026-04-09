package coredevices.coreapp.ring.queue

import coredevices.indexai.data.entity.ContentPartType
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageContentPart
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.ToolCall
import coredevices.ring.agent.ToolDeclaration
import coredevices.ring.api.NenyaClient
import coredevices.ring.api.OpenAIConversationMessage
import coredevices.ring.api.RunResponse
import coredevices.ring.api.RunResult
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException

class FakeNenyaClient : NenyaClient {
    private val responseQueue = ArrayDeque<NenyaResponse>()

    sealed class NenyaResponse {
        /** Return assistant message with tool_calls so agent enters tool loop */
        data object SuccessWithToolCalls : NenyaResponse()
        /** Return final assistant message without tool_calls */
        data object SuccessFinal : NenyaResponse()
        /** Throw IOException (in tool loop this becomes AgentNetworkException) */
        data object ThrowIOException : NenyaResponse()
        /** Return HTTP 500 (in tool loop, agent throws Exception, processText swallows it) */
        data object ServerError500 : NenyaResponse()
    }

    fun enqueue(vararg responses: NenyaResponse) {
        responseQueue.addAll(responses)
    }

    override suspend fun run(
        input: String?,
        conversationHistory: List<ConversationMessageDocument>,
        toolSpecs: List<ToolDeclaration>,
        additionalContext: String,
        timezone: TimeZone,
        searchMode: Boolean,
    ): RunResult {
        val response = responseQueue.removeFirst()
        return when (response) {
            is NenyaResponse.SuccessWithToolCalls -> RunResult(
                statusCode = HttpStatusCode.OK,
                response = RunResponse(
                    success = true,
                    conversation = listOf(
                        OpenAIConversationMessage(
                            role = MessageRole.assistant,
                            content = listOf(
                                MessageContentPart(
                                    type = ContentPartType.text,
                                    text = "I'll create a note for you."
                                )
                            ),
                            tool_calls = listOf(
                                ToolCall(
                                    id = "call_${responseQueue.size}",
                                    type = "function",
                                    function = FunctionToolCall(
                                        name = "notes.create_note",
                                        arguments = """{"text":"test note"}"""
                                    )
                                )
                            )
                        )
                    )
                )
            )
            is NenyaResponse.SuccessFinal -> RunResult(
                statusCode = HttpStatusCode.OK,
                response = RunResponse(
                    success = true,
                    conversation = listOf(
                        OpenAIConversationMessage(
                            role = MessageRole.assistant,
                            content = listOf(
                                MessageContentPart(
                                    type = ContentPartType.text,
                                    text = "Done! Note created."
                                )
                            ),
                            tool_calls = emptyList()
                        )
                    )
                )
            )
            is NenyaResponse.ThrowIOException -> throw IOException("Fake network error")
            is NenyaResponse.ServerError500 -> RunResult(
                statusCode = HttpStatusCode.InternalServerError,
                response = RunResponse(success = false, message = "Internal Server Error")
            )
        }
    }
}
