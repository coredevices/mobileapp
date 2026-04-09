package coredevices.indexai.data

import coredevices.indexai.data.entity.ContentPartType
import coredevices.indexai.data.entity.MessageContentPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

class MessageContentPartListSerializer: KSerializer<List<MessageContentPart>> {
    override val descriptor = ListSerializer(MessageContentPart.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<MessageContentPart>) {
        encoder.encodeSerializableValue(ListSerializer(MessageContentPart.serializer()), value)
    }

    override fun deserialize(decoder: Decoder): List<MessageContentPart> {
        decoder as? JsonDecoder ?: error("This class can be loaded only by JSON")
        val element = decoder.decodeJsonElement()
        return if (element is JsonArray) {
            element.map { decoder.json.decodeFromJsonElement(MessageContentPart.serializer(), it) }
        } else {
            listOf(MessageContentPart(type = ContentPartType.text, text = element.jsonPrimitive.content))
        }
    }
}