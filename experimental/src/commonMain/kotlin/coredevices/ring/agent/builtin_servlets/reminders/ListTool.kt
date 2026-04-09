package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.indexai.time.HumanDateTimeParser
import coredevices.indexai.time.InterpretedDateTime
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import kotlin.time.Clock

class ListTool: BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "list_name" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The name of the list to add the item to e.g. 'shopping', 'todo'. " +
                                    "Use a short search term keyword, e.g. 'shopping' instead of 'my shopping list' to improve matching with existing lists."
                        ).toJson()
                    ),
                    "message" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The text of the list item to add"
                        ).toJson()
                    ),
                    "reminder_date_time_human" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "If provided by the user, the date and/or time to remind the user of the list item in human readable format e.g. 'tomorrow at 13:00'"
                        ).toJson()
                    ),
                )
            ),
            required = listOf(
                "list_name",
                "message"
            )
        )
    )
), KoinComponent {
    val reminderFactory: ReminderFactory by inject()

    companion object Companion {
        const val TOOL_NAME = "create_list_item"
        const val TOOL_DESCRIPTION = "Create a new item in the user's list (e.g a shopping list, todo list) with an optional reminder time"
        private val logger = Logger.withTag(ReminderTool::class.simpleName!!)
    }

    @Serializable
    private data class ListItemArgs(
        val list_name: String,
        val reminder_date_time_human: String? = null,
        val message: String
    )

    @Serializable
    data class ListAddResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val id: String? = null
    )

    override suspend fun call(jsonInput: String): ToolCallResult {
        val listItemArgs = JsonSnake.decodeFromString<ListItemArgs>(jsonInput)
        val instant = listItemArgs.reminder_date_time_human?.let {
            val tz = TimeZone.currentSystemDefault()
            val parser = HumanDateTimeParser(timeZone = tz)
            val parsed = parser.parse(listItemArgs.reminder_date_time_human)
            when (parsed) {
                is InterpretedDateTime.AbsoluteDate -> {
                    logger.d { "Parsed absolute date: $parsed will assume 9am" }
                    LocalDateTime(
                        date = parsed.date,
                        time = kotlinx.datetime.LocalTime(9, 0)
                    )
                }
                is InterpretedDateTime.AbsoluteDateTime -> {
                    logger.d { "Parsed absolute date time: $parsed" }
                    parsed.dateTime
                }
                is InterpretedDateTime.AbsoluteTime -> {
                    logger.d { "Parsed absolute time: $parsed" }
                    val currentTime = Clock.System.now().toLocalDateTime(tz)
                    if (parsed.time < currentTime.time) {
                        // If the time has already passed today, assume it's for tomorrow
                        logger.d { "Parsed time has already passed today, assuming it's for tomorrow" }
                        LocalDateTime(
                            date = currentTime.date.plus(DatePeriod(days = 1)),
                            time = parsed.time
                        )
                    } else {
                        logger.d { "Parsed time has not passed today, assuming it's for today" }
                        LocalDateTime(
                            date = currentTime.date,
                            time = parsed.time
                        )
                    }
                }
                is InterpretedDateTime.Relative -> {
                    logger.d { "Parsed relative date time: $parsed" }
                    val currentTime = Clock.System.now()
                    (currentTime + parsed.duration).toLocalDateTime(tz)
                }
                null -> {
                    logger.e { "Failed to parse date time: '${listItemArgs.reminder_date_time_human}'" }
                    return ToolCallResult(
                        JsonSnake.encodeToString(
                            ListAddResult(
                                success = false,
                                errorMessage = "Failed to parse date time: '${listItemArgs.reminder_date_time_human}'"
                            )
                        ),
                        SemanticResult.GenericFailure(
                            "Failed to parse time",
                            llmRecoverable = true
                        )
                    )
                }
            }.toInstant(tz)
        }

        val reminder = reminderFactory.create(
            time = instant,
            message = listItemArgs.message
        )
        return try {
            val reminderId = if (reminder is ListAssignableReminder) {
                reminder.scheduleToList(listItemArgs.list_name)
            } else {
                reminder.schedule()
            }
            ToolCallResult(
                JsonSnake.encodeToString(ListAddResult(success = true, id = reminderId)),
                if ((reminder as? ListAssignableReminder)?.listTitle != null) {
                    SemanticResult.ListItemCreation(
                        content = reminder.message,
                        listUsed = reminder.listTitle,
                        remindAt = reminder.time
                    )
                } else {
                    SemanticResult.TaskCreation(
                        title = reminder.message,
                        deadline = reminder.time
                    )
                }
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to create reminder" }
            ToolCallResult(
                JsonSnake.encodeToString(
                    ListAddResult(
                        success = false,
                        errorMessage = e.message
                    )
                ),
                SemanticResult.GenericFailure("Failed to create reminder: ${e.message}")
            )
        }
    }
}