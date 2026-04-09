package coredevices.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
val WisprJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class WisprContext(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val app: AppInfo? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val dictionaryContext: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val userFirstName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val userLastName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val contentText: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conversation: WisprConversationContext? = null
)

@Serializable
data class AppInfo(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val type: String? = null,
)

@Serializable
data class WisprConversationContext(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val participants: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val messages: List<WisprConversationMessage> = emptyList()
)

@Serializable
data class WisprConversationMessage(
    val role: String,
    val content: String
)

// WebSocket protocol messages

@Serializable
data class WisprAuthMessage(
    val type: String = "auth",
    val accessToken: String,
    val context: WisprContext,
    val language: List<String>,
)

@Serializable
data class WisprAudioPackets(
    val packets: List<String>,
    val volumes: List<Double>,
    val packetDuration: Double,
    val audioEncoding: String = "wav",
    val byteEncoding: String = "base64",
)

@Serializable
data class WisprAppendMessage(
    val type: String = "append",
    val position: Int,
    val audioPackets: WisprAudioPackets,
)

@Serializable
data class WisprCommitMessage(
    val type: String = "commit",
    val totalPackets: Int,
)

@Serializable
data class WisprResponseBody(
    val text: String? = null,
)

@Serializable
data class WisprResponse(
    val status: String? = null,
    val error: String? = null,
    val message: JsonObject? = null,
    val final: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val body: WisprResponseBody? = null,
)