package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.integrations.itemSource
import coredevices.ring.agent.toolschema.builtinToolDefinitions
import coredevices.ring.database.room.repository.GeofenceLocationRepository
import coredevices.toolschema.ToolDefinition
import coredevices.toolschema.ToolParam
import coredevices.util.capitalizeFirstWord
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocationReminderTool(
    private val geofenceLocationRepository: GeofenceLocationRepository
): BuiltInMcpTool(
    definition = builtinToolDefinitions.getValue(TOOL_NAME),
), KoinComponent {
    private val reminders: BuiltInReminderIntegration by inject()

    @ToolDefinition(servlet = ReminderServlet.NAME, name = LocationReminderTool.TOOL_NAME, description = TOOL_DESCRIPTION)
    @Serializable
    private data class RemindArgs(
        @ToolParam("The location or place the user wants to be reminded when they arrive at")
        val place: String,
        @ToolParam("The message to remind the user e.g. 'Buy more milk'")
        val message: String,
    )

    @Serializable
    data class RemindResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val reminderId: String? = null
    )

    override suspend fun getExtraContext(): String = buildString {
        appendLine("When the user specifies to be reminded at a place in the list below and not a time, create a location reminder.")
        appendLine("<valid_reminder_places>")
        val locations = geofenceLocationRepository.getAllFlow().first()
        if (locations.isNotEmpty()) {
            locations.forEach {
                appendLine("<place>${it.name}</place>")
            }
        } else {
            appendLine("No places set up")
        }
        append("</valid_reminder_places>")
    }


    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val args = JsonSnake.decodeFromString<RemindArgs>(jsonInput)
            .let { it.copy(message = it.message.capitalizeFirstWord()) }
        val locations = geofenceLocationRepository.getAllFlow().first()
        val location = locations.firstOrNull { it.name.trim().equals(args.place.trim(), ignoreCase = true) }
            ?: return ToolCallResult(
                JsonSnake.encodeToString(
                    RemindResult(
                        success = false,
                        errorMessage = "Unknown place '${args.place}'. Valid places: " +
                            locations.joinToString { "'${it.name}'" }.ifEmpty { "none set up" } + "."
                    )
                ),
                SemanticResult.GenericFailure("Unknown place '${args.place}'", llmRecoverable = locations.isNotEmpty())
            )

        return try {
            val reminderId = reminders.createGeofenceReminder(args.message, location, context.itemSource())
            ToolCallResult(
                JsonSnake.encodeToString(RemindResult(success = true, reminderId = reminderId)),
                SemanticResult.TaskCreation(title = args.message, deadline = null)
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to create location reminder" }
            ToolCallResult(
                JsonSnake.encodeToString(RemindResult(success = false, errorMessage = e.message)),
                SemanticResult.GenericFailure(e.message)
            )
        }
    }

    companion object {
        const val TOOL_NAME = "create_location_reminder"
        const val TOOL_DESCRIPTION = "Set a reminder that notifies the user when they arrive at a preset place."
        private val logger = Logger.withTag("LocationReminderTool")
    }
}