package coredevices.pebble.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rebble.libpebblecommon.health.HealthTimeRange

/**
 * Displays a row of metrics (distance, calories, active time) for steps data
 */
@Composable
internal fun StepsMetricsRow(
    metrics: StepsMetrics,
    timeRange: HealthTimeRange,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatsTile(
            label = getDistanceLabel(timeRange),
            value = metrics.distance,
            modifier = Modifier.weight(1f)
        )
        StatsTile(
            label = getCaloriesLabel(timeRange),
            value = metrics.calories,
            modifier = Modifier.weight(1f)
        )
        StatsTile(
            label = getActiveLabel(timeRange),
            value = metrics.activeTime,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Returns the appropriate label for distance based on time range
 */
private fun getDistanceLabel(timeRange: HealthTimeRange): String {
    return when (timeRange) {
        HealthTimeRange.Daily -> "Distance"
        HealthTimeRange.Weekly, HealthTimeRange.Monthly -> "Avg Distance"
    }
}

/**
 * Returns the appropriate label for calories based on time range
 */
private fun getCaloriesLabel(timeRange: HealthTimeRange): String {
    return when (timeRange) {
        HealthTimeRange.Daily -> "Calories"
        HealthTimeRange.Weekly, HealthTimeRange.Monthly -> "Avg Calories"
    }
}

/**
 * Returns the appropriate label for active time based on time range
 */
private fun getActiveLabel(timeRange: HealthTimeRange): String {
    return when (timeRange) {
        HealthTimeRange.Daily -> "Active"
        HealthTimeRange.Weekly, HealthTimeRange.Monthly -> "Avg Active"
    }
}
