package coredevices.pebble.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coredevices.pebble.ui.PluginCapabilities.ActionSummary
import coredevices.pebble.ui.PluginCapabilities.SourceGroup
import io.rebble.libpebblecommon.plugin.PluginMetadata

/** What a plugin's manifest declares it serves and can do. Rows expand on tap for the detail. */
@Composable
fun PluginCapabilitiesSection(metadata: PluginMetadata) {
    val capabilities = remember(metadata) { PluginCapabilities.of(metadata) }
    if (capabilities.isEmpty) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (capabilities.sources.isNotEmpty()) {
            SectionTitle("Sources")
            capabilities.sources.forEach { SourceGroupRow(it) }
        }
        if (capabilities.actions.isNotEmpty()) {
            SectionTitle("Actions")
            var showAll by remember(metadata) { mutableStateOf(false) }
            val actions = capabilities.actions
            val shown = if (showAll) actions else actions.take(PluginCapabilities.ACTIONS_PREVIEW)
            shown.forEach { ActionRow(it) }
            if (actions.size > PluginCapabilities.ACTIONS_PREVIEW) {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (showAll) "Show less" else "Show all ${actions.size}")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
}

@Composable
private fun SourceGroupRow(group: SourceGroup) {
    val expandable = group.items.any { it.properties.isNotEmpty() }
    var expanded by remember(group) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable) { expanded = !expanded }
            .padding(vertical = 4.dp)
            .animateContentSize(),
    ) {
        Icon(
            categoryIcon(group.category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(group.label, style = MaterialTheme.typography.bodyMedium)
            if (expanded) {
                group.items.forEach { item ->
                    val detail = if (item.properties.isEmpty()) item.label
                    else "${item.label}: ${item.properties.joinToString()}"
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    group.items.joinToString(" · ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(action: ActionSummary) {
    var expanded by remember(action) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = action.description.isNotEmpty()) { expanded = !expanded }
            .padding(vertical = 4.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(action.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (action.requiresConfirmation) Tag("Asks first", MaterialTheme.colorScheme.onSurfaceVariant)
            if (action.destructive) Tag("Destructive", MaterialTheme.colorScheme.error)
        }
        if (action.description.isNotEmpty()) {
            Text(
                action.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(start = 8.dp))
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "weather" -> Icons.Default.WbSunny
    "calendar" -> Icons.Default.CalendarMonth
    "phone" -> Icons.Default.Smartphone
    "music" -> Icons.Default.MusicNote
    "notifications" -> Icons.Default.Notifications
    "watch" -> Icons.Default.Watch
    "home" -> Icons.Default.Home
    "finance" -> Icons.Default.ShowChart
    "notes" -> Icons.Default.Notes
    "reminders" -> Icons.Default.Checklist
    else -> Icons.Default.Extension
}
