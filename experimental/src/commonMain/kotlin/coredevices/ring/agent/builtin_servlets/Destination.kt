package coredevices.ring.agent.builtin_servlets

import coredevices.mcp.SessionContext
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.integrations.providers.titleFor
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.json.JsonObject

/** Tool argument naming the provider to send an item to instead of the selected default. */
const val DESTINATION_ARG = "destination"

fun destinationProperty(item: String): JsonObject = JsonObject(
    mapOf(
        "type" to "string",
        "description" to "Only when the user explicitly names the app to put the $item in " +
            "(e.g. 'on TickTick'), that app's name exactly as listed in the context. " +
            "Omit to use the user's default."
    ).toJson()
)

/** The schema for the on-device model, whose fixed tool list has no [DESTINATION_ARG]. */
fun Tool.withoutDestination(): Tool = copy(
    inputSchema = inputSchema.copy(
        properties = inputSchema.properties?.let { JsonObject(it.filterKeys { key -> key != DESTINATION_ARG }) }
    )
)

suspend fun SessionContext?.userMessageIfReady(): String? =
    this?.userMessageText.takeIf { it?.isCompleted ?: false }?.await()

/** Context lines telling the agent which named, non-default providers [tools] may route to. */
suspend fun IntegrationProviderRepository.destinationHints(
    message: String?,
    support: IntegrationSupport,
    vararg tools: String,
): List<String> {
    if (message == null) return emptyList()
    return mentionedProviders(message, support).map { provider ->
        val title = provider.titleFor(support)
        "The user named '$title'. If they asked for it to go there, pass $DESTINATION_ARG='$title' " +
            "to ${tools.joinToString(" or ")}."
    }
}
