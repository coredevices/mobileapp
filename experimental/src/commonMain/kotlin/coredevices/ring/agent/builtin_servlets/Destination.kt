package coredevices.ring.agent.builtin_servlets

import coredevices.mcp.SessionContext
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.integrations.providers.titleFor

/** Tool argument naming the provider to send an item to instead of the selected default. */
const val DESTINATION_ARG = "destination"

private const val DESTINATION_DESCRIPTION_START = "Only when the user explicitly names the app to put the "
private const val DESTINATION_DESCRIPTION_END = " in (e.g. 'on TickTick'), that app's name exactly as listed " +
    "in the context. Omit to use the user's default."

const val NOTE_DESTINATION_DESCRIPTION = DESTINATION_DESCRIPTION_START + "note" + DESTINATION_DESCRIPTION_END
const val REMINDER_DESTINATION_DESCRIPTION = DESTINATION_DESCRIPTION_START + "reminder" + DESTINATION_DESCRIPTION_END
const val LIST_ITEM_DESTINATION_DESCRIPTION = DESTINATION_DESCRIPTION_START + "list item" + DESTINATION_DESCRIPTION_END

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
