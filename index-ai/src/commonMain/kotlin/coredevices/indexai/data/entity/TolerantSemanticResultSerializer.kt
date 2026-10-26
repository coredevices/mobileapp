package coredevices.indexai.data.entity

import co.touchlab.kermit.Logger
import coredevices.mcp.data.SemanticResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull

/**
 * Tolerant serializer for the optional, display-only `semantic_result` field.
 *
 * Writers of the shared Firestore `recordings` collection (notably index-webapp) have
 * emitted `SemanticResult` discriminators that don't match the PascalCase `@SerialName`s
 * on the sealed type — e.g. `list_item_creation` vs `ListItemCreation` (MOB-9181). The
 * polymorphic decoder throws on an unknown discriminator, which would otherwise fail the
 * whole RecordingDocument decode (dropping the recording from sync / crashing read paths).
 *
 * On iOS gitlive only strips `NSNull` at the document's top level, so a nested explicit
 * `semantic_result: null` also reaches this serializer as a non-map value.
 *
 * Degrade an unresolvable subtype to null instead of throwing, mirroring
 * [TolerantInstantSerializer]: one drifted field never strands the entire recording. The
 * field is nullable and the UI already null-checks it.
 */
object TolerantSemanticResultSerializer : KSerializer<SemanticResult?> {
    private val delegate = SemanticResult.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): SemanticResult? {
        // gitlive decodes from a materialised map, so a failure can just be swallowed; JSON
        // must consume the whole element first or a failed decode leaves the lexer mid-object.
        val json = decoder as? JsonDecoder
            ?: return decodeOrNull { decoder.decodeSerializableValue(delegate) }
        val element = json.decodeJsonElement()
        if (element is JsonNull) return null
        return decodeOrNull { json.json.decodeFromJsonElement(delegate, element) }
    }

    private inline fun decodeOrNull(decode: () -> SemanticResult): SemanticResult? = try {
        decode()
    } catch (e: Exception) {
        Logger.withTag("SemanticResult")
            .w { "Unknown/undecodable semantic_result, dropping: ${e.message}" }
        null
    }

    override fun serialize(encoder: Encoder, value: SemanticResult?) {
        if (value == null) encoder.encodeNull()
        else encoder.encodeSerializableValue(delegate, value)
    }
}
