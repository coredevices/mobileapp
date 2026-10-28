package coredevices.pebble.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coredevices.pebble.services.StoreBatteryImpact
import coredevices.pebble.services.StoreBatteryImpactMode
import io.rebble.libpebblecommon.metadata.WatchType

internal fun String.capitalized() = replaceFirstChar { it.uppercase() }

internal fun batteryImpactWatchName(codename: String, short: Boolean): String =
    WatchType.fromCodename(codename)?.let { if (short) it.shortName() else it.modelDescription() } ?: codename

private fun batteryLifeRange(level: String) = when (level) {
    "negligible" -> "<5%"
    "small" -> "5-15%"
    "medium" -> "15-30%"
    "large" -> "30%+"
    else -> null
}

private fun batteryImpactModeName(mode: String) = when (mode) {
    "default" -> "Default"
    "seconds" -> "With seconds"
    else -> "With animations"
}

@Composable
fun BatteryImpactDialog(
    impacts: Map<String, StoreBatteryImpact>,
    initialPlatform: String,
    onDismiss: () -> Unit,
) {
    var platform by remember { mutableStateOf(initialPlatform) }
    val impact = impacts.getValue(platform)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery Impact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (impacts.size > 1) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { expanded = true },
                            label = { Text(batteryImpactWatchName(platform, short = false)) },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            impacts.keys.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(batteryImpactWatchName(option, short = false)) },
                                    onClick = {
                                        platform = option
                                        expanded = false
                                    },
                                    leadingIcon = if (option == platform) {
                                        { Icon(Icons.Filled.Done, contentDescription = "Selected") }
                                    } else null,
                                )
                            }
                        }
                    }
                } else {
                    Text(batteryImpactWatchName(platform, short = false), fontWeight = FontWeight.Bold)
                }
                impact.modes.ifEmpty { listOf(StoreBatteryImpactMode("default", impact.level)) }.forEach { mode ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(batteryImpactModeName(mode.mode))
                            Text(mode.level.capitalized(), fontWeight = FontWeight.Bold)
                        }
                        batteryLifeRange(mode.level)?.let {
                            Text(
                                "$it shorter battery life",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                            )
                        }
                    }
                }
                val versionText = impact.appVersion?.let { " (version $it)" } ?: ""
                Text(
                    "Compared with the TicToc watchface, measured on real watches$versionText.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        },
    )
}
