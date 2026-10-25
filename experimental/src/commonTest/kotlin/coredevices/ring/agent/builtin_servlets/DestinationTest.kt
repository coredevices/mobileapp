package coredevices.ring.agent.builtin_servlets

import coredevices.ring.agent.builtin_servlets.reminders.ListTool
import coredevices.ring.agent.builtin_servlets.reminders.ReminderTool
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationTest {
    @Test
    fun withoutDestinationDropsOnlyThatProperty() {
        val tool = Tool(
            name = "t",
            inputSchema = ToolSchema(
                properties = JsonObject(mapOf("message" to JsonObject(emptyMap()), DESTINATION_ARG to destinationProperty("thing"))),
                required = listOf("message"),
            ),
        )

        val local = tool.withoutDestination()

        assertEquals(setOf("message"), local.inputSchema.properties?.keys)
        assertEquals(listOf("message"), local.inputSchema.required)
        assertEquals(tool.copy(inputSchema = local.inputSchema), local)
    }

    @Test
    fun destinationToolsHideTheArgumentFromTheLocalModel() {
        listOf(ReminderTool(), ListTool()).forEach { tool ->
            val cloud = tool.definition.inputSchema.properties?.keys.orEmpty()
            val local = tool.localDefinition.inputSchema.properties?.keys.orEmpty()
            assertEquals(cloud - DESTINATION_ARG, local, tool.definition.name)
            assertEquals(true, DESTINATION_ARG in cloud, tool.definition.name)
        }
    }
}
