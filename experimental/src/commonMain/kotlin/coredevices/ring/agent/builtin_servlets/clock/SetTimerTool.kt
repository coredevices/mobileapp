package coredevices.ring.agent.builtin_servlets.clock

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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SetTimerTool : BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "time_human" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The duration or target end time to set the timer for in human readable format e.g. '1 hour and 30 minutes', '45 minutes', 'for 1:50pm', 'at 13:30'",
                        ).toJson()
                    )
                )
            ),
            required = listOf("time_human")
        )
    ),
) {
    companion object {
        private val logger = Logger.withTag(SetTimerTool::class.simpleName!!)
        const val TOOL_NAME = "set_timer"
        const val TOOL_DESCRIPTION =
            "Set a timer for the specified duration or end time"

        fun interpretedTimeToFireTime(interpreted: InterpretedDateTime, now: Instant, tz: TimeZone): Instant {
            return when (interpreted) {
                is InterpretedDateTime.AbsoluteDate -> {
                    // assume 9am
                    LocalDateTime(
                        date = interpreted.date,
                        time = LocalTime(9, 0)
                    ).toInstant(tz)
                }
                is InterpretedDateTime.AbsoluteDateTime -> {
                    interpreted.dateTime.toInstant(tz)
                }
                is InterpretedDateTime.AbsoluteTime -> {
                    val todayDateTime = now.toLocalDateTime(tz)
                    if (interpreted.time >= todayDateTime.time) {
                        LocalDateTime(
                            date = todayDateTime.date,
                            time = interpreted.time
                        ).toInstant(tz)
                    } else {
                        // time has already passed today, set for tomorrow
                        val tomorrowDate = (now + 1.days).toLocalDateTime(tz).date
                        LocalDateTime(
                            date = tomorrowDate,
                            time = interpreted.time
                        ).toInstant(tz)
                    }
                }
                is InterpretedDateTime.Relative -> {
                    val period = interpreted.period
                    if (period != null) {
                        val local = now.toLocalDateTime(tz)
                        val newDate = local.date.plus(period)
                        LocalDateTime(newDate, local.time).toInstant(tz) + interpreted.duration
                    } else {
                        now + interpreted.duration
                    }
                }
            }
        }
    }

    @Serializable
    private data class SetTimerArgs(
        @SerialName("time_human")
        val timeHuman: String,
    )

    @Serializable
    private data class SetTimerResult(
        val success: Boolean,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @SerialName("error_message")
        val errorMessage: String? = null,
    )

    override suspend fun call(jsonInput: String): ToolCallResult {
        val setTimerArgs = JsonSnake.decodeFromString<SetTimerArgs>(jsonInput)
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val parser = HumanDateTimeParser()
        val fireTime = parser.parse(setTimerArgs.timeHuman)?.let { interpretedTimeToFireTime(it, now, tz) }
            ?: return ToolCallResult(
                JsonSnake.encodeToString(
                    SetTimerResult(
                        success = false,
                        errorMessage = "Could not parse time: '${setTimerArgs.timeHuman}'"
                    )
                ),
                SemanticResult.GenericFailure("Could not parse time: '${setTimerArgs.timeHuman}'", llmRecoverable = true)
            )
        val duration = fireTime - now
        if (duration.isNegative()) {
            return ToolCallResult(
                JsonSnake.encodeToString(
                    SetTimerResult(
                        success = false,
                        errorMessage = "Specified time is in the past"
                    )
                ),
                SemanticResult.GenericFailure("Specified time is in the past", llmRecoverable = true)
            )
        }
        return try {
            setTimer(duration)
            val fireTime = Clock.System.now() + duration
            ToolCallResult(
                JsonSnake.encodeToString(SetTimerResult(success = true)),
                SemanticResult.TimerCreation(duration, fireTime)
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to set timer via tool" }
            ToolCallResult(
                JsonSnake.encodeToString(SetTimerResult(success = false, errorMessage = e.message)),
                SemanticResult.GenericFailure("Failed to set timer: ${e.message}")
            )
        }
    }
}

expect suspend fun setTimer(duration: Duration, title: String? = null, skipUI: Boolean = true)
