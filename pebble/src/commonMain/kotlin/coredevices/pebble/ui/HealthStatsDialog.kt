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
import kotlinx.coroutines.launch

@Composable
fun HealthStatsDialog(libPebble: LibPebble, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<HealthDebugStats?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun syncAndRefresh() {
        scope.launch {
            try {
                isRefreshing = true

                // Clean duplicates first
                Logger.i("HealthStatsDialog") { "Cleaning duplicate sleep entries..." }
                libPebble.forceHealthDataOverwrite()
                kotlinx.coroutines.delay(500) // Give it time to complete

                // Pull latest data from watch
                libPebble.requestHealthData(fullSync = false)

                // Wait for sync update with timeout
                try {
                    kotlinx.coroutines.withTimeout(10_000) {
                        libPebble.healthUpdateFlow.collect {
                            // First emission means we got an update
                            throw kotlinx.coroutines.CancellationException("Got update")
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Expected - flow collection cancelled by us
                } catch (e: Exception) {
                    Logger.w("HealthStatsDialog") { "Timeout waiting for health sync" }
                }

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
                                    s.lastNightSleepHours?.let { String.format("%.1fh", it) }
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
                                    "${String.format("%.1f", avgSleepHrs)}h/night",
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
