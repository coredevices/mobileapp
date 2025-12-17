package coredevices.pebble.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import io.github.koalaplot.core.bar.DefaultBar
import io.github.koalaplot.core.bar.DefaultBarPosition
import io.github.koalaplot.core.bar.DefaultVerticalBarPlotEntry
import io.github.koalaplot.core.bar.VerticalBarPlot
import io.github.koalaplot.core.line.AreaBaseline
import io.github.koalaplot.core.line.AreaPlot2
import io.github.koalaplot.core.style.AreaStyle
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.AxisStyle
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.Point
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberFloatLinearAxisModel
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.health.OverlayType
import io.rebble.libpebblecommon.metadata.WatchType
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours

enum class HealthTimeRange {
    Daily, Weekly, Monthly
}

// Data structures for sleep charts
data class StackedSleepData(
    val label: String,
    val lightSleepHours: Float,
    val deepSleepHours: Float
)

data class SleepSegment(
    val startHour: Float,      // Hour of day (0-24)
    val durationHours: Float,
    val type: OverlayType      // Sleep or DeepSleep
)

data class DailySleepData(
    val segments: List<SleepSegment>,
    val bedtime: Float,        // Start hour
    val wakeTime: Float,       // End hour
    val totalSleepHours: Float
)

// Helper function to check if a year is a leap year
private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

// Helper function to get days in a month
private fun getDaysInMonth(month: Month, year: Int): Int {
    return when (month) {
        Month.JANUARY -> 31
        Month.FEBRUARY -> if (isLeapYear(year)) 29 else 28
        Month.MARCH -> 31
        Month.APRIL -> 30
        Month.MAY -> 31
        Month.JUNE -> 30
        Month.JULY -> 31
        Month.AUGUST -> 31
        Month.SEPTEMBER -> 30
        Month.OCTOBER -> 31
        Month.NOVEMBER -> 30
        Month.DECEMBER -> 31
        else -> 30
    }
}

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
            modifier = Modifier
                .fillMaxWidth()
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

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StepsChart(healthDao: HealthDao, timeRange: HealthTimeRange) {
    val scope = rememberCoroutineScope()
    var stepsData by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var totalSteps by remember { mutableStateOf(0L) }

    LaunchedEffect(timeRange) {
        scope.launch {
            val (labels, values, total) = fetchStepsData(healthDao, timeRange)
            stepsData = labels.zip(values)
            totalSteps = total
        }
    }

    if (stepsData.isNotEmpty()) {
        Column {
            Text(
                text = "${totalSteps.toInt()} steps",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(12.dp)) {
                when (timeRange) {
                    HealthTimeRange.Daily -> StepsDailyChart(stepsData)
                    HealthTimeRange.Weekly -> StepsWeeklyChart(stepsData)
                    HealthTimeRange.Monthly -> StepsMonthlyChart(stepsData)
                }
            }
        }
    } else {
        Text(
            text = "No steps data available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 32.dp)
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StepsDailyChart(data: List<Pair<String, Float>>) {
    val points = data.mapIndexed { index, (_, value) ->
        DefaultPoint(index.toFloat(), value)
    }
    val smoothedPoints = catmullRomSmooth(points, segments = 6)
    val maxY = data.maxOfOrNull { it.second }?.let { it * 1.1f } ?: 10f
    val maxX = (data.size - 1).toFloat().coerceAtLeast(1f)

    val labelProvider: (Float) -> String = { value: Float ->
        val index = value.roundToInt()
        if (abs(value - index) < 0.01f) {
            data.getOrNull(index)?.first ?: ""
        } else {
            ""
        }
    }

    XYGraph(
        modifier = Modifier.padding(horizontal = 10.dp),
        xAxisModel = rememberFloatLinearAxisModel(0f..maxX),
        yAxisModel = rememberFloatLinearAxisModel(0f..maxY),
        horizontalMajorGridLineStyle = null,
        horizontalMinorGridLineStyle = null,
        verticalMajorGridLineStyle = null,
        verticalMinorGridLineStyle = null,
        xAxisLabels = labelProvider,
        xAxisStyle = AxisStyle(
            color = Color.Transparent,
            majorTickSize = 0.dp,
            minorTickSize = 0.dp
        ),
        yAxisLabels = { "" },
        yAxisStyle = AxisStyle(
            color = Color.Transparent,
            majorTickSize = 0.dp,
            minorTickSize = 0.dp
        )
    ) {
        AreaPlot2(
            data = smoothedPoints,
            lineStyle = LineStyle(
                brush = SolidColor(MaterialTheme.colorScheme.primary),
                strokeWidth = 2.dp
            ),
            areaBaseline = AreaBaseline.ConstantLine(value = 0f),
            areaStyle = AreaStyle(
                brush = SolidColor(MaterialTheme.colorScheme.primary),
                alpha = 0.3f
            )
        )
    }
}

// Catmull-Rom spline (cubic) to approximate a bezier-like smooth line through the points
private fun catmullRomSmooth(
    points: List<Point<Float, Float>>,
    segments: Int = 8
): List<Point<Float, Float>> {
    if (points.size < 3 || segments <= 0) return points

    val smoothed = mutableListOf<Point<Float, Float>>()
    for (i in 0 until points.lastIndex) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]

        if (i == 0) smoothed += p1

        for (j in 1..segments) {
            val t = j / segments.toFloat()
            val t2 = t * t
            val t3 = t2 * t

            fun blend(v0: Float, v1: Float, v2: Float, v3: Float): Float {
                return 0.5f * (
                    (2f * v1) +
                        (-v0 + v2) * t +
                        (2f * v0 - 5f * v1 + 4f * v2 - v3) * t2 +
                        (-v0 + 3f * v1 - 3f * v2 + v3) * t3
                    )
            }

            val x = blend(p0.x, p1.x, p2.x, p3.x)
            val y = blend(p0.y, p1.y, p2.y, p3.y)
            // Clamp to avoid spline undershooting below origin or below the segment bounds
            val lowerBound = maxOf(0f, minOf(p1.y, p2.y))
            val upperBound = maxOf(p1.y, p2.y)
            smoothed += DefaultPoint(x, y.coerceIn(lowerBound, upperBound))
        }
    }

    return smoothed
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StepsWeeklyChart(data: List<Pair<String, Float>>) {
    val labels = data.map { it.first }
    val values = data.map { it.second }
    val maxY = values.maxOrNull()?.let { it * 1.1f } ?: 10f

    val barEntries = data.map { (label, value) ->
        DefaultVerticalBarPlotEntry(label, DefaultBarPosition(0f, value))
    }

    XYGraph(
        modifier = Modifier.padding(horizontal = 10.dp),
        xAxisModel = CategoryAxisModel(labels),
        yAxisModel = rememberFloatLinearAxisModel(0f..maxY),
        horizontalMajorGridLineStyle = null,
        horizontalMinorGridLineStyle = null,
        verticalMajorGridLineStyle = null,
        verticalMinorGridLineStyle = null,
        yAxisLabels = { "" },
        xAxisStyle = AxisStyle(
            color = Color.Transparent,
            minorTickSize = 0.dp
        ),
        yAxisStyle = AxisStyle(
            color = Color.Transparent,
            majorTickSize = 0.dp,
            minorTickSize = 0.dp
        )
    ) {
        VerticalBarPlot(
            barEntries,
            bar = { _, _, _ ->
                DefaultBar(
                    brush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StepsMonthlyChart(data: List<Pair<String, Float>>) {
    val labels = data.map { it.first }
    val values = data.map { it.second }
    val maxY = values.maxOrNull()?.let { it * 1.1f } ?: 10f

    val barEntries = data.map { (label, value) ->
        DefaultVerticalBarPlotEntry(label, DefaultBarPosition(0f, value))
    }

    XYGraph(
        modifier = Modifier.padding(horizontal = 10.dp),
        xAxisModel = CategoryAxisModel(labels),
        yAxisModel = rememberFloatLinearAxisModel(0f..maxY),
        horizontalMajorGridLineStyle = null,
        horizontalMinorGridLineStyle = null,
        verticalMajorGridLineStyle = null,
        verticalMinorGridLineStyle = null,
        yAxisLabels = { "" },
        xAxisStyle = AxisStyle(
            color = Color.Transparent,
            minorTickSize = 0.dp
        ),
        yAxisStyle = AxisStyle(
            color = Color.Transparent,
            majorTickSize = 0.dp,
            minorTickSize = 0.dp
        )
    ) {
        VerticalBarPlot(
            barEntries,
            bar = { _, _, _ ->
                DefaultBar(
                    brush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun HeartRateChart(healthDao: HealthDao, timeRange: HealthTimeRange) {
    val scope = rememberCoroutineScope()
    var hrData by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var avgHR by remember { mutableStateOf(0) }

    LaunchedEffect(timeRange) {
        scope.launch {
            val (labels, values, avg) = fetchHeartRateData(healthDao, timeRange)
            hrData = labels.zip(values)
            avgHR = avg
        }
    }

    if (avgHR > 0) {
        Column {
            Text(
                text = "$avgHR bpm avg",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(12.dp)) {
                when (timeRange) {
                    HealthTimeRange.Daily -> HeartRateDailyChart(hrData)
                    HealthTimeRange.Weekly -> HeartRateWeeklyChart(hrData)
                    HealthTimeRange.Monthly -> HeartRateMonthlyChart(hrData)
                }
            }
        }
    } else {
        Text(
            text = "No heart rate data available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 32.dp)
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun HeartRateDailyChart(data: List<Pair<String, Float>>) {
    val heartRateColor = Color(0xFFE91E63)
    val points = data.mapIndexed { index, (_, value) ->
        DefaultPoint(index.toFloat(), value)
    }
    val maxY = data.maxOfOrNull { it.second }?.let { it * 1.1f } ?: 100f
    val maxX = (data.size - 1).toFloat().coerceAtLeast(1f)

    XYGraph(
        modifier = Modifier.padding(horizontal = 10.dp),
        xAxisModel = rememberFloatLinearAxisModel(0f..maxX),
        yAxisModel = rememberFloatLinearAxisModel(0f..maxY)
    ) {
        AreaPlot2(
            data = points,
            lineStyle = LineStyle(
                brush = SolidColor(heartRateColor),
                strokeWidth = 2.dp
            ),
            areaBaseline = AreaBaseline.ConstantLine(0f),
            areaStyle = AreaStyle(
                brush = SolidColor(heartRateColor),
                alpha = 0.3f
            )
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun HeartRateWeeklyChart(data: List<Pair<String, Float>>) {
    val heartRateColor = Color(0xFFE91E63)
    val labels = data.map { it.first }
    val values = data.map { it.second }
    val maxY = values.maxOrNull()?.let { it * 1.1f } ?: 100f

    val barEntries = data.map { (label, value) ->
        DefaultVerticalBarPlotEntry(label, DefaultBarPosition(0f, value))
    }

    XYGraph(
        modifier = Modifier.padding(horizontal = 10.dp),
        xAxisModel = CategoryAxisModel(labels),
        yAxisModel = rememberFloatLinearAxisModel(0f..maxY)
    ) {
        VerticalBarPlot(
            barEntries,
            bar = { _, _, _ ->
                DefaultBar(
                    brush = SolidColor(heartRateColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun HeartRateMonthlyChart(data: List<Pair<String, Float>>) {
    val heartRateColor = Color(0xFFE91E63)
    val labels = data.map { it.first }
    val values = data.map { it.second }
    val maxY = values.maxOrNull()?.let { it * 1.1f } ?: 100f

    val barEntries = data.map { (label, value) ->
        DefaultVerticalBarPlotEntry(label, DefaultBarPosition(0f, value))
    }

    XYGraph(
        modifier = Modifier.padding(horizontal = 10.dp),
        xAxisModel = CategoryAxisModel(labels),
        yAxisModel = rememberFloatLinearAxisModel(0f..maxY)
    ) {
        VerticalBarPlot(
            barEntries,
            bar = { _, _, _ ->
                DefaultBar(
                    brush = SolidColor(heartRateColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun SleepChart(healthDao: HealthDao, timeRange: HealthTimeRange) {
    val scope = rememberCoroutineScope()
    var dailySleepData by remember { mutableStateOf<DailySleepData?>(null) }
    var stackedSleepData by remember { mutableStateOf<List<StackedSleepData>>(emptyList()) }
    var avgSleepHours by remember { mutableStateOf(0f) }

    LaunchedEffect(timeRange) {
        scope.launch {
            when (timeRange) {
                HealthTimeRange.Daily -> {
                    val (daily, avg) = fetchDailySleepData(healthDao)
                    dailySleepData = daily
                    avgSleepHours = avg
                }
                else -> {
                    val (stacked, avg) = fetchStackedSleepData(healthDao, timeRange)
                    stackedSleepData = stacked
                    avgSleepHours = avg
                }
            }
        }
    }

    if (avgSleepHours > 0 || (dailySleepData != null && dailySleepData!!.segments.isNotEmpty()) || stackedSleepData.isNotEmpty()) {
        Column {
            Text(
                text = "%.1f hours avg".format(avgSleepHours),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(12.dp)) {
                when (timeRange) {
                    HealthTimeRange.Daily -> dailySleepData?.let { SleepDailyChart(it) }
                    HealthTimeRange.Weekly -> SleepWeeklyChart(stackedSleepData)
                    HealthTimeRange.Monthly -> SleepMonthlyChart(stackedSleepData)
                }
            }
        }
    } else {
        Text(
            text = "No sleep data available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 32.dp)
        )
    }
}

@Composable
private fun SleepDailyChart(data: DailySleepData) {
    val lightSleepColor = Color(0xFF9C27B0).copy(alpha = 0.6f)
    val deepSleepColor = Color(0xFF9C27B0)

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        if (data.segments.isEmpty()) return@Canvas

        val totalHours = data.wakeTime - data.bedtime
        if (totalHours <= 0) return@Canvas

        val pixelsPerHour = size.width / totalHours
        val barHeight = 40.dp.toPx()
        val yCenter = size.height / 2

        data.segments.forEach { segment ->
            val startX = (segment.startHour - data.bedtime) * pixelsPerHour
            val width = segment.durationHours * pixelsPerHour

            val color = when (segment.type) {
                OverlayType.Sleep -> lightSleepColor
                OverlayType.DeepSleep -> deepSleepColor
                else -> Color.Transparent
            }

            drawRect(
                color = color,
                topLeft = Offset(startX, yCenter - barHeight / 2),
                size = Size(width, barHeight)
            )
        }
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun SleepWeeklyChart(data: List<StackedSleepData>) {
    if (data.isEmpty()) return

    val lightSleepColor = Color(0xFF9C27B0).copy(alpha = 0.6f)
    val deepSleepColor = Color(0xFF9C27B0)
    val labels = data.map { it.label }
    val lightSleepValues = data.map { it.lightSleepHours }
    val deepSleepValues = data.map { it.deepSleepHours }
    val maxY = data.maxOfOrNull { it.lightSleepHours + it.deepSleepHours }?.let { it * 1.1f } ?: 10f

    val lightBarEntries = data.map { item ->
        DefaultVerticalBarPlotEntry(item.label, DefaultBarPosition(0f, item.lightSleepHours))
    }
    val deepBarEntries = data.mapIndexed { idx, item ->
        DefaultVerticalBarPlotEntry(item.label, DefaultBarPosition(lightSleepValues[idx], lightSleepValues[idx] + item.deepSleepHours))
    }

    XYGraph(
        xAxisModel = CategoryAxisModel(labels),
        yAxisModel = rememberFloatLinearAxisModel(0f..maxY),
        horizontalMajorGridLineStyle = null,
        horizontalMinorGridLineStyle = null,
        verticalMajorGridLineStyle = null,
        verticalMinorGridLineStyle = null,
        yAxisLabels = { "" },
        xAxisStyle = AxisStyle(
            color = Color.Transparent,
            minorTickSize = 0.dp
        ),
        yAxisStyle = AxisStyle(
            color = Color.Transparent,
            majorTickSize = 0.dp,
            minorTickSize = 0.dp
        )
    ) {
        // Draw light sleep bars first (bottom layer)
        VerticalBarPlot(
            lightBarEntries,
            bar = { _, _, _ ->
                DefaultBar(
                    brush = SolidColor(lightSleepColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
        // Draw deep sleep bars on top
        VerticalBarPlot(
            deepBarEntries,
            bar = { _, _, _ ->
                DefaultBar(
                    brush = SolidColor(deepSleepColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun SleepMonthlyChart(data: List<StackedSleepData>) {
    if (data.isEmpty()) return

    val lightSleepColor = Color(0xFF9C27B0).copy(alpha = 0.6f)
    val deepSleepColor = Color(0xFF9C27B0)
    val labels = data.map { it.label }
    val lightSleepValues = data.map { it.lightSleepHours }
    val deepSleepValues = data.map { it.deepSleepHours }
    val maxY = data.maxOfOrNull { it.lightSleepHours + it.deepSleepHours }?.let { it * 1.1f } ?: 10f

    val lightBarEntries = data.map { item ->
        DefaultVerticalBarPlotEntry(item.label, DefaultBarPosition(0f, item.lightSleepHours))
    }
    val deepBarEntries = data.mapIndexed { idx, item ->
        DefaultVerticalBarPlotEntry(item.label, DefaultBarPosition(lightSleepValues[idx], lightSleepValues[idx] + item.deepSleepHours))
    }

    XYGraph(
        xAxisModel = CategoryAxisModel(labels),
        yAxisModel = rememberFloatLinearAxisModel(0f..maxY),
        horizontalMajorGridLineStyle = null,
        horizontalMinorGridLineStyle = null,
        verticalMajorGridLineStyle = null,
        verticalMinorGridLineStyle = null,
        yAxisLabels = { "" },
        xAxisStyle = AxisStyle(
            color = Color.Transparent,
            minorTickSize = 0.dp
        ),
        yAxisStyle = AxisStyle(
            color = Color.Transparent,
            majorTickSize = 0.dp,
            minorTickSize = 0.dp
        )
    ) {
        // Draw light sleep bars first (bottom layer)
        VerticalBarPlot(
            lightBarEntries,
            bar = { _, _, _ ->
                DefaultBar(
                    brush = SolidColor(lightSleepColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
        // Draw deep sleep bars on top
        VerticalBarPlot(
            deepBarEntries,
            bar = { _, _, _ ->
                DefaultBar(
                    brush = SolidColor(deepSleepColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

fun roundToNearestHour(instant: Instant, timeZone: TimeZone): Instant {
    val localDateTime = instant.toLocalDateTime(timeZone)
    val secondsPastHour = localDateTime.minute * 60 + localDateTime.second
    val hourStart = localDateTime.date.atTime(localDateTime.hour, 0, 0)
    val hourStartInstant = hourStart.toInstant(timeZone)
    return if (secondsPastHour >= 30 * 60) {
        hourStartInstant + 1.hours
    } else {
        hourStartInstant
    }
}

// Data fetching functions
private suspend fun fetchStepsData(
    healthDao: HealthDao,
    timeRange: HealthTimeRange
): Triple<List<String>, List<Float>, Long> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date

    return when (timeRange) {
        HealthTimeRange.Daily -> {
            val nowInstant = Clock.System.now()
            val todayStart = today.atStartOfDayIn(timeZone).epochSeconds

            // Get wakeup time from sleep data (if available)
            val searchStart = today.minus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds + (18 * 3600)
            val searchEnd = today.atStartOfDayIn(timeZone).epochSeconds + (14 * 3600)
            val sleepTypes = listOf(OverlayType.Sleep.value, OverlayType.DeepSleep.value)
            val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, sleepTypes)
                .sortedBy { it.startTime }

            val wakeupInstant = sleepEntries.maxOfOrNull { it.startTime + it.duration }
                ?.let { Instant.fromEpochSeconds(it) }
            val fallbackWakeup = today.atStartOfDayIn(timeZone) + 6.hours
            val wakeCandidate = wakeupInstant ?: fallbackWakeup
            val dayStartInstant = today.atStartOfDayIn(timeZone)
            var startInstant = wakeCandidate
            if (startInstant > nowInstant) startInstant = nowInstant
            if (startInstant < dayStartInstant) startInstant = dayStartInstant
            startInstant = roundToNearestHour(startInstant, timeZone)

            // Sample once per hour from wakeup to "now", plus an initial point at wakeup and a final point at current time.
            val labels = mutableListOf<String>()
            val values = mutableListOf<Float>()
            val sampleTimes = generateSequence(startInstant) { it + 1.hours }
                .takeWhile { it < nowInstant }
                .toMutableList()
            sampleTimes += nowInstant

            sampleTimes.forEach { instant ->
                val label = formatTimeLabel(instant, timeZone)
                val steps = healthDao.getTotalStepsExclusiveEnd(todayStart, instant.epochSeconds) ?: 0L
                labels.add(label)
                values.add(steps.toFloat())
            }

            val todayEnd = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
            val total = healthDao.getTotalStepsExclusiveEnd(todayStart, todayEnd) ?: 0L
            Triple(labels, values, total)
        }

        HealthTimeRange.Weekly -> {
            val labels = mutableListOf<String>()
            val values = mutableListOf<Float>()
            var total = 0L

            repeat(7) { offset ->
                val day = today.minus(DatePeriod(days = 6 - offset))
                labels.add(day.dayOfWeek.name.take(3))

                val start = day.atStartOfDayIn(timeZone).epochSeconds
                val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
                val steps = healthDao.getTotalStepsExclusiveEnd(start, end) ?: 0L
                values.add(steps.toFloat())
                total += steps
            }
            Triple(labels, values, total)
        }

        HealthTimeRange.Monthly -> {
            // Last 12 months with average steps per month
            val labels = mutableListOf<String>()
            val values = mutableListOf<Float>()
            var total = 0L

            repeat(12) { monthOffset ->
                val monthDate = today.minus(DatePeriod(months = 11 - monthOffset))
                val monthStart = LocalDate(monthDate.year, monthDate.month, 1)
                labels.add(monthStart.month.name.take(3))

                // Calculate days in this month
                val monthEnd = monthStart.plus(DatePeriod(months = 1))
                val daysInMonth = getDaysInMonth(monthStart.month, monthStart.year)

                val start = monthStart.atStartOfDayIn(timeZone).epochSeconds
                val end = monthEnd.atStartOfDayIn(timeZone).epochSeconds
                val monthSteps = healthDao.getTotalStepsExclusiveEnd(start, end) ?: 0L
                val avgSteps = if (daysInMonth > 0) (monthSteps.toFloat() / daysInMonth) else 0f

                values.add(avgSteps)
                total += monthSteps
            }
            Triple(labels, values, total)
        }
    }
}

private fun formatTimeLabel(instant: Instant, timeZone: TimeZone): String {
    val localTime = instant.toLocalDateTime(timeZone).time
    val hour24 = localTime.hour
    val minute = localTime.minute
    val amPm = if (hour24 >= 12) "PM" else "AM"
    val displayHour = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }

    return if (minute == 0) {
        "$displayHour$amPm"
    } else {
        "%d:%02d%s".format(displayHour, minute, amPm)
    }
}

private suspend fun fetchHeartRateData(
    healthDao: HealthDao,
    timeRange: HealthTimeRange
): Triple<List<String>, List<Float>, Int> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date

    return when (timeRange) {
        HealthTimeRange.Daily -> {
            val labels = (0..23).map { hour -> String.format("%02d:00", hour) }
            val values = List(24) { 0f }
            Triple(labels, values, 0)
        }

        HealthTimeRange.Weekly -> {
            val labels = mutableListOf<String>()
            val values = mutableListOf<Float>()
            var count = 0
            var sum = 0

            repeat(7) { offset ->
                val day = today.minus(DatePeriod(days = 6 - offset))
                labels.add(day.dayOfWeek.name.take(3))

                val start = day.atStartOfDayIn(timeZone).epochSeconds
                val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds

                // Get average HR for this day from health_data table
                val avgHR = healthDao.getAverageSteps(start, end)?.toInt() ?: 0
                values.add(avgHR.toFloat())
                if (avgHR > 0) {
                    sum += avgHR
                    count++
                }
            }
            val avg = if (count > 0) sum / count else 0
            Triple(labels, values, avg)
        }

        HealthTimeRange.Monthly -> {
            // Last 12 months with average HR per month
            val labels = mutableListOf<String>()
            val values = mutableListOf<Float>()
            var totalCount = 0
            var totalSum = 0

            repeat(12) { monthOffset ->
                val monthDate = today.minus(DatePeriod(months = 11 - monthOffset))
                val monthStart = LocalDate(monthDate.year, monthDate.month, 1)
                labels.add(monthStart.month.name.take(3))

                val monthEnd = monthStart.plus(DatePeriod(months = 1))
                val start = monthStart.atStartOfDayIn(timeZone).epochSeconds
                val end = monthEnd.atStartOfDayIn(timeZone).epochSeconds

                // Get average HR for this month (placeholder - actual implementation depends on heart rate data structure)
                val avgHR = healthDao.getAverageSteps(start, end)?.toInt() ?: 0
                values.add(avgHR.toFloat())
                if (avgHR > 0) {
                    totalSum += avgHR
                    totalCount++
                }
            }
            val avg = if (totalCount > 0) totalSum / totalCount else 0
            Triple(labels, values, avg)
        }
    }
}

private suspend fun fetchDailySleepData(
    healthDao: HealthDao
): Pair<DailySleepData?, Float> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date

    // Search from 6 PM yesterday to 2 PM today
    val searchStart = today.minus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds + (18 * 3600)
    val searchEnd = today.atStartOfDayIn(timeZone).epochSeconds + (14 * 3600)

    val sleepTypes = listOf(OverlayType.Sleep.value, OverlayType.DeepSleep.value)
    val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, sleepTypes)
        .sortedBy { it.startTime }

    if (sleepEntries.isEmpty()) {
        return Pair(null, 0f)
    }

    val segments = mutableListOf<SleepSegment>()
    var bedtime = Float.MAX_VALUE
    var wakeTime = 0f
    var totalSleepSeconds = 0L

    sleepEntries.forEach { entry ->
        val type = OverlayType.fromValue(entry.type)
        val startHour = ((entry.startTime - searchStart) / 3600f) + 18f // Offset to 6 PM = hour 18
        val durationHours = entry.duration / 3600f

        segments.add(SleepSegment(startHour, durationHours, type ?: OverlayType.Sleep))
        bedtime = minOf(bedtime, startHour)
        wakeTime = maxOf(wakeTime, startHour + durationHours)

        if (type == OverlayType.Sleep || type == OverlayType.DeepSleep) {
            totalSleepSeconds += entry.duration
        }
    }

    val totalSleepHours = totalSleepSeconds / 3600f
    val dailyData = DailySleepData(segments, bedtime, wakeTime, totalSleepHours)

    return Pair(dailyData, totalSleepHours)
}

private suspend fun fetchStackedSleepData(
    healthDao: HealthDao,
    timeRange: HealthTimeRange
): Pair<List<StackedSleepData>, Float> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date

    return when (timeRange) {
        HealthTimeRange.Weekly -> {
            val stackedData = mutableListOf<StackedSleepData>()
            var totalHours = 0f

            repeat(7) { offset ->
                val day = today.minus(DatePeriod(days = 6 - offset))
                val label = day.dayOfWeek.name.take(3)

                val searchStart = day.minus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds + (18 * 3600)
                val searchEnd = day.atStartOfDayIn(timeZone).epochSeconds + (14 * 3600)

                val sleepTypes = listOf(OverlayType.Sleep.value, OverlayType.DeepSleep.value)
                val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, sleepTypes)

                val lightSleepSeconds = sleepEntries.filter { OverlayType.fromValue(it.type) == OverlayType.Sleep }
                    .sumOf { it.duration }
                val deepSleepSeconds = sleepEntries.filter { OverlayType.fromValue(it.type) == OverlayType.DeepSleep }
                    .sumOf { it.duration }

                val lightSleepHours = lightSleepSeconds / 3600f
                val deepSleepHours = deepSleepSeconds / 3600f

                stackedData.add(StackedSleepData(label, lightSleepHours, deepSleepHours))
                totalHours += (lightSleepHours + deepSleepHours)
            }

            val avg = totalHours / 7f
            Pair(stackedData, avg)
        }

        HealthTimeRange.Monthly -> {
            // Last 12 months with average sleep per month
            val stackedData = mutableListOf<StackedSleepData>()
            var totalHours = 0f
            var totalDays = 0

            repeat(12) { monthOffset ->
                val monthDate = today.minus(DatePeriod(months = 11 - monthOffset))
                val monthStart = LocalDate(monthDate.year, monthDate.month, 1)
                val label = monthStart.month.name.take(3)

                val monthEnd = monthStart.plus(DatePeriod(months = 1))
                val daysInMonth = getDaysInMonth(monthStart.month, monthStart.year)

                var monthLightSleepSeconds = 0L
                var monthDeepSleepSeconds = 0L

                // Sum all sleep for the month
                repeat(daysInMonth) { dayOffset ->
                    val day = monthStart.plus(DatePeriod(days = dayOffset))
                    val searchStart = day.minus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds + (18 * 3600)
                    val searchEnd = day.atStartOfDayIn(timeZone).epochSeconds + (14 * 3600)

                    val sleepTypes = listOf(OverlayType.Sleep.value, OverlayType.DeepSleep.value)
                    val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, sleepTypes)

                    monthLightSleepSeconds += sleepEntries.filter { OverlayType.fromValue(it.type) == OverlayType.Sleep }
                        .sumOf { it.duration }
                    monthDeepSleepSeconds += sleepEntries.filter { OverlayType.fromValue(it.type) == OverlayType.DeepSleep }
                        .sumOf { it.duration }
                }

                val avgLightSleepHours = if (daysInMonth > 0) (monthLightSleepSeconds / 3600f) / daysInMonth else 0f
                val avgDeepSleepHours = if (daysInMonth > 0) (monthDeepSleepSeconds / 3600f) / daysInMonth else 0f

                stackedData.add(StackedSleepData(label, avgLightSleepHours, avgDeepSleepHours))
                totalHours += (monthLightSleepSeconds + monthDeepSleepSeconds) / 3600f
                totalDays += daysInMonth
            }

            val avg = if (totalDays > 0) totalHours / totalDays else 0f
            Pair(stackedData, avg)
        }

        else -> Pair(emptyList(), 0f)
    }
}
