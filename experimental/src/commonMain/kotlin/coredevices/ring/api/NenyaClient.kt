package coredevices.ring.api

import co.touchlab.kermit.Logger
import coredevices.api.ApiClient
import coredevices.indexai.data.MessageContentPartListSerializer
import coredevices.indexai.data.entity.ContentPartType
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageContentPart
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.ToolCall
import coredevices.ring.agent.ToolDeclaration
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class RunResponse(
    val success: Boolean,
    val message: String? = null,
    val conversation: List<OpenAIConversationMessage> = emptyList()
)

@Serializable
data class OpenAIConversationMessage(
    val role: MessageRole,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @Serializable(with = MessageContentPartListSerializer::class)
    val content: List<MessageContentPart>? = null,
    @SerialName("tool_calls")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tool_calls: List<ToolCall>? = emptyList(),
    @SerialName("tool_call_id")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tool_call_id: String? = null
) {
    fun toConversationMessage() = ConversationMessageDocument(
        role = role,
        content = content
            ?.filter { it.type == ContentPartType.text }
            ?.ifEmpty { null }
            ?.joinToString("\n") { it.text ?: "" },
        tool_calls = tool_calls,
        tool_call_id = tool_call_id
    )
}

fun List<OpenAIConversationMessage>.toConversationMessages() =
    this.map { it.toConversationMessage() }

data class RunResult(val statusCode: HttpStatusCode, val response: RunResponse?)

@Serializable
private data class RunRequestData(
    val device_tool_specifications: List<ToolDeclaration>,
    val input: String?,
    val conversation_history: List<ConversationMessageDocument>,
    val timezone: String,
    val additional_context: String,
    val search_mode: Boolean = false,
)

interface NenyaClient {
    companion object {
        const val RETRY_COUNT = 3
    }
    /**
     * Run an iteration of the agent.
     * @param input The input to the agent, or null to run the agent without input.
     * @param recordingId The ID of the recording feed entry to which this conversation belongs.
     * @param toolSpecs The specifications of the available tools to run.
     * @param timezone The timezone of the user.
     * @return The result of the run.
     */
    suspend fun run(
        input: String?,
        conversationHistory: List<ConversationMessageDocument> = emptyList(),
        toolSpecs: List<ToolDeclaration> = emptyList(),
        additionalContext: String = "",
        timezone: TimeZone = TimeZone.currentSystemDefault(),
        searchMode: Boolean = false,
    ): RunResult
}

class NenyaClientImpl(config: ApiConfig): NenyaClient, ApiClient(config.version) {
    private val baseUrl = config.nenyaUrl
    private val logger = Logger.withTag("NenyaClientImpl")

    override suspend fun run(
        input: String?,
        conversationHistory: List<ConversationMessageDocument>,
        toolSpecs: List<ToolDeclaration>,
        additionalContext: String,
        timezone: TimeZone,
        searchMode: Boolean
    ): RunResult {
        var retries = 0
        var resp: HttpResponse? = null
        while (retries < NenyaClient.RETRY_COUNT) {
            resp = try {
                client.post("$baseUrl/run") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    firebaseAuth()
                    setBody(
                        RunRequestData(
                            device_tool_specifications = toolSpecs,
                            input = input,
                            conversation_history = conversationHistory,
                            timezone = timezone.id,
                            additional_context = additionalContext,
                            search_mode = searchMode
                        )
                    )
                }
            } catch (e: Exception) {
                retries++
                logger.w(e) { "Error while requesting agent processing, try: $retries" }
                if (retries >= NenyaClient.RETRY_COUNT && e is IOException) { // Rethrow network errors
                    throw e
                }
                delay(1.seconds)
                continue // Retry on failure
            }
            if (resp.status.isSuccess()) {
                return RunResult(resp.status, resp.body())
            } else {
                retries++
                logger.w { "Received error response (try $retries): ${resp.status}, body: ${resp.body<String>()}" }
                delay(1.seconds)
            }
        }
        logger.e { "Failed to run agent after $retries attempts" }
        return RunResult(
            statusCode = resp?.status ?: HttpStatusCode.InternalServerError,
            response = null
        )
    }
}