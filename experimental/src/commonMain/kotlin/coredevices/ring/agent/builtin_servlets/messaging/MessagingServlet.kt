package coredevices.ring.agent.builtin_servlets.messaging

import coredevices.mcp.SessionContext
import coredevices.ring.agent.pebble_plugin.BuiltInServletPlugin
import io.rebble.libpebblecommon.plugin.PluginPermissions
import coredevices.ring.database.Preferences
import coredevices.ring.isBeeperAvailable
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

val MESSAGING_PLUGIN_UUID: Uuid = Uuid.parse("00000000-0000-0000-0002-000000000002")

const val MESSAGING_SERVLET_NAME = "builtin_messaging"

object MessagingServlet : BuiltInServletPlugin(
    name = MESSAGING_SERVLET_NAME,
    tools = listOf(
        SendBeeperMessageTool(),
    ),
    pluginUuid = MESSAGING_PLUGIN_UUID,
    pluginName = "Beeper (Index)",
    description = "Send instant messages to the user's approved contacts through Beeper.",
    callerPermission = PluginPermissions.SEND_MESSAGES_BEEPER,
), KoinComponent {
    private val prefs: Preferences by inject()
    override suspend fun getDisabledTools(): List<String> {
        val canMessage = prefs.approvedBeeperContacts.value.isNotEmpty() || prefs.beeperMessageAnyone.value
        return if (canMessage) emptyList() else listOf(SendBeeperMessageToolConstants.TOOL_NAME)
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val approved = prefs.approvedBeeperContacts.value
        val messageAnyone = prefs.beeperMessageAnyone.value
        if (approved.isEmpty() && !messageAnyone) return super.getExtraContext(sessionContext)
        return buildString {
            appendLine(super.getExtraContext(sessionContext))
            if (approved.isNotEmpty()) {
                appendLine("Approved contacts for ${SendBeeperMessageToolConstants.TOOL_NAME}:")
                approved.forEach { contact ->
                    appendLine("- ${contact.name} ${contact.nickname?.let { "(Nickname: $it)" } ?: ""}".trim())
                }
            }
            if (messageAnyone) {
                appendLine("Messages to anyone other than the approved contacts are held for the user to confirm before sending.")
            }
        }
    }
}
/** Beeper needs to be installed and to have granted access before the agent can message through it. */
fun beeperUnavailableReason(permissionRequester: PermissionRequester): Flow<String?> =
    permissionRequester.grantedPermissions.map { granted ->
        when {
            !isBeeperAvailable() -> "Beeper isn't installed on this phone"
            Permission.Beeper !in granted -> "Allow access to Beeper in your phone's settings"
            else -> null
        }
    }
