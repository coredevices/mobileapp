package coredevices.ring.data.entity.room

import androidx.room.Entity
import coredevices.ring.agent.pebble_plugin.PluginGrant
import kotlin.uuid.Uuid

/**
 * One thing the user has withheld from the Index agent. Absence means allowed, so a freshly
 * installed plugin needs no rows.
 */
@Entity(primaryKeys = ["pluginUuid", "grant", "name"])
data class PluginGrantDenial(
    val pluginUuid: Uuid,
    val grant: PluginGrant,
    /** The single action or source path withheld; [WHOLE_GRANT] withholds all of them. */
    val name: String = WHOLE_GRANT,
) {
    companion object {
        const val WHOLE_GRANT = ""
    }
}
