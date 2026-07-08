package coredevices.indexai.data.entity

import co.touchlab.kermit.Logger
import coredevices.mcp.data.SemanticResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Tolerant serializer for the optional, display-only `semantic_result` field.
 *
 * Writers of the shared Firestore `recordings` collection (notably index-webapp) have
 * emitted `SemanticResult` discriminators that don't match the PascalCase `@SerialName`s
 * on the sealed type — e.g. `list_item_creation` vs `ListItemCreation` (MOB-9181). The
 * polymorphic decoder throws on an unknown discriminator, which would otherwise fail the
 * whole RecordingDocument decode (dropping the recording from sync / crashing read paths).
 *
 * Degrade an unresolvable subtype to null instead of throwing, mirroring
 * [TolerantInstantSerializer]: one drifted field never strands the entire recording. The
 * field is nullable and the UI already null-checks it.
 */
object TolerantSemanticResultSerializer : KSerializer<SemanticResult?> {
    private val delegate = SemanticResult.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): SemanticResult? =
        try {
            decoder.decodeSerializableValue(delegate)
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
