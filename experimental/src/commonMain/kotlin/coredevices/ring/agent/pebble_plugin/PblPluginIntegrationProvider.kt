package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.connection.Plugins
import coredevices.ring.agent.integrations.AgentIntegration
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlin.uuid.Uuid

/**
 * A plugin offered as a note/reminder/agent destination because it declares a `create_note`,
 * `create_reminder` and/or `send_to_agent` action together with the matching `notes/configured` /
 * `reminders/configured` / `agent/configured` source. Instances are recreated on every lookup, so
 * equality is by [id].
 */
class PblPluginIntegrationProvider private constructor(
    private val plugin: PluginMetadata,
    private val plugins: Plugins,
    override val supports: Set<IntegrationSupport>,
) : IntegrationProvider {
    val pluginUuid: Uuid get() = plugin.pluginUuid
    override val id: String = idFor(pluginUuid)
    override val title: String get() = plugin.name

    override fun notes(): NoteIntegration = PblPluginActionNoteIntegration(plugin, plugins)
    override fun reminders(): ReminderIntegration = PblPluginActionReminderIntegration(plugin, plugins)
    override fun agent(): AgentIntegration = PblPluginActionAgentIntegration(plugin, plugins)

    override suspend fun isAuthorized(): Boolean = supports.all { isAuthorized(it) }

    override suspend fun isAuthorized(support: IntegrationSupport): Boolean = when (support) {
        IntegrationSupport.Note -> notes().isAuthorized()
        IntegrationSupport.Reminder -> reminders().isAuthorized()
        IntegrationSupport.Agent -> agent().isAuthorized()
    }

    override fun equals(other: Any?): Boolean = other is PblPluginIntegrationProvider && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "PblPluginIntegrationProvider($id, $title)"
    override val isExternalPlugin: Boolean get() = true

    companion object {
        private const val ID_PREFIX = "plugin:"

        fun idFor(pluginUuid: Uuid): String = ID_PREFIX + pluginUuid

        /** Null when the plugin offers neither kind. */
        fun forPlugin(plugin: PluginMetadata, plugins: Plugins): PblPluginIntegrationProvider? {
            val supports = destinationSupports(plugin)
            return if (supports.isEmpty()) null else PblPluginIntegrationProvider(plugin, plugins, supports)
        }

        /** The kinds [plugin] can be a destination for, whether or not it is installed. */
        fun destinationSupports(plugin: PluginMetadata): Set<IntegrationSupport> = buildSet {
            if (plugin.offersDestination(PblPluginActionNoteIntegration.ACTION_NAME, PblPluginActionNoteIntegration.CATEGORY)) {
                add(IntegrationSupport.Note)
            }
            if (plugin.offersDestination(PblPluginActionReminderIntegration.ACTION_NAME, PblPluginActionReminderIntegration.CATEGORY)) {
                add(IntegrationSupport.Reminder)
            }
            if (plugin.offersDestination(PblPluginActionAgentIntegration.ACTION_NAME, PblPluginActionAgentIntegration.CATEGORY)) {
                add(IntegrationSupport.Agent)
            }
        }

        private fun PluginMetadata.offersDestination(action: String, category: String): Boolean =
            action(action) != null && serves(category, PblPluginActionIntegration.CONFIGURED_ITEM)
    }
}
