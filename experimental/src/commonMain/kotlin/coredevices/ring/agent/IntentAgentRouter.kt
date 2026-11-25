package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.Agent
import coredevices.mcp.client.McpSession
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Picks the agent for a recording from what [classifier] makes of its text.
 * @param sourcePlugins The plugins whose data sources the assistant may read.
 */
class IntentAgentRouter(
    private val classifier: RecordingIntentClassifier,
    private val sourcePlugins: suspend () -> List<PluginMetadata>,
    private val searchAgent: () -> Agent,
) {
    /**
     * [default], the search agent for a question only a web search can answer, or a
     * [CaptureOnlyAgent] for a plain note. Stays on [default] when it runs on-device or the
     * intent can't be had.
     * @param canCapture The caller saves [text] itself when the agent takes no action.
     */
    suspend fun route(default: Agent, text: String, mcpSession: McpSession, canCapture: Boolean): Agent {
        if (!default.isCloud) return default
        val intent = try {
            mcpSession.openSession()
            try {
                withTimeoutOrNull(CLASSIFY_TIMEOUT) { classifier.classify(text, mcpSession, sourcePlugins()) }
            } finally {
                mcpSession.closeSession()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Couldn't classify recording, staying on ${default.label}: ${e.message}" }
            return default
        }
        return when (intent) {
            null -> {
                logger.w { "Classifying recording took over $CLASSIFY_TIMEOUT, staying on ${default.label}" }
                default
            }
            RecordingIntent.SearchQuestion -> searchAgent()
            RecordingIntent.Note -> if (canCapture) CaptureOnlyAgent() else default
            RecordingIntent.ToolQuestion,
            RecordingIntent.Other -> default
        }
    }

    companion object {
        private val logger = Logger.withTag("IntentAgentRouter")
        internal val CLASSIFY_TIMEOUT = 5.seconds
    }
}
