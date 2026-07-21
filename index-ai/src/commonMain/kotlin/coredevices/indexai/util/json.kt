package coredevices.indexai.util

import coredevices.indexai.data.entity.ItemDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.modules.SerializersModule

val JsonSnake = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        // Metadata types that no longer exist (e.g. the removed "calendar_event") or that a newer
        // app version writes must not brick decoding of the whole item — fall back to a plain note.
        polymorphicDefaultDeserializer(ItemDocument.ItemMetadata::class) {
            ItemDocument.ItemMetadata.Note.serializer()
        }
    }
}
