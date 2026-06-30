package coredevices.mcp.data

import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    /**
     * Tool call resulted in a task creation action (e.g., to-do item, reminder)
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
    ): SemanticResult()
    /**
     * Tool call resulted in a list item creation action (e.g. generic note, list)
     */
    @Serializable
    @SerialName("ListItemCreation")
    data class ListItemCreation(val content: String, val listUsed: String? = null, val remindAt: Instant? = null, val resolvedListId: String? = null): SemanticResult()
    /**
     * Tool call resulted in an alarm creation action
     */
    @Serializable
    @SerialName("AlarmCreation")
    data class AlarmCreation(val fireTime: LocalTime): SemanticResult()
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
    data class TimerCreation(val requestedDuration: Duration?, val fireTime: Instant): SemanticResult()
    /**
     * Tool call provided supporting data for the LLM to use (e.g. JS eval, search)
     * @param summary A brief summary of the supporting data
     * @param assistiveOnly Whether the data is only to be used for assistive purposes
     * @param question User or LLM query
     * (e.g. informing future actions) and not for direct response generation
     */
    @Serializable
    @SerialName("SupportingData")
    data class SupportingData(
        val summary: String?,
        val assistiveOnly: Boolean = false,
        val question: String? = null,
    ): SemanticResult()
    /**
     * The agent replied with a message as its main action. Creates no item;
     * surfaced directly (e.g. in the completion notification)
     * @param text The reply body
     * @param question The user query the reply answers
     */
    @Serializable
    @SerialName("Response")
    data class Response(val text: String, val question: String? = null): SemanticResult()
    /**
     * Tool call resulted in a message being sent to a contact
     * @param recipientName The display name of the message recipient
     * @param text The message body that was sent
     * @param contactId The id of the contact/room the message was sent to
     */
    @Serializable
    @SerialName("MessageSent")
    data class MessageSent(val recipientName: String, val text: String = "", val contactId: String = ""): SemanticResult()
    /**
     * Tool call performed an action that should be logged in the feed (e.g. running JavaScript)
     * @param toolName The name of the tool that ran
     * @param title A short human-readable title for the action
     * @param success Whether the action succeeded
     * @param body Optional detail (e.g. the code that ran)
     */
    @Serializable
    @SerialName("ActionLogged")
    data class ActionLogged(val toolName: String, val title: String, val success: Boolean, val body: String = ""): SemanticResult()
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