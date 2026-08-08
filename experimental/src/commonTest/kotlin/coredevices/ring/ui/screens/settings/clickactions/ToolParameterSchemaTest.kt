package coredevices.ring.ui.screens.settings.clickactions

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolParameterSchemaTest {

    private fun tool(properties: JsonObject, required: List<String> = emptyList()) = Tool(
        name = "test_tool",
        description = "A test tool",
        inputSchema = ToolSchema(properties = properties, required = required),
    )

    private val kitchenSink = tool(
        properties = buildJsonObject {
            put("room", buildJsonObject {
                put("type", "string")
                put("description", "Which room")
            })
            put("brightness", buildJsonObject { put("type", "integer") })
            put("fade", buildJsonObject { put("type", "number") })
            put("on", buildJsonObject { put("type", "boolean") })
            put("mode", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("warm"))
                    add(JsonPrimitive("cool"))
                })
            })
            put("scenes", buildJsonObject { put("type", "array") })
        },
        required = listOf("room", "on"),
    )

    @Test
    fun `maps schema types to field kinds`() {
        val byName = kitchenSink.parameterFields().associateBy { it.name }

        assertEquals(ToolParameterField.Kind.Text, byName.getValue("room").kind)
        assertEquals(ToolParameterField.Kind.Number, byName.getValue("brightness").kind)
        assertEquals(ToolParameterField.Kind.Number, byName.getValue("fade").kind)
        assertEquals(ToolParameterField.Kind.Bool, byName.getValue("on").kind)
        assertEquals(
            ToolParameterField.Kind.Choice(listOf("warm", "cool")),
            byName.getValue("mode").kind,
        )
        // Arrays have no single control, so they fall back to raw JSON rather than
        // making the tool unbindable.
        assertEquals(ToolParameterField.Kind.Json, byName.getValue("scenes").kind)
    }

    @Test
    fun `carries description and required flag`() {
        val byName = kitchenSink.parameterFields().associateBy { it.name }

        assertEquals("Which room", byName.getValue("room").description)
        assertTrue(byName.getValue("room").required)
        assertTrue(byName.getValue("on").required)
        assertTrue(!byName.getValue("brightness").required)
    }

    @Test
    fun `tool with no properties has no fields`() {
        assertEquals(emptyList(), tool(properties = JsonObject(emptyMap())).parameterFields())
    }

    @Test
    fun `buildArguments coerces each kind to its json type`() {
        val args = buildArguments(
            kitchenSink.parameterFields(),
            mapOf(
                "room" to "office",
                "brightness" to "80",
                "fade" to "1.5",
                "on" to "true",
                "mode" to "warm",
                "scenes" to """["evening"]""",
            ),
        )

        assertEquals("office", args.getValue("room").jsonPrimitive.content)
        assertEquals("80", args.getValue("brightness").jsonPrimitive.content)
        assertEquals(false, args.getValue("brightness").jsonPrimitive.isString)
        assertEquals("1.5", args.getValue("fade").jsonPrimitive.content)
        assertEquals(true, args.getValue("on").jsonPrimitive.content.toBoolean())
        assertEquals(false, args.getValue("on").jsonPrimitive.isString)
        assertEquals("warm", args.getValue("mode").jsonPrimitive.content)
        assertEquals("""["evening"]""", args.getValue("scenes").toString())
    }

    @Test
    fun `buildArguments omits blank values so optional params stay absent`() {
        val args = buildArguments(
            kitchenSink.parameterFields(),
            mapOf("room" to "office", "brightness" to "   ", "mode" to ""),
        )

        assertEquals(setOf("room"), args.keys)
    }

    @Test
    fun `unparseable number falls back to a string rather than dropping the value`() {
        val args = buildArguments(kitchenSink.parameterFields(), mapOf("brightness" to "bright"))

        assertEquals("bright", args.getValue("brightness").jsonPrimitive.content)
        assertEquals(true, args.getValue("brightness").jsonPrimitive.isString)
    }

    @Test
    fun `missingRequired reports only blank required fields`() {
        assertEquals(
            listOf("room", "on"),
            missingRequired(kitchenSink.parameterFields(), emptyMap()),
        )
        assertEquals(
            listOf("on"),
            missingRequired(kitchenSink.parameterFields(), mapOf("room" to "office")),
        )
        assertEquals(
            emptyList(),
            missingRequired(
                kitchenSink.parameterFields(),
                mapOf("room" to "office", "on" to "true"),
            ),
        )
    }

    @Test
    fun `invalidJsonFields flags only unparseable json`() {
        val fields = kitchenSink.parameterFields()

        assertEquals(listOf("scenes"), invalidJsonFields(fields, mapOf("scenes" to "[unclosed")))
        assertEquals(emptyList(), invalidJsonFields(fields, mapOf("scenes" to """["ok"]""")))
        // Blank is absent, not invalid.
        assertEquals(emptyList(), invalidJsonFields(fields, mapOf("scenes" to "  ")))
    }

    @Test
    fun `stored arguments round trip back into form values`() {
        val fields = kitchenSink.parameterFields()
        val original = mapOf(
            "room" to "office",
            "brightness" to "80",
            "on" to "true",
            "mode" to "cool",
            "scenes" to """["evening"]""",
        )

        assertEquals(original, buildArguments(fields, original).toFormValues(fields))
    }

    @Test
    fun `arguments for parameters no longer in the schema are dropped`() {
        val fields = tool(
            properties = buildJsonObject { put("room", buildJsonObject { put("type", "string") }) }
        ).parameterFields()
        val stored = buildJsonObject {
            put("room", "office")
            put("removed_param", "stale")
        }

        assertEquals(mapOf("room" to "office"), stored.toFormValues(fields))
    }
}
