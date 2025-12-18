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
 * Displays a row of metrics (hours slept, deep sleep) for sleep data
 */
@Composable
internal fun SleepMetricsRow(
    metrics: SleepMetrics,
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
            label = getHoursSleptLabel(timeRange),
            value = metrics.hoursSlept,
            modifier = Modifier.weight(1f)
        )
        StatsTile(
            label = getDeepSleepLabel(timeRange),
            value = metrics.deepSleep,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Returns the appropriate label for hours slept based on time range
 */
private fun getHoursSleptLabel(timeRange: HealthTimeRange): String {
    return when (timeRange) {
        HealthTimeRange.Daily -> "Hours Slept"
        HealthTimeRange.Weekly, HealthTimeRange.Monthly -> "Avg Hours"
    }
}

/**
 * Returns the appropriate label for deep sleep based on time range
 */
private fun getDeepSleepLabel(timeRange: HealthTimeRange): String {
    return when (timeRange) {
        HealthTimeRange.Daily -> "Deep Sleep"
        HealthTimeRange.Weekly, HealthTimeRange.Monthly -> "Avg Deep Sleep"
    }
}
