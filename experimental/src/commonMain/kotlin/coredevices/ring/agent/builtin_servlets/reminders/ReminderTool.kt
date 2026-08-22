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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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
                            "description" to "If provided by the user, the date and/or time to remind the user in human readable format. Must be in English — translate it if the user spoke another language. e.g. 'tomorrow at 13:00', 'next Monday at 9am', 'on July 5th at 14:30', 'at 3pm'"
                        ).toJson()
                    ),
                    "duration_human" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "If provided by the user, the duration from now to remind the user in human readable format. Must be in English — translate it if the user spoke another language. e.g. 'in 2 hours', 'in 30 minutes', 'in 1 day and 3 hours'"
                        ).toJson()
                    ),
                    "notification_hours_before" to JsonObject(
                        mapOf(
                            "type" to "number",
                            "description" to "If the user requests to be reminded before the reminder time, set the number of hours here, for example 'before Monday' would be 24 hours notice, but 'before 3pm' would be 1 hour notice."
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
    ),
    extraContext = """
        Always pass the time the user said in date_time_human or duration_human. Example: 'remind me to call mom at five p.m.' → message='call mom', date_time_human='at 5pm'. Omit the time fields only when the user gave no time at all.
    """.trimIndent()
), KoinComponent {
    val reminderIntegrationFactory: ReminderIntegrationFactory by inject()

    companion object Companion {
        const val TOOL_NAME = "create_reminder"
        const val TOOL_DESCRIPTION = "Set a reminder optionally for a future time. Use when the user requests a reminder, asks to remember to do something ('remind me to...', 'remember to...'), or wants to be reminded at a specific time or date."
        private val logger = Logger.withTag("ReminderTool")
    }

    @Serializable
    private data class RemindArgs(
        val date_time_human: String? = null,
        val duration_human: String? = null,
        val notification_hours_before: Double? = null,
        val message: String
    )

    @Serializable
    data class RemindResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val reminderId: String? = null
    )

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val remindArgs = JsonSnake.decodeFromString<RemindArgs>(jsonInput)
        logger.i {
            "create_reminder message=${remindArgs.message} date_time_human=${remindArgs.date_time_human} duration_human=${remindArgs.duration_human}"
        }

        val instant = resolveDeadline(remindArgs, context)
        logger.i { "create_reminder resolved deadline=${instant?.toString()} epochMs=${instant?.toEpochMilliseconds()}" }

        val notifyBefore = instant?.let {
            remindArgs.notification_hours_before?.takeIf { hours -> hours > 0 }?.hours
        }

        return try {
            val reminderId = reminderIntegrationFactory.createReminderIntegration()
                .createReminder(remindArgs.message, instant, notifyBefore = notifyBefore, source = context.itemSource())
            ToolCallResult(
                JsonSnake.encodeToString(RemindResult(success = true, reminderId = reminderId)),
                SemanticResult.TaskCreation(
                    title = remindArgs.message,
                    deadline = instant,
                    localReminderId = reminderId.toIntOrNull(),
                    notifyBeforeMillis = notifyBefore?.inWholeMilliseconds,
                )
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to create reminder" }
            ToolCallResult(
                JsonSnake.encodeToString(
                    RemindResult(success = false, errorMessage = e.message)
                ),
                SemanticResult.GenericFailure(
                    "Failed to create reminder",
                    llmRecoverable = true
                )
            )
        }
    }

    /**
     * Prefer the dedicated time fields, then fall back to parsing the message itself
     * so "Remind me to call mom at five p.m." still gets a 5pm Once reminder when
     * the model stuffed the time into `message` and left `date_time_human` empty.
     */
    private fun resolveDeadline(args: RemindArgs, context: SessionContext): Instant? {
        val candidates = listOfNotNull(
            args.date_time_human?.takeIf { it.isNotBlank() },
            args.duration_human?.takeIf { it.isNotBlank() },
            args.message.takeIf { it.isNotBlank() },
        )
        for (candidate in candidates) {
            parseHumanDateTime(candidate, context)?.let { return it }
        }
        return null
    }

    private fun parseHumanDateTime(dateTimeHuman: String, context: SessionContext): Instant? {
        val tz = TimeZone.currentSystemDefault()
        val timeBase = context.timeBase
        val anchor = timeBase ?: Clock.System.now()
        val parser = HumanDateTimeParser(clock = anchor.asFrozenClock(), timeZone = tz)
        val parsed = parser.parse(dateTimeHuman) ?: return null
        if (timeBase == null && parsed is InterpretedDateTime.Relative) return null
        val local = when (parsed) {
            is InterpretedDateTime.AbsoluteDate -> {
                LocalDateTime(date = parsed.date, time = LocalTime(9, 0))
            }
            is InterpretedDateTime.AbsoluteDateTime -> parsed.dateTime
            is InterpretedDateTime.AbsoluteTime -> {
                val currentTime = anchor.toLocalDateTime(tz)
                if (parsed.time < currentTime.time) {
                    LocalDateTime(
                        date = currentTime.date.plus(DatePeriod(days = 1)),
                        time = parsed.time
                    )
                } else {
                    LocalDateTime(date = currentTime.date, time = parsed.time)
                }
            }
            is InterpretedDateTime.Relative -> {
                val currentTime = anchor
                val period = parsed.period
                if (period != null) {
                    val localNow = currentTime.toLocalDateTime(tz)
                    val newDate = localNow.date.plus(period)
                    (LocalDateTime(newDate, localNow.time).toInstant(tz) + parsed.duration)
                        .toLocalDateTime(tz)
                } else {
                    (currentTime + parsed.duration).toLocalDateTime(tz)
                }
            }
        }
        return local.toInstant(tz)
    }
}
