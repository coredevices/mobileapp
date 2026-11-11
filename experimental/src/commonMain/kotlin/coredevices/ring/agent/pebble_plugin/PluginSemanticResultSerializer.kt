package coredevices.ring.agent.pebble_plugin

import coredevices.mcp.data.SemanticResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/** Decodes a plugin's semantic result, whose `type` may also be written in CAPS_SNAKE_CASE. */
internal object PluginSemanticResultSerializer :
    JsonTransformingSerializer<SemanticResult>(SemanticResult.serializer()) {
    private const val TYPE = "type"

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val type = (element[TYPE] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return element
        if (type != type.uppercase()) return element
        return JsonObject(element + (TYPE to JsonPrimitive(type.capsSnakeToPascalCase())))
    }

    private fun String.capsSnakeToPascalCase() = split('_').joinToString("") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}
