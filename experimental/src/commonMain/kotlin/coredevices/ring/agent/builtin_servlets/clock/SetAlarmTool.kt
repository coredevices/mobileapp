package coredevices.ring.agent.builtin_servlets.clock

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.toolschema.builtinToolDefinitions
import coredevices.toolschema.ToolDefinition
import coredevices.toolschema.ToolParam
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SetAlarmTool : BuiltInMcpTool(
    definition = builtinToolDefinitions.getValue(TOOL_NAME),
    extraContext = """
        Only call 'set_alarm' with a time the user actually said. If the alarm time is missing from the request, do not call this tool and do not guess a time.
    """.trimIndent()
) {

    companion object {
        private val logger = Logger.withTag(SetAlarmTool::class.simpleName!!)
        const val TOOL_NAME = "set_alarm"
        const val TOOL_DESCRIPTION = "Set an alarm for a specific clock time like 8am or 16:30."
    }

    @ToolDefinition(servlet = CLOCK_SERVLET_NAME, name = TOOL_NAME, description = TOOL_DESCRIPTION)
    @Serializable
    private data class SetAlarmArgs(
        @SerialName("time_hours")
        @ToolParam("The hour component of the alarm time (24 hour time)")
        val timeHours: Int,
        @SerialName("time_minutes")
        @ToolParam("The minute component of the alarm time (0-59)")
        val timeMinutes: Int,
        @ToolParam("An optional label or title for the alarm")
        val label: String? = null,
    )

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val setAlarmArgs = JsonSnake.decodeFromString<SetAlarmArgs>(jsonInput)
        return try {
            setAlarm(setAlarmArgs.timeHours, setAlarmArgs.timeMinutes, setAlarmArgs.label)
            val fireTime = LocalTime(hour = setAlarmArgs.timeHours, minute = setAlarmArgs.timeMinutes)
            ToolCallResult(
                "Alarm created",
                semanticResult = SemanticResult.AlarmCreation(fireTime = fireTime)
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