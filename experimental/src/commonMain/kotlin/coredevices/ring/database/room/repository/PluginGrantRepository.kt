package coredevices.ring.database.room.repository

import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.data.entity.room.PluginGrantDenial
import coredevices.ring.database.room.dao.PluginGrantDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/** What the user lets installed plugins do in the Index agent; everything not denied is allowed. */
class PluginGrantRepository(private val dao: PluginGrantDao) {

    /** Plugins the user has withheld [grant] from entirely. */
    fun disabledPlugins(grant: PluginGrant): Flow<Set<Uuid>> =
        dao.getAllFlow().map { denials ->
            denials
                .filter { it.grant == grant && it.name == PluginGrantDenial.WHOLE_GRANT }
                .mapTo(mutableSetOf()) { it.pluginUuid }
        }

    suspend fun setPluginGrantEnabled(pluginUuid: Uuid, grant: PluginGrant, enabled: Boolean) {
        val denial = PluginGrantDenial(pluginUuid, grant)
        if (enabled) dao.delete(denial) else dao.insert(denial)
    }
}
