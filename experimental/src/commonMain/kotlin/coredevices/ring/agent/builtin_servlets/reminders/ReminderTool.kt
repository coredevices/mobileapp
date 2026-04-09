package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.indexai.time.HumanDateTimeParser
import coredevices.indexai.time.InterpretedDateTime
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.ui.isLocale24HourFormat
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

class ReminderTool: BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "date_time_human" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "If provided by the user, the date and/or time to remind the user in human readable format, use English keywords e.g. 'tomorrow at 13:00', 'next Monday at 9am', 'on July 5th at 14:30', 'at 3pm'"
                        ).toJson()
                    ),
                    "message" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The message to remind the user e.g. 'Buy more milk'"
                        ).toJson()
                    ),
                )
            ),
            required = listOf(
                "message"
            )
        )
    )
), KoinComponent {
    val reminderFactory: ReminderFactory by inject()

    companion object Companion {
        const val TOOL_NAME = "create_reminder"
        const val TOOL_DESCRIPTION = "Create a reminder for the user at a specific time"
        private val logger = Logger.withTag("ReminderTool")
    }

    @Serializable
    private data class RemindArgs(
        val date_time_human: String? = null,
        val message: String
    )

    @Serializable
    data class RemindResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val reminderId: String? = null
    )

    override suspend fun call(jsonInput: String): ToolCallResult {
        val remindArgs = JsonSnake.decodeFromString<RemindArgs>(jsonInput)
        val instant = remindArgs.date_time_human?.let {
            val tz = TimeZone.currentSystemDefault()
            val parser = HumanDateTimeParser(timeZone = tz)
            val parsed = parser.parse(remindArgs.date_time_human)
            when (parsed) {
                is InterpretedDateTime.AbsoluteDate -> {
                    logger.d { "Parsed absolute date: $parsed will assume 9am" }
                    LocalDateTime(
                        date = parsed.date,
                        time = LocalTime(9, 0)
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
                        val is12HourFormat = !isLocale24HourFormat()
                        if (is12HourFormat && parsed.time.hour in 1..11) {
                            // If the parsed time is in the past for today, and the locale is 12-hour format, there's a chance it was an AM/PM issue.
                            // For example, if it's currently 3pm and the user said "at 3", it might have been parsed as 3am which has already passed.
                            logger.d { "Parsed time is in the past and locale is 12-hour format, assuming it's an AM/PM parsing issue and adding 12 hours" }
                            val correctedTime = LocalTime(
                                hour = (parsed.time.hour + 12) % 24,
                                minute = parsed.time.minute,
                                second = parsed.time.second
                            )

                            if (correctedTime < currentTime.time) {
                                logger.d { "Corrected time is still in the past, assuming it's for tomorrow" }
                                LocalDateTime(
                                    date = currentTime.date.plus(DatePeriod(days = 1)),
                                    time = parsed.time
                                )
                            } else {
                                logger.d { "Corrected time is not in the past, assuming it's for today" }
                                LocalDateTime(
                                    date = currentTime.date,
                                    time = correctedTime
                                )
                            }
                        } else {
                            logger.d { "Parsed time has already passed today, assuming it's for tomorrow" }
                            LocalDateTime(
                                date = currentTime.date.plus(DatePeriod(days = 1)),
                                time = parsed.time
                            )
                        }
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
                    val period = parsed.period
                    if (period != null) {
                        val local = currentTime.toLocalDateTime(tz)
                        val newDate = local.date.plus(period)
                        (LocalDateTime(newDate, local.time).toInstant(tz) + parsed.duration).toLocalDateTime(tz)
                    } else {
                        (currentTime + parsed.duration).toLocalDateTime(tz)
                    }
                }
                null -> {
                    logger.e { "Failed to parse date time: '${remindArgs.date_time_human}'" }
                    return ToolCallResult(
                        JsonSnake.encodeToString(
                            RemindResult(
                                success = false,
                                errorMessage = "Failed to parse date time: '${remindArgs.date_time_human}'"
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
            message = remindArgs.message
        )
        return try {
            val reminderId = reminder.schedule()
            ToolCallResult(
                JsonSnake.encodeToString(RemindResult(success = true, reminderId = reminderId)),
                SemanticResult.TaskCreation(
                    title = reminder.message,
                    deadline = reminder.time
                )
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to create reminder" }
            ToolCallResult(
                JsonSnake.encodeToString(
                    RemindResult(
                        success = false,
                        errorMessage = e.message
                    )
                ),
                SemanticResult.GenericFailure("Failed to create reminder: ${e.message}")
            )
        }
    }
}