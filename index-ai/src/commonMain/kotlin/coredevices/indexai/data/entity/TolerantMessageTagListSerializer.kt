package coredevices.indexai.data.entity

import co.touchlab.kermit.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull

/**
 * Tolerant serializer for `message_tags`: a tag type this app version doesn't know (written by
 * a newer version to the shared Firestore recordings) is dropped rather than failing the whole
 * message decode, mirroring [TolerantSemanticResultSerializer].
 */
object TolerantMessageTagListSerializer : KSerializer<List<MessageTag>?> {
    private val delegate = ListSerializer(MessageTag.serializer()).nullable
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<MessageTag>? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(delegate)
        val element = json.decodeJsonElement()
        if (element is JsonNull) return null
        if (element !is JsonArray) {
            Logger.withTag("MessageTag").w { "Unexpected message_tags shape, dropping: $element" }
            return null
        }
        return element.mapNotNull { tag ->
            try {
                json.json.decodeFromJsonElement(MessageTag.serializer(), tag)
            } catch (e: Exception) {
                Logger.withTag("MessageTag").w { "Unknown/undecodable message tag, dropping: ${e.message}" }
                null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<MessageTag>?) =
        encoder.encodeSerializableValue(delegate, value)
}
