package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.connection.Plugins
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.agent.integrations.fuzzyFilter
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import io.rebble.libpebblecommon.plugin.SourceUpdate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.offsetAt
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Creates reminders through a plugin's `create_reminder` action. Built by [PblPluginIntegrationProvider]. */
class PblPluginActionReminderIntegration(
    plugin: PluginMetadata,
    plugins: Plugins,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ReminderIntegration, PblPluginActionIntegration(plugin, plugins, CATEGORY) {
    companion object {
        const val CATEGORY = "reminders"
        const val ACTION_NAME = "create_reminder"
        private const val DEFAULT_LIST_SOURCE = "$CATEGORY/list"
        private val LIST_TIMEOUT = 3.seconds
    }

    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?
    ): String {
        val args = buildJsonObject {
            put("title", title)
            deadline?.let {
                put("due_iso", it.format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET, timeZone.offsetAt(it)))
                put("timezone", timeZone.id)
                notifyBefore?.let { lead -> put("notify_before_ms", lead.inWholeMilliseconds) }
            }
            listId?.let { put("instanceId", it) }
        }
        val result = plugins.invoke(PluginCaller.Host(), plugin, ACTION_NAME, args)
        if (!result.ok) error(result.message ?: result.code ?: "$ACTION_NAME failed")
        return result.text ?: ""
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        getAllLists().fuzzyFilter(listName)

    /** Lists come from the source the action targets; a plugin with no such source only has its default list. */
    override suspend fun getAllLists(): List<ReminderListEntry> {
        val target = plugin.action(ACTION_NAME)?.targets?.firstOrNull() ?: DEFAULT_LIST_SOURCE
        val (category, item) = target.split("/").takeIf { it.size >= 2 } ?: return emptyList()
        if (!plugin.serves(category, item)) return emptyList()
        val update = plugins.read(
            PluginCaller.Host(),
            plugin,
            category,
            item,
            properties = listOf(SourceInstance.PROPERTY_NAME),
            timeout = LIST_TIMEOUT,
        )
        val envelope = (update as? SourceUpdate.Data)?.envelope ?: return emptyList()
        return envelope.instances.mapNotNull { instance ->
            instance.properties[SourceInstance.PROPERTY_NAME]
                ?.get(SourceShapeNames.SHORT_TEXT)
                ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?.let { ReminderListEntry(instance.instanceId, it) }
        }
    }
}
