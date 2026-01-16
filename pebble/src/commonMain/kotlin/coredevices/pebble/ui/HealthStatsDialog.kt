package coredevices.pebble.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coredevices.ui.M3Dialog
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.health.HealthDebugStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("HealthStatsDialog")

@Composable
fun HealthStatsDialog(libPebble: LibPebble, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<HealthDebugStats?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun syncAndRefresh() {
        scope.launch {
            try {
                isRefreshing = true
                // Pull ALL data from watch to reconcile any missing steps
                val requested = libPebble.requestHealthData(fullSync = true)

                // We don't know how long it will actually take for datalogging to sync health data
                delay(5.seconds)

                // Update stats
                stats = libPebble.getHealthDebugStats()
                // Send updated averages back to watch
                libPebble.sendHealthAveragesToWatch()
            } catch (e: Exception) {
                Logger.e("HealthStatsDialog", e) { "Failed to sync health data" }
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            stats = libPebble.getHealthDebugStats()
        } catch (e: Exception) {
            Logger.e("HealthStatsDialog", e) { "Failed to load health stats" }
        }
    }

    M3Dialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Health Debug Stats")
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = { syncAndRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh health data")
                    }
                }
            }
        },
        buttons = {
            TextButton(onClick = onDismissRequest) { Text("Close") }
        },
    ) {
        Box(Modifier.heightIn(max = 400.dp)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (stats != null) {
                    val s = stats!!
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Today's Steps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Today's Steps", style = MaterialTheme.typography.bodyMedium)
                            Text("${s.todaySteps}", style = MaterialTheme.typography.bodyMedium)
                        }

                        // 30d Avg Steps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Average",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${s.averageStepsPerDay}/day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Last Night's Sleep
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Last Night's Sleep", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                s.lastNightSleepHours?.let {
                                    "${it.format(1)}h"
                                }
                                    ?: "--",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (s.lastNightSleepHours != null) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                            )
                        }

                        // 30d Avg Sleep
                        val avgSleepHrs = s.averageSleepSecondsPerDay / 3600.0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Average",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${avgSleepHrs.format(1)}h/night",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        "Loading health statistics...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

expect fun Double.format(digits: Int): String
fun Float.format(digits: Int): String = toDouble().format(digits)