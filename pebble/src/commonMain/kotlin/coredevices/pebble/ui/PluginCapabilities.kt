package coredevices.pebble.ui

import coredevices.util.capitalizeFirstWord
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.PluginMetadata

/** What a plugin declares, shaped for display: sources grouped by category, actions in order. */
data class PluginCapabilities(
    val sources: List<SourceGroup>,
    val actions: List<ActionSummary>,
) {
    val isEmpty: Boolean get() = sources.isEmpty() && actions.isEmpty()

    data class SourceGroup(
        val category: String,
        val label: String,
        val items: List<SourceItem>,
    )

    data class SourceItem(
        val label: String,
        val properties: List<String>,
    )

    data class ActionSummary(
        val label: String,
        val description: String,
        val destructive: Boolean,
        val requiresConfirmation: Boolean,
    )

    companion object {
        /** How many actions a plugin shows before the list collapses behind "Show all". */
        const val ACTIONS_PREVIEW = 3

        fun of(metadata: PluginMetadata): PluginCapabilities {
            val groups = metadata.sources.groupBy { it.category }.map { (category, declarations) ->
                val items = declarations.flatMap { declaration ->
                    declaration.items.map { item -> item to declaration.properties.keys.toList() }
                }.distinctBy { (item, _) -> item }
                SourceGroup(
                    category = category,
                    label = humanize(category),
                    items = items.map { (item, properties) -> SourceItem(humanize(item), properties.map(::humanize)) },
                )
            }
            return PluginCapabilities(groups, metadata.actions.map(::summarize))
        }

        private fun summarize(action: ActionDeclaration) = ActionSummary(
            label = humanize(action.name),
            description = action.description.trim(),
            destructive = action.destructive,
            requiresConfirmation = action.requiresConfirmation,
        )

        /** `room_lights` / `setPlaying` -> "Room lights" / "Set playing". */
        fun humanize(name: String): String = name
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.lowercase() }
            .capitalizeFirstWord()
    }
}
