package coredevices.mcp.data

import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The result of a tool call, including the raw JSON result and any semantic interpretation
 * that can be derived from it.
 * @param resultString The raw text or JSON string returned by the tool, to be sent to the LLM
 * @param semanticResult An optional semantic interpretation of the tool result for display or further processing
 */
@Serializable
data class ToolCallResult(
    val resultString: String,
    val semanticResult: SemanticResult?,
)

@Serializable
sealed class SemanticResult {
    /** Feed item this result created, when one exists. Null for results that create no item and
     *  for records written before the id was recorded. */
    open val itemId: String? get() = null

    /**
     * Tool call resulted in a task creation action (e.g., to-do item, reminder)
     * @param destination Display name of the external provider the task was sent to, or null
     * when it was stored locally
     */
    @Serializable
    @SerialName("TaskCreation")
    data class TaskCreation(
        val title: String,
        val deadline: Instant?,
        /** Platform-local reminder id when this task is a scheduled reminder, used to
         *  link the produced feed item back to the local reminder. Null for old
         *  records / non-reminder tasks. */
        val localReminderId: Int? = null,
        /** Lead time (ms) before [deadline] at which an extra early notification was
         *  requested, or null for none. */
        val notifyBeforeMillis: Long? = null,
        val destination: String? = null,
        override val itemId: String? = null,
    ): SemanticResult()
    /**
     * Tool call resulted in a list item creation action (e.g. generic note, list)
     * @param destination Display name of the external provider the item was sent to, or null
     * when it was stored locally
     */
    @Serializable
    @SerialName("ListItemCreation")
    data class ListItemCreation(
        val content: String,
        val listUsed: String? = null,
        val remindAt: Instant? = null,
        val resolvedListId: String? = null,
        val destination: String? = null,
        override val itemId: String? = null,
    ): SemanticResult()
    /**
     * Tool call resulted in an alarm creation action
     */
    @Serializable
    @SerialName("AlarmCreation")
    data class AlarmCreation(val fireTime: LocalTime, override val itemId: String? = null): SemanticResult()
    /**
     * Tool call created an event in the user's calendar
     */
    @Serializable
    @SerialName("CalendarEventCreation")
    data class CalendarEventCreation(
        val title: String,
        val startTime: Instant,
        val endTime: Instant,
        val location: String? = null,
    ): SemanticResult()
    /**
     * Tool call resulted in a timer creation action
     * @param requestedDuration The relative duration requested by the user, if any
     * @param fireTime The exact time the timer is set to go off
     */
    @Serializable
    @SerialName("TimerCreation")
    data class TimerCreation(
        val requestedDuration: Duration?,
        val fireTime: Instant,
        override val itemId: String? = null,
    ): SemanticResult()
    /**
     * Tool call provided supporting data for the LLM to use (e.g. reading a plugin source).
     * Never shown to the user as an outcome; answers are tagged assistant messages.
     * @param summary A brief summary of the supporting data
     */
    @Serializable
    @SerialName("SupportingData")
    data class SupportingData(
        val summary: String?,
        override val itemId: String? = null,
    ): SemanticResult()
    /**
     * Tool call resulted in a message being sent to a contact
     * @param recipientName The display name of the message recipient
     * @param text The message body that was sent
     * @param contactId The id of the contact/room the message was sent to
     */
    @Serializable
    @SerialName("MessageSent")
    data class MessageSent(
        val recipientName: String,
        val text: String = "",
        val contactId: String = "",
        override val itemId: String? = null,
    ): SemanticResult()
    /**
     * Tool call performed an action that should be logged in the feed (e.g. running JavaScript)
     * @param toolName The name of the tool that ran
     * @param title A short human-readable title for the action
     * @param success Whether the action succeeded
     * @param body Optional detail (e.g. the code that ran)
     */
    @Serializable
    @SerialName("ActionLogged")
    data class ActionLogged(
        val toolName: String,
        val title: String,
        val success: Boolean,
        val body: String = "",
        override val itemId: String? = null,
    ): SemanticResult()
    /**
     * The tool was not run: it needs the user's go-ahead first. The tool call stays open until
     * the user answers, when a second result for the same call (the real one) is recorded.
     * Never shown to the model.
     * @param integrationName Integration the tool belongs to, for re-dispatching the call
     * @param arguments The call's parameters, for the user to review
     * @param destructive Whether the action cannot be undone
     */
    @Serializable
    @SerialName("RequiresConfirmation")
    data class RequiresConfirmation(
        val integrationName: String,
        val toolName: String,
        val arguments: JsonObject,
        val destructive: Boolean,
        val description: String? = null,
    ): SemanticResult()
    /** The user refused a [RequiresConfirmation] request, so the tool was not run. */
    @Serializable
    @SerialName("UserDenied")
    data object UserDenied: SemanticResult()
    /**
     * Generic success or failure without additional context
     */
    @Serializable
    @SerialName("GenericSuccess")
    data object GenericSuccess: SemanticResult()
    /**
     * Generic failure, with an optional user-facing error message
     * @param userErrorMessage An optional message to show to the user explaining the failure
     * @param llmRecoverable Whether the LLM can attempt to recover from this failure
     * @param forceFallbackTool Whether the system should attempt to use a fallback tool if available
     */
    @Serializable
    @SerialName("GenericFailure")
    data class GenericFailure(
        val userErrorMessage: String?,
        val llmRecoverable: Boolean = false,
        val forceFallbackTool: Boolean = false,
    ): SemanticResult()
}

/** This result with [itemId] recorded; results that create no item are returned unchanged. */
fun SemanticResult.withItemId(itemId: String): SemanticResult = when (this) {
    is SemanticResult.TaskCreation -> copy(itemId = itemId)
    is SemanticResult.ListItemCreation -> copy(itemId = itemId)
    is SemanticResult.AlarmCreation -> copy(itemId = itemId)
    is SemanticResult.TimerCreation -> copy(itemId = itemId)
    is SemanticResult.MessageSent -> copy(itemId = itemId)
    is SemanticResult.ActionLogged -> copy(itemId = itemId)
    is SemanticResult.CalendarEventCreation,
    is SemanticResult.SupportingData,
    is SemanticResult.RequiresConfirmation,
    is SemanticResult.UserDenied,
    is SemanticResult.GenericSuccess,
    is SemanticResult.GenericFailure -> this
}
