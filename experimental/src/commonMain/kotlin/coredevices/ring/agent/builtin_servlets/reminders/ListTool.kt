package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.indexai.time.HumanDateTimeParser
import coredevices.indexai.time.InterpretedDateTime
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.asFrozenClock
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.integrations.itemSource
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.database.room.repository.ListRepository
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.coroutines.flow.first
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
    val reminderIntegrationFactory: ReminderIntegrationFactory by inject()
    private val listRepo: ListRepository by inject()

    companion object Companion {
        const val TOOL_NAME = "create_list_item"
        const val TOOL_DESCRIPTION = "Add an item to a shopping list, grocery list, or to-do list. Use when the user names a list or wants to buy groceries, food, or household supplies."
        private val logger = Logger.withTag(ReminderTool::class.simpleName!!)

        /**
         * Resolves a list-name hint from the model to a list's firestoreId.
         * Prefers a title match (covers user-renamed and custom lists), then
         * falls back to the stable seed marker so built-in lists keep resolving
         * by their original keyword after a rename. The model is still prompted
         * to use 'todo', which no longer matches the renamed "Reminders" title
         * but does match its unchanged seed ("todos").
         */
        fun matchListIdByHint(lists: List<CachedList>, hint: String): String? {
            val normalized = hint.trim().lowercase()
            if (normalized.isEmpty()) return null
            return lists.firstOrNull { it.title.lowercase().contains(normalized) }?.firestoreId
                ?: lists.firstOrNull { it.seed?.lowercase()?.contains(normalized) == true }?.firestoreId
        }
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

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val listItemArgs = JsonSnake.decodeFromString<ListItemArgs>(jsonInput)
        val instant = listItemArgs.reminder_date_time_human?.let {
            val tz = TimeZone.currentSystemDefault()
            // Anchor time resolution to when the user actually spoke. When that's unknown, only
            // absolute times can fall back to the current clock; relative ones are refused.
            val timeBase = context.timeBase
            val anchor = timeBase ?: Clock.System.now()
            val parser = HumanDateTimeParser(clock = anchor.asFrozenClock(), timeZone = tz)
            val parsed = parser.parse(listItemArgs.reminder_date_time_human)
            if (timeBase == null && parsed is InterpretedDateTime.Relative) {
                return ToolCallResult(
                    JsonSnake.encodeToString(
                        ListAddResult(
                            success = false,
                            errorMessage = "Cannot resolve relative time '$it': the recording's " +
                                    "original time is unknown. Use an absolute time, or create " +
                                    "the item without a reminder time."
                        )
                    ),
                    SemanticResult.GenericFailure(
                        "Couldn't determine when the recording was made",
                        llmRecoverable = true
                    )
                )
            }
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
                    val currentTime = anchor.toLocalDateTime(tz)
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
                    (anchor + parsed.duration).toLocalDateTime(tz)
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

        return try {
            val integration = reminderIntegrationFactory.createReminderIntegration()
            val list = integration.searchForList(listItemArgs.list_name).firstOrNull()
            val reminderId = integration.createReminder(
                listItemArgs.message,
                instant,
                listId = list?.id,
                source = context.itemSource(),
            )
            val resolvedListId = runCatching { resolveListIdByHint(listItemArgs.list_name) }.getOrNull()
            ToolCallResult(
                JsonSnake.encodeToString(ListAddResult(success = true, id = reminderId)),
                SemanticResult.ListItemCreation(
                    content = listItemArgs.message,
                    listUsed = list?.title,
                    remindAt = instant,
                    resolvedListId = resolvedListId,
                )
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

    private suspend fun resolveListIdByHint(hint: String): String? =
        matchListIdByHint(listRepo.getAllFlow().first(), hint)
}