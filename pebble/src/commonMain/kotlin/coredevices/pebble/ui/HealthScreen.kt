package coredevices.pebble.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.ui.health.SleepChart
import coredevices.pebble.ui.health.StepsChart
import io.rebble.libpebblecommon.health.HealthTimeRange
import io.rebble.libpebblecommon.health.getDateRangeLabel
import io.rebble.libpebblecommon.health.getPreviousSunday
import io.rebble.libpebblecommon.database.dao.HealthDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import theme.localHealthColors

/**
 * Main Health screen showing steps, heart rate, and sleep data.
 * Refactored to be more modular and memory-efficient.
 */
@Composable
fun HealthScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams
) {
    val libPebble = rememberLibPebble()
    val healthDao: HealthDao = koinInject()
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(false)
        topBarParams.actions {
            // Sync button with animation
            IconButton(
                onClick = {
                    scope.launch {
                        isSyncing = true

                        // Get baseline timestamp before sync
                        val baselineTimestamp = healthDao.getLatestTimestamp() ?: 0L

                        // Trigger the force sync
                        libPebble.forceSyncLast24Hours()

                        // Poll for new data (timeout after 10 seconds)
                        var attempts = 0
                        val maxAttempts = 20 // 20 attempts * 500ms = 10 seconds
                        var newDataReceived = false

                        while (attempts < maxAttempts && !newDataReceived) {
                            delay(500)
                            val currentTimestamp = healthDao.getLatestTimestamp() ?: 0L
                            if (currentTimestamp > baselineTimestamp) {
                                newDataReceived = true
                                // Give it a bit more time for all data to process
                                delay(1000)
                            }
                            attempts++
                        }

                        isSyncing = false
                    }
                },
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    val infiniteTransition = rememberInfiniteTransition(label = "sync")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = "Syncing...",
                        modifier = Modifier.rotate(rotation)
                    )
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync Health Data")
                }
            }

            IconButton(onClick = { navBarNav.navigateTo(PebbleNavBarRoutes.HealthSettingsRoute) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Health Settings")
            }
        }
        topBarParams.title("Health")
        topBarParams.canGoBack(false)

        // Notify that HealthScreen is active - enables real-time health data updates
        libPebble.setHealthScreenActive(true)
    }

    // Clean up when screen is disposed
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            libPebble.setHealthScreenActive(false)
        }
    }

    var timeRange by remember { mutableStateOf(HealthTimeRange.Daily) }
    var offset by remember(timeRange) { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(true) }

    val healthColors = localHealthColors.current
    val timeZone = TimeZone.currentSystemDefault()

    // Check if we can go back (if there's data in the previous period)
    LaunchedEffect(timeRange, offset) {
        val nextOffset = offset + 1
        val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date

        val targetDate = when (timeRange) {
            HealthTimeRange.Daily -> today.minus(DatePeriod(days = nextOffset))
            HealthTimeRange.Weekly -> today.minus(DatePeriod(days = nextOffset * 7))
            HealthTimeRange.Monthly -> today.minus(DatePeriod(months = nextOffset))
        }

        // Check for steps data for the target day/week/month
        val start = when (timeRange) {
            HealthTimeRange.Daily -> targetDate.atStartOfDayIn(timeZone).epochSeconds
            HealthTimeRange.Weekly -> {
                val weekStart = getPreviousSunday(targetDate)
                weekStart.atStartOfDayIn(timeZone).epochSeconds
            }
            HealthTimeRange.Monthly -> {
                val monthStart = LocalDate(targetDate.year, targetDate.month, 1)
                monthStart.atStartOfDayIn(timeZone).epochSeconds
            }
        }

        val end = when (timeRange) {
            HealthTimeRange.Daily -> targetDate.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
            HealthTimeRange.Weekly -> {
                val weekStart = getPreviousSunday(targetDate)
                weekStart.plus(DatePeriod(days = 7)).atStartOfDayIn(timeZone).epochSeconds
            }
            HealthTimeRange.Monthly -> {
                val monthStart = LocalDate(targetDate.year, targetDate.month, 1)
                monthStart.plus(DatePeriod(months = 1)).atStartOfDayIn(timeZone).epochSeconds
            }
        }

        // Check if there's ANY meaningful data (steps > 0 or sleep entries) in the range
        val hasStepsData = (healthDao.getTotalStepsExclusiveEnd(start, end) ?: 0L) > 0
        val sleepTypes = listOf(1, 2) // Sleep and DeepSleep overlay types
        val hasSleepData = healthDao.getOverlayEntries(start, end, sleepTypes).isNotEmpty()

        canGoBack = hasStepsData || hasSleepData
    }

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

        // Date range navigation
        DateRangeNavigation(
            timeRange = timeRange,
            offset = offset,
            canGoBack = canGoBack,
            onOffsetChange = { offset = it }
        )

        // Steps chart
        HealthMetricCard(
            title = "Activity",
            icon = Icons.Filled.DirectionsRun,
            iconTint = healthColors.steps
        ) {
            StepsChart(healthDao, timeRange, offset)
        }

        // Sleep chart
        HealthMetricCard(
            title = "Sleep",
            icon = Icons.Filled.Hotel,
            iconTint = healthColors.lightSleep
        ) {
            SleepChart(healthDao, timeRange, offset)
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
 * Date range navigation with arrows and current period label.
 */
@Composable
private fun DateRangeNavigation(
    timeRange: HealthTimeRange,
    offset: Int,
    canGoBack: Boolean,
    onOffsetChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onOffsetChange(offset + 1) },
            enabled = canGoBack
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous ${timeRange.name.lowercase()}",
                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        Text(
            text = getDateRangeLabel(timeRange, offset, TimeZone.currentSystemDefault()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        IconButton(
            onClick = { onOffsetChange(offset - 1) },
            enabled = offset > 0
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next ${timeRange.name.lowercase()}",
                tint = if (offset > 0) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
