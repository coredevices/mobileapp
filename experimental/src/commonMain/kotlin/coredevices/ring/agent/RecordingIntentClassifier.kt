package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.systemone.SystemOneAgent
import coredevices.indexai.agent.systemone.decide
import coredevices.mcp.client.McpSession
import coredevices.ring.agent.builtin_servlets.plugins.sourcesJson
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class RecordingIntent(internal val key: String, internal val criteria: String) {
    Note(
        "note",
        "A thought, idea, note to self, or piece of information to save as it is, with nothing to answer, " +
                "act upon from an assistant, or schedule",
    ),
    ToolQuestion(
        "tool_question",
        "A question or request that the tools listed in $STATE_TOOLS or the data sources listed in " +
            "$STATE_SOURCES can answer or act upon, " +
            "such as one about the user's own data in a listed service or a request for action from an assistant",
    ),
    SearchQuestion(
        "search_question",
        "A question that none of the tools listed in $STATE_TOOLS or the data sources listed in " +
            "$STATE_SOURCES can answer but a live internet " +
                "search can",
    ),
    Other(
        "other",
        "Anything else",
    ),
}

class RecordingIntentClassifier(private val systemOne: SystemOneAgent) {
    /**
     * @param sourcePlugins The plugins whose data sources the assistant may read.
     * @throws coredevices.indexai.agent.systemone.SystemOneException if no answer can be had; retrying won't help.
     * @throws kotlinx.io.IOException on network failure.
     */
    suspend fun classify(
        transcript: String,
        mcpSession: McpSession,
        sourcePlugins: List<PluginMetadata>,
    ): RecordingIntent {
        val state = buildJsonObject {
            put(STATE_RECORDING, transcript)
            put(STATE_TOOLS, mcpSession.summarizeTools())
            put(STATE_SOURCES, sourcePlugins.sourcesJson())
        }
        val answer = systemOne.decide(state) {
            choice(QUESTION, INSTRUCTIONS) {
                RecordingIntent.entries.forEach { it.key means it.criteria }
            }
        }.choice(QUESTION)
        val chosen = RecordingIntent.entries.firstOrNull { it.key == answer.choice }
        val (intent, reason) = when {
            answer.confidence < MIN_CONFIDENCE -> RecordingIntent.Note to "confidence below $MIN_CONFIDENCE"
            chosen == null -> RecordingIntent.Other to "unknown choice"
            else -> chosen to "model choice"
        }
        logger.i {
            "Classified as $intent ($reason): choice=${answer.choice} confidence=${answer.confidence} " +
                "probabilities=${answer.probabilities}"
        }
        return intent
    }

    companion object {
        private val logger = Logger.withTag("RecordingIntentClassifier")
        internal const val QUESTION = "user_intent"
        internal const val MIN_CONFIDENCE = 0.55
        private const val INSTRUCTIONS =
            "What is the voice recording in $STATE_RECORDING? " +
                "$STATE_TOOLS summarises the tools an assistant could use to answer it, and " +
                "$STATE_SOURCES lists the live data sources it could read."
    }
}

internal const val STATE_RECORDING = "user_recording"
internal const val STATE_TOOLS = "available_tools"
internal const val STATE_SOURCES = "available_data_sources"
