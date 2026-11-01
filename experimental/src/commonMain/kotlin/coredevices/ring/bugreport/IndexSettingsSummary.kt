package coredevices.ring.bugreport

import coredevices.ring.agent.IndexActionsRepository
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.McpServerRepository
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.ui.screens.settings.gestureLabel
import coredevices.ring.ui.screens.settings.tileLabel
import kotlinx.coroutines.flow.first

/**
 * What the user has configured in Index settings, for bug reports.
 *
 * Webhook endpoints are reported by host only — the path and query routinely carry the
 * secret half of the endpoint, and this text lands in a Linear issue.
 */
class IndexSettingsSummary(
    private val preferences: Preferences,
    private val providers: IntegrationProviderRepository,
    private val gestureRouting: GestureRoutingPreferences,
    private val webhookPreferences: IndexWebhookPreferences,
    private val actionsRepository: IndexActionsRepository,
    private val mcpServerRepository: McpServerRepository,
) {
    suspend fun summary(): String = buildString {
        appendLine()
        appendLine("Index Settings")

        appendLine("Ring button:")
        val agentTitles = providers.providers(IntegrationSupport.Agent).associate { it.id to it.title }
        RingGesture.entries.forEach { gesture ->
            appendLine("  ${gesture.gestureLabel}: ${gestureRouting.destinationFor(gesture).tileLabel(agentTitles)}")
        }

        val webhooks = RingGesture.entries.mapNotNull { gesture ->
            webhookPreferences.configFor(gesture).takeIf { it.isActive }?.let { config ->
                "${gesture.gestureLabel} -> ${hostOf(config.url)} (${config.payloadMode})"
            }
        }
        appendLine("Webhooks: ${webhooks.ifEmpty { listOf("none") }.joinToString("; ")}")

        val actions = actionsRepository.actions.first()
        appendLine("Actions:")
        actions.forEach { action ->
            val state = if (action.enabled) "on" else "off"
            val reason = action.disabledReason?.let { " ($it)" } ?: ""
            appendLine("  ${action.title}: $state$reason")
        }

        appendLine("Notes save to: ${providers.selectedNoteProvider.value.notesTitle}")
        appendLine("Reminders save to: ${providers.selectedReminderProvider.value.remindersTitle}")
        appendLine("Unclear requests become: ${preferences.defaultCaptureType.value}")
        appendLine("Phone calendar connected: ${preferences.phoneCalendarEnabled.value}")

        val servers = mcpServerRepository.httpServersFlow().first()
        append("HTTP MCP servers: ${servers.count { it.enabled }} of ${servers.size} enabled")
    }

    private fun hostOf(url: String?): String =
        url?.substringAfter("://")?.substringBefore('/')?.ifBlank { "set" } ?: "set"
}
