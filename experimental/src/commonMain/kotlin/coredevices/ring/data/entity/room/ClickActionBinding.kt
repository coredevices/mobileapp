package coredevices.ring.data.entity.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A user-configured binding from a run of short clicks on the ring to an action.
 *
 * Click-only sequences reach the app on a non-audio collection, so these actions have no
 * spoken input to work from — every action carries its own fixed configuration.
 */
@Entity(indices = [Index(value = ["clickCount"], unique = true)])
data class ClickActionBinding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clickCount: Int,
    val action: ClickAction,
    val enabled: Boolean = true,
) {
    companion object {
        const val MIN_CLICK_COUNT = 1

        /**
         * Runs longer than this are impractical: each press has to land inside
         * [coredevices.ring.service.IndexButtonSequenceRecorder.EVENT_DEBOUNCE_MS] of the last.
         */
        const val MAX_CLICK_COUNT = 8
        val CLICK_COUNT_RANGE = MIN_CLICK_COUNT..MAX_CLICK_COUNT
    }
}

@Serializable
sealed interface ClickAction {
    /**
     * Runs [text] through the normal agent pipeline via
     * [coredevices.ring.service.recordings.RecordingProcessingQueue.queueTextProcessing].
     * Text ending in '?' is routed to the search agent.
     */
    @Serializable
    @SerialName("agent_text")
    data class AgentText(val text: String) : ClickAction

    /**
     * Calls a single MCP tool directly with fixed [arguments]. No model is involved, so the
     * outcome is deterministic. Covers both built-in servlets and HTTP MCP servers — they are
     * the same dispatch path.
     */
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val integrationName: String,
        val toolName: String,
        val arguments: JsonObject = JsonObject(emptyMap()),
    ) : ClickAction

    /**
     * A stored action this build can't decode — typically written by a newer version, or by a
     * variant that has since been renamed. Kept as a value rather than failing the read so one
     * bad row can't break every query against the table; the binding is inert until re-created.
     */
    @Serializable
    @SerialName("unsupported")
    data object Unsupported : ClickAction
}
