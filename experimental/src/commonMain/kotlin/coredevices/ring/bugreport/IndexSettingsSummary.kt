package coredevices.ring.bugreport

import coredevices.ring.agent.IndexActionsRepository
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.ui.screens.settings.gestureLabel
import coredevices.ring.ui.screens.settings.settingsTitle
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
    private val gestureRouting: GestureRoutingPreferences,
    private val webhookPreferences: IndexWebhookPreferences,
    private val actionsRepository: IndexActionsRepository,
    private val sandboxRepository: McpSandboxRepository,
) {
    suspend fun summary(): String = buildString {
        appendLine()
        appendLine("Index Settings")

        appendLine("Ring button:")
        RingGesture.entries.forEach { gesture ->
            appendLine("  ${gesture.gestureLabel}: ${gestureRouting.destinationFor(gesture).tileLabel}")
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

        appendLine("Notes save to: ${preferences.noteProvider.value.settingsTitle}")
        appendLine("Reminders save to: ${preferences.reminderProvider.value.settingsTitle}")
        appendLine("Unclear requests become: ${preferences.defaultCaptureType.value}")
        appendLine("Phone calendar connected: ${preferences.phoneCalendarEnabled.value}")

        val groups = sandboxRepository.getAllGroupsFlow().first()
        append("MCP sandbox groups: ${groups.size}")
    }

    private fun hostOf(url: String?): String =
        url?.substringAfter("://")?.substringBefore('/')?.ifBlank { "set" } ?: "set"
}
