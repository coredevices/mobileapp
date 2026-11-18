package coredevices.ring.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Replaces every `$ref` into `$defs` with the referenced schema, at any depth. Keys beside the
 * `$ref` override the target's. Cyclic and unresolvable references become a bare object.
 */
internal fun JsonObject.inlineRefs(defs: JsonObject?): JsonObject =
    inlineRefs(defs ?: JsonObject(emptyMap()), emptyList()) as JsonObject

private fun JsonElement.inlineRefs(defs: JsonObject, expanding: List<String>): JsonElement = when (this) {
    is JsonArray -> JsonArray(map { it.inlineRefs(defs, expanding) })
    is JsonObject -> {
        val ref = this["\$ref"]?.jsonPrimitive?.contentOrNull
        if (ref == null) {
            JsonObject(mapValues { it.value.inlineRefs(defs, expanding) })
        } else {
            val name = ref.substringAfterLast('/')
            val target = defs[name] as? JsonObject
            val siblings = filterKeys { it != "\$ref" }
            if (target == null || name in expanding) {
                JsonObject(mapOf("type" to JsonPrimitive("object")) + siblings)
            } else {
                JsonObject(target + siblings).inlineRefs(defs, expanding + name)
            }
        }
    }
    else -> this
}
