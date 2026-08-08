package coredevices.ring.ui.screens.settings.clickactions

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One editable control derived from a tool's MCP input schema. */
data class ToolParameterField(
    val name: String,
    val description: String?,
    val required: Boolean,
    val kind: Kind,
) {
    sealed interface Kind {
        data object Text : Kind
        data object Number : Kind
        data object Bool : Kind
        data class Choice(val options: List<String>) : Kind

        /** Arrays and nested objects, which have no single sensible control — edited as JSON. */
        data object Json : Kind
    }
}

/**
 * Derives the form controls for this tool. Unknown or compound types fall back to
 * [ToolParameterField.Kind.Json] so every tool stays bindable, however exotic its schema.
 */
fun Tool.parameterFields(): List<ToolParameterField> {
    val requiredNames = inputSchema.required.orEmpty().toSet()
    val properties = inputSchema.properties ?: return emptyList()
    return properties.map { (name, element) ->
        val schema = element.jsonObject
        val enumOptions = (schema["enum"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotEmpty() }
        val kind = when {
            enumOptions != null -> ToolParameterField.Kind.Choice(enumOptions)
            else -> when ((schema["type"] as? JsonPrimitive)?.contentOrNull) {
                "string" -> ToolParameterField.Kind.Text
                "number", "integer" -> ToolParameterField.Kind.Number
                "boolean" -> ToolParameterField.Kind.Bool
                else -> ToolParameterField.Kind.Json
            }
        }
        ToolParameterField(
            name = name,
            description = (schema["description"] as? JsonPrimitive)?.contentOrNull,
            required = name in requiredNames,
            kind = kind,
        )
    }
}

/** Required fields the user has left blank. */
fun missingRequired(
    fields: List<ToolParameterField>,
    values: Map<String, String>,
): List<String> = fields
    .filter { it.required && values[it.name].isNullOrBlank() }
    .map { it.name }

/** JSON-typed fields whose current text does not parse. */
fun invalidJsonFields(
    fields: List<ToolParameterField>,
    values: Map<String, String>,
): List<String> = fields
    .filter { it.kind is ToolParameterField.Kind.Json }
    .filter { field ->
        val raw = values[field.name]?.trim().orEmpty()
        raw.isNotEmpty() && runCatching { Json.parseToJsonElement(raw) }.isFailure
    }
    .map { it.name }

/**
 * Encodes form text back into the arguments object the tool is called with. Blank values are
 * omitted rather than sent as empty strings, so optional parameters stay absent.
 */
fun buildArguments(
    fields: List<ToolParameterField>,
    values: Map<String, String>,
): JsonObject = buildJsonObject {
    for (field in fields) {
        val raw = values[field.name]?.trim().orEmpty()
        if (raw.isEmpty()) continue
        val encoded = when (field.kind) {
            is ToolParameterField.Kind.Text, is ToolParameterField.Kind.Choice ->
                JsonPrimitive(raw)
            is ToolParameterField.Kind.Number ->
                raw.toLongOrNull()?.let { JsonPrimitive(it) }
                    ?: raw.toDoubleOrNull()?.let { JsonPrimitive(it) }
                    ?: JsonPrimitive(raw)
            is ToolParameterField.Kind.Bool ->
                JsonPrimitive(raw.equals("true", ignoreCase = true))
            is ToolParameterField.Kind.Json ->
                runCatching { Json.parseToJsonElement(raw) }.getOrElse { JsonPrimitive(raw) }
        }
        put(field.name, encoded)
    }
}

/**
 * Decodes stored arguments back into form text for editing. Arguments whose parameter no longer
 * exists in the schema are dropped — the tool would reject them anyway.
 */
fun JsonObject.toFormValues(fields: List<ToolParameterField>): Map<String, String> =
    fields.mapNotNull { field ->
        val element = this[field.name] ?: return@mapNotNull null
        val text = when (field.kind) {
            is ToolParameterField.Kind.Json -> element.toString()
            else -> (element as? JsonPrimitive)?.contentOrNull ?: element.toString()
        }
        field.name to text
    }.toMap()
