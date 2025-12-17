package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.ui.health.HeartRateChart
import coredevices.pebble.ui.health.SleepChart
import coredevices.pebble.ui.health.StepsChart
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.health.HealthTimeRange
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.metadata.WatchType
import org.koin.compose.koinInject

/**
 * Main Health screen showing steps, heart rate, and sleep data.
 * Refactored to be more modular and memory-efficient.
 */
@Composable
fun HealthScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams
) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(false)
        topBarParams.actions {}
        topBarParams.title("Health")
        topBarParams.canGoBack(false)
    }

    val healthDao: HealthDao = koinInject()
    val libPebble = rememberLibPebble()
    val watches by libPebble.watches.collectAsState()

    val connectedDevice = remember(watches) {
        watches.filterIsInstance<ConnectedPebbleDevice>().firstOrNull()
    }

    val supportsHeartRate = remember(connectedDevice) {
        connectedDevice?.watchInfo?.platform?.watchType in listOf(
            WatchType.DIORITE,  // Pebble 2 HR
            WatchType.EMERY     // Pebble Time 2
        )
    }

    var timeRange by remember { mutableStateOf(HealthTimeRange.Daily) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Time range selector
        TimeRangeSelector(
            selectedRange = timeRange,
            onRangeSelected = { timeRange = it }
        )

        // Steps chart
        HealthMetricCard(
            title = "Steps",
            icon = Icons.Filled.DirectionsRun,
            iconTint = MaterialTheme.colorScheme.primary
        ) {
            StepsChart(healthDao, timeRange)
        }

        // Heart rate chart (only on devices that support it)
        if (supportsHeartRate) {
            HealthMetricCard(
                title = "Heart Rate",
                icon = Icons.Filled.Favorite,
                iconTint = Color(0xFFE91E63)
            ) {
                HeartRateChart(healthDao, timeRange)
            }
        }

        // Sleep chart
        HealthMetricCard(
            title = "Sleep",
            icon = Icons.Filled.Hotel,
            iconTint = Color(0xFF9C27B0)
        ) {
            SleepChart(healthDao, timeRange)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Time range selector chip row.
 */
@Composable
private fun TimeRangeSelector(
    selectedRange: HealthTimeRange,
    onRangeSelected: (HealthTimeRange) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HealthTimeRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.name) },
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * Reusable card wrapper for health metrics.
 */
@Composable
private fun HealthMetricCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}
