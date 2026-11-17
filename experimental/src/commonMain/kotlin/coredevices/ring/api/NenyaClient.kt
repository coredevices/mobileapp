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
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds

@Serializable
data class RunResponse(
    val success: Boolean,
    val message: String? = null,
    val conversation: List<OpenAIConversationMessage> = emptyList(),
    val language_model_used: String? = null
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
    fun toConversationMessage(model: String? = null) = ConversationMessageDocument(
        role = role,
        content = content
            ?.filter { it.type == ContentPartType.text }
            ?.ifEmpty { null }
            ?.joinToString("\n") { it.text ?: "" },
        tool_calls = tool_calls,
        tool_call_id = tool_call_id,
        language_model_used = model,
    )
}

data class RunResult(val statusCode: HttpStatusCode, val response: RunResponse?)

/** Language model selection, mirrors nenya's `model_type` values. */
@Serializable
enum class NenyaModel {
    @SerialName("default") Default,
    @SerialName("search") Search,
    //@SerialName("high_capability") HighCapability,
}

@Serializable
private data class RunRequestData(
    val device_tool_specifications: List<ToolDeclaration>,
    val conversation_history: List<ConversationMessageDocument>,
    val additional_context: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val model_type: NenyaModel? = null,
)

/** A question for nenya's `/v2/decision`, in Jev wire form. */
@Serializable
sealed interface DecisionQuestion {
    /** Yes/no question; [yes] and [no] optionally describe what counts as each outcome. */
    @Serializable
    @SerialName("noul")
    data class Noul(
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val instructions: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val criteria: Criteria? = null,
    ) : DecisionQuestion {
        @Serializable
        data class Criteria(
            @SerialName("true")
            @EncodeDefault(EncodeDefault.Mode.NEVER)
            val yes: String? = null,
            @SerialName("false")
            @EncodeDefault(EncodeDefault.Mode.NEVER)
            val no: String? = null,
        )
    }

    /** Picks one of the [criteria] keys; each value optionally describes when that choice applies. */
    @Serializable
    @SerialName("choice")
    data class Choice(
        val criteria: Map<String, String?>,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val instructions: String? = null,
    ) : DecisionQuestion

    /** Rates against ordered level descriptions; a level's index is its score. */
    @Serializable
    @SerialName("score")
    data class Score(
        val criteria: List<String>,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val instructions: String? = null,
    ) : DecisionQuestion
}

@Serializable
sealed interface DecisionAnswer {
    /** [probability] of a yes answer, 0 to 1. */
    @Serializable
    @SerialName("noul")
    data class Noul(@SerialName("noul") val probability: Double) : DecisionAnswer

    @Serializable
    @SerialName("choice")
    data class Choice(
        val choice: String,
        val confidence: Double,
        val probabilities: Map<String, Double>,
    ) : DecisionAnswer

    /** [score] is the probability-weighted average level, so it can fall between levels. */
    @Serializable
    @SerialName("score")
    data class Score(
        val score: Double,
        val confidence: Double,
        val legend: Map<Int, String>,
        val probabilities: Map<Int, Double>,
    ) : DecisionAnswer
}

@Serializable
data class DecisionUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
)

@Serializable
data class DecisionResponse(
    val success: Boolean,
    val message: String? = null,
    val answers: Map<String, DecisionAnswer> = emptyMap(),
    val model: String? = null,
    val usage: DecisionUsage? = null,
)

data class DecisionResult(val statusCode: HttpStatusCode, val response: DecisionResponse?)

@Serializable
internal data class DecisionRequestData(
    val state: JsonElement,
    val questions: Map<String, DecisionQuestion>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val model: String? = null,
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
     * @param model The language model to use for the run.
     * @return The result of the run.
     */
    suspend fun run(
        conversationHistory: List<ConversationMessageDocument> = emptyList(),
        toolSpecs: List<ToolDeclaration> = emptyList(),
        additionalContext: String = "",
        timezone: TimeZone = TimeZone.currentSystemDefault(),
        model: NenyaModel = NenyaModel.Default,
    ): RunResult

    /**
     * Ask fast structured questions about [state] (text, or a JSON object/array).
     * @param questions The questions, keyed by the name their answer is returned under.
     * @param model Jev model override, or null for the server default.
     */
    suspend fun decide(
        state: JsonElement,
        questions: Map<String, DecisionQuestion>,
        model: String? = null,
    ): DecisionResult
}

suspend fun NenyaClient.decide(
    state: String,
    questions: Map<String, DecisionQuestion>,
    model: String? = null,
): DecisionResult = decide(JsonPrimitive(state), questions, model)

class NenyaClientImpl(config: ApiConfig): NenyaClient, ApiClient(config.version) {
    private val baseUrl = config.nenyaUrl
    private val logger = Logger.withTag("NenyaClientImpl")

    override suspend fun run(
        conversationHistory: List<ConversationMessageDocument>,
        toolSpecs: List<ToolDeclaration>,
        additionalContext: String,
        timezone: TimeZone,
        model: NenyaModel
    ): RunResult {
        val resp = postWithRetry(
            "/v2/run",
            RunRequestData(
                device_tool_specifications = toolSpecs,
                conversation_history = conversationHistory,
                additional_context = additionalContext,
                model_type = model.takeIf { it != NenyaModel.Default }
            )
        )
        if (resp?.status?.isSuccess() == true) {
            return RunResult(resp.status, resp.body())
        }
        logger.e { "Failed to run agent: ${resp?.status}" }
        return RunResult(
            statusCode = resp?.status ?: HttpStatusCode.InternalServerError,
            response = null
        )
    }

    override suspend fun decide(
        state: JsonElement,
        questions: Map<String, DecisionQuestion>,
        model: String?,
    ): DecisionResult {
        // 4xx means the request itself is invalid, so only server errors are retried.
        val resp = postWithRetry("/v2/decision", DecisionRequestData(state, questions, model)) { it.value >= 500 }
        if (resp?.status?.isSuccess() == true) {
            return DecisionResult(resp.status, resp.body())
        }
        logger.e { "Failed to get decision: ${resp?.status}" }
        return DecisionResult(
            statusCode = resp?.status ?: HttpStatusCode.InternalServerError,
            response = null
        )
    }

    /** Returns the last response received (successful or not), or null if every attempt threw. */
    private suspend inline fun <reified T> postWithRetry(
        path: String,
        body: T,
        retryOn: (HttpStatusCode) -> Boolean = { true },
    ): HttpResponse? {
        var retries = 0
        var resp: HttpResponse? = null
        while (retries < NenyaClient.RETRY_COUNT) {
            resp = try {
                client.post("$baseUrl$path") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    firebaseAuth()
                    setBody(body)
                }
            } catch (e: Exception) {
                retries++
                logger.w(e) { "Error while requesting $path, try: $retries" }
                if (retries >= NenyaClient.RETRY_COUNT && e is IOException) { // Rethrow network errors
                    throw e
                }
                delay(1.seconds)
                continue // Retry on failure
            }
            if (resp.status.isSuccess()) {
                return resp
            }
            retries++
            logger.w { "Received error response from $path (try $retries): ${resp.status}, body: ${resp.body<String>()}" }
            if (!retryOn(resp.status)) {
                return resp
            }
            delay(1.seconds)
        }
        return resp
    }
}