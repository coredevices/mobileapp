package coredevices.mcp.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A [JsonObject] that is written as an object by Json and as its JSON text by any other format
 * (e.g. Firestore's encoder, which can't encode a [JsonObject]).
 */
object FirestoreSafeJsonObjectSerializer : KSerializer<JsonObject> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("coredevices.mcp.data.FirestoreSafeJsonObject", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JsonObject) {
        if (encoder is JsonEncoder) encoder.encodeJsonElement(value)
        else encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): JsonObject =
        if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonObject
        else Json.parseToJsonElement(decoder.decodeString()).jsonObject
}
