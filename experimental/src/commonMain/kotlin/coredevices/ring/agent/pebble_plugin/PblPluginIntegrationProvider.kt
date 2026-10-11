package coredevices.ring.agent.pebble_plugin

import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import io.rebble.libpebblecommon.plugin.Plugin
import kotlin.uuid.Uuid

/**
 * A plugin offered as a note/reminder destination because it declares a `create_note` and/or
 * `create_reminder` action together with the matching `notes/configured` / `reminders/configured`
 * source. Instances are recreated on every lookup, so equality is by [id].
 */
class PblPluginIntegrationProvider private constructor(
    private val plugin: Plugin,
    override val supports: Set<IntegrationSupport>,
) : IntegrationProvider {
    val pluginUuid: Uuid get() = plugin.pluginUuid
    override val id: String = idFor(pluginUuid)
    override val title: String get() = plugin.name

    override fun notes(): NoteIntegration = PblPluginActionNoteIntegration(plugin)
    override fun reminders(): ReminderIntegration = PblPluginActionReminderIntegration(plugin)

    override suspend fun isAuthorized(): Boolean = supports.all { isAuthorized(it) }

    override suspend fun isAuthorized(support: IntegrationSupport): Boolean = when (support) {
        IntegrationSupport.Note -> notes().isAuthorized()
        IntegrationSupport.Reminder -> reminders().isAuthorized()
    }

    override fun equals(other: Any?): Boolean = other is PblPluginIntegrationProvider && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "PblPluginIntegrationProvider($id, $title)"
    override val isExternalPlugin: Boolean get() = true

    companion object {
        private const val ID_PREFIX = "plugin:"

        fun idFor(pluginUuid: Uuid): String = ID_PREFIX + pluginUuid

        /** Null when the plugin offers neither kind. */
        fun forPlugin(plugin: Plugin): PblPluginIntegrationProvider? {
            val supports = buildSet {
                if (plugin.offersDestination(PblPluginActionNoteIntegration.ACTION_NAME, PblPluginActionNoteIntegration.CATEGORY)) {
                    add(IntegrationSupport.Note)
                }
                if (plugin.offersDestination(PblPluginActionReminderIntegration.ACTION_NAME, PblPluginActionReminderIntegration.CATEGORY)) {
                    add(IntegrationSupport.Reminder)
                }
            }
            return if (supports.isEmpty()) null else PblPluginIntegrationProvider(plugin, supports)
        }

        private fun Plugin.offersDestination(action: String, category: String): Boolean =
            action(action) != null && serves(category, PblPluginActionIntegration.CONFIGURED_ITEM)
    }
}
