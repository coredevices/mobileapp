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
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.battery_impact_mode_default
import coreapp.pebble.generated.resources.battery_impact_mode_seconds
import coreapp.pebble.generated.resources.battery_impact_mode_animations
import coreapp.pebble.generated.resources.battery_impact_level_negligible
import coreapp.pebble.generated.resources.battery_impact_level_small
import coreapp.pebble.generated.resources.battery_impact_level_medium
import coreapp.pebble.generated.resources.battery_impact_level_large
import coreapp.pebble.generated.resources.battery_impact_title
import coreapp.pebble.generated.resources.battery_impact_selected
import coreapp.pebble.generated.resources.battery_impact_shorter_life
import coreapp.pebble.generated.resources.battery_impact_compared
import coreapp.pebble.generated.resources.battery_impact_compared_version
import coreapp.pebble.generated.resources.battery_impact_dismiss
import org.jetbrains.compose.resources.stringResource

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

@Composable
private fun batteryImpactModeName(mode: String) = when (mode) {
    "default" -> stringResource(Res.string.battery_impact_mode_default)
    "seconds" -> stringResource(Res.string.battery_impact_mode_seconds)
    else -> stringResource(Res.string.battery_impact_mode_animations)
}

@Composable
private fun batteryImpactLevelName(level: String) = when (level) {
    "negligible" -> stringResource(Res.string.battery_impact_level_negligible)
    "small" -> stringResource(Res.string.battery_impact_level_small)
    "medium" -> stringResource(Res.string.battery_impact_level_medium)
    "large" -> stringResource(Res.string.battery_impact_level_large)
    else -> level.capitalized()
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
        title = { Text(stringResource(Res.string.battery_impact_title)) },
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
                                        { Icon(Icons.Filled.Done, contentDescription = stringResource(Res.string.battery_impact_selected)) }
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
                            Text(batteryImpactLevelName(mode.level), fontWeight = FontWeight.Bold)
                        }
                        batteryLifeRange(mode.level)?.let {
                            Text(
                                stringResource(Res.string.battery_impact_shorter_life, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                            )
                        }
                    }
                }
                Text(
                    impact.appVersion?.let { stringResource(Res.string.battery_impact_compared_version, it) }
                        ?: stringResource(Res.string.battery_impact_compared),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.battery_impact_dismiss)) }
        },
    )
}
