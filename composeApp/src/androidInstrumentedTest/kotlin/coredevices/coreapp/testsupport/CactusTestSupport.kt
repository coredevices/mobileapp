package coredevices.coreapp.testsupport

import coredevices.analytics.CoreAnalytics
import coredevices.util.transcription.CactusModelPathProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Read-only view of the already-downloaded model directory for on-device Cactus tests. Unlike the
 * production provider it never downloads or deletes, so running the tests can't wipe the on-device model.
 */
class ReadOnlyModelPathProvider(
    private val modelsDir: File,
    private val sttModelName: String,
) : CactusModelPathProvider {
    override suspend fun getSTTModelPath(): String = modelsDir.resolve(sttModelName).absolutePath
    override suspend fun getLMModelPath(): String = error("LM model not served via ReadOnlyModelPathProvider")
    override fun isModelDownloaded(modelName: String): Boolean =
        modelsDir.resolve(modelName).resolve("config.txt").exists()
    override fun getDownloadedModels(): List<String> =
        modelsDir.listFiles()?.filter { it.resolve("config.txt").exists() }?.map { it.name } ?: emptyList()
    override fun getIncompatibleModels(): List<String> = emptyList()
    override fun deleteModel(modelName: String) { /* never delete in tests */ }
    override fun getModelSizeBytes(modelName: String): Long = 0L
    override fun initTelemetry() {}
}

/** No-op analytics for on-device Cactus tests. */
object NoopAnalytics : CoreAnalytics {
    override fun logEvent(name: String, parameters: Map<String, Any>?) {}
    override suspend fun logHeartbeatState(name: String, value: Boolean, timestamp: Instant) {}
    override suspend fun processHeartbeat() {}
    override fun updateLastConnectedSerial(serial: String?) {}
    override fun updateRingTransferDurationMetric(duration: Duration) {}
    override fun updateRingLifetimeCollectionCount(serial: String, count: Int) {}
}

/**
 * The tool set [coredevices.ring.agent.IndexAgentCactus] exposes to needle (calendar is excluded on
 * device — the local model isn't trained on it), plus helpers to run and inspect needle tool-call
 * inference in tests. Descriptions mirror the app's servlet definitions (what needle was trained on).
 */
object NeedleTestTools {
    val TOOLS_JSON = """
        [
          {"type":"function","function":{"name":"create_note","description":"Create a new note with the given text.","parameters":{"type":"object","properties":{"text":{"type":"string","description":"The text of the note."}},"required":["text"]}}},
          {"type":"function","function":{"name":"set_alarm","description":"Set an alarm at a clock time like 7am or 6:30.","parameters":{"type":"object","properties":{"time_hours":{"type":"number","description":"Hour in 24h format."},"time_minutes":{"type":"number","description":"Minute, 0-59."},"label":{"type":"string","description":"Optional label."}},"required":["time_hours","time_minutes"]}}},
          {"type":"function","function":{"name":"set_timer","description":"Set a countdown timer for a duration like 20 minutes.","parameters":{"type":"object","properties":{"time_human":{"type":"string","description":"Duration in human readable form."}},"required":["time_human"]}}},
          {"type":"function","function":{"name":"send_instant_message","description":"Send a text message to a contact by name.","parameters":{"type":"object","properties":{"contact_name":{"type":"string","description":"The person's name."},"text":{"type":"string","description":"The message text."}},"required":["contact_name","text"]}}},
          {"type":"function","function":{"name":"create_reminder","description":"Set a reminder. Only use when the user specifies a time or date.","parameters":{"type":"object","properties":{"date_time_human":{"type":"string","description":"Date/time in human readable form."},"duration_human":{"type":"string","description":"Duration from now, e.g. 'in 2 hours', 'in 30 minutes'."},"message":{"type":"string","description":"The reminder message."}},"required":["message"]}}},
          {"type":"function","function":{"name":"create_list_item","description":"Add item to a named list like shopping, grocery, or todo.","parameters":{"type":"object","properties":{"list_name":{"type":"string","description":"Name of the list."},"message":{"type":"string","description":"The item to add."},"reminder_date_time_human":{"type":"string","description":"Optional reminder time."}},"required":["list_name","message"]}}}
        ]
    """.trimIndent()

    const val OPTIONS_JSON = """{"max_tokens":256,"temperature":0.0,"tool_rag_top_k":0}"""

    val LIST_NAMES = setOf("shopping", "grocery", "todo")

    /**
     * Parse a `cactusComplete` result into (toolName, argsObject) pairs. Tool-call arguments come back
     * either as a JSON object or a JSON-encoded string; both are handled.
     */
    fun parseCalls(cactusCompleteResult: String): List<Pair<String, JsonObject>> {
        val calls = Json.parseToJsonElement(cactusCompleteResult).jsonObject["function_calls"]?.jsonArray
            ?: return emptyList()
        return calls.map { it.jsonObject }.mapNotNull { call ->
            val name = call["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val args = call["arguments"]
            val obj = when (args) {
                is JsonObject -> args
                null -> JsonObject(emptyMap())
                else -> runCatching { Json.parseToJsonElement(args.jsonPrimitive.content).jsonObject }
                    .getOrDefault(JsonObject(emptyMap()))
            }
            name to obj
        }
    }

    /** Count create_list_item calls that target a real list and carry a non-blank item. */
    fun wellFormedListItems(calls: List<Pair<String, JsonObject>>): Int = calls.count { (name, args) ->
        name == "create_list_item" &&
            args["list_name"]?.jsonPrimitive?.contentOrNull in LIST_NAMES &&
            !args["message"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
    }
}
