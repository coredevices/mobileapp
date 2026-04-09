package coredevices.ring.agent.builtin_servlets.clock

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

class SetAlarmTool : BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "time_hours" to JsonObject(
                        mapOf(
                            "type" to "number",
                            "description" to "The hour component of the alarm time (24 hour time)"
                        ).toJson()
                    ),
                    "time_minutes" to JsonObject(
                        mapOf(
                            "type" to "number",
                            "description" to "The minute component of the alarm time (0-59)"
                        ).toJson()
                    ),
                    "label" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "An optional label or title for the alarm"
                        ).toJson()
                    ),
                )
            ),
            required = listOf("time_hours", "time_minutes")
        )
    )
) {
    companion object {
        private val logger = Logger.withTag(SetAlarmTool::class.simpleName!!)
        const val TOOL_NAME = "set_alarm"
        const val TOOL_DESCRIPTION = "Set an alarm for a specified time"
    }

    @Serializable
    private data class SetAlarmArgs(
        @SerialName("time_hours")
        val timeHours: Int,
        @SerialName("time_minutes")
        val timeMinutes: Int,
        val label: String? = null
    )

    override suspend fun call(jsonInput: String): ToolCallResult {
        val setAlarmArgs = JsonSnake.decodeFromString<SetAlarmArgs>(jsonInput)
        return try {
            setAlarm(setAlarmArgs.timeHours, setAlarmArgs.timeMinutes, setAlarmArgs.label)
            ToolCallResult(
                "Alarm created",
                semanticResult = SemanticResult.AlarmCreation(
                    fireTime = LocalTime(
                        hour = setAlarmArgs.timeHours,
                        minute = setAlarmArgs.timeMinutes
                    )
                )
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to set alarm via tool" }
            ToolCallResult(
                "Failed to set alarm: ${e.message}",
                semanticResult = SemanticResult.GenericFailure("Failed to set alarm: ${e.message}")
            )
        }
    }
}

expect suspend fun setAlarm(hours: Int, minutes: Int, label: String?)