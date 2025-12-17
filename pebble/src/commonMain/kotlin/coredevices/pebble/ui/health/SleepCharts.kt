package coredevices.pebble.ui.health

import io.rebble.libpebblecommon.health.DailySleepData
import io.rebble.libpebblecommon.health.HealthTimeRange
import io.rebble.libpebblecommon.health.StackedSleepData
import io.rebble.libpebblecommon.health.fetchDailySleepData
import io.rebble.libpebblecommon.health.fetchStackedSleepData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberFloatLinearAxisModel
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.health.OverlayType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val lightSleepColor = Color(0xFF9C27B0).copy(alpha = 0.6f)
private val deepSleepColor = Color(0xFF9C27B0)
private val bottomBar = RoundedCornerShape(0.dp, 0.dp, 5.dp, 5.dp)

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun SleepChart(healthDao: HealthDao, timeRange: HealthTimeRange) {
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
                    shape = bottomBar,
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
                    shape = topBar,
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

    // Convert to indexed points (weekly aggregated data)
    val totalSleepPoints = data.mapIndexed { index, item ->
        DefaultPoint(index.toFloat(), item.lightSleepHours + item.deepSleepHours)
    }
    val deepSleepPoints = data.mapIndexed { index, item ->
        DefaultPoint(index.toFloat(), item.deepSleepHours)
    }

    val smoothedTotalSleep = catmullRomSmooth(totalSleepPoints, segments = 6)
    val smoothedDeepSleep = catmullRomSmooth(deepSleepPoints, segments = 6)

    val maxY = data.maxOfOrNull { it.lightSleepHours + it.deepSleepHours }?.let { it * 1.1f } ?: 10f
    val minY = data.minOfOrNull { it.deepSleepHours }?.let { (it * 0.9f).coerceAtLeast(0f) } ?: 0f
    val maxX = (data.size - 1).toFloat().coerceAtLeast(1f)

    val labelProvider: (Float) -> String = { value: Float ->
        val index = value.roundToInt()
        if (abs(value - index) < 0.01f) {
            data.getOrNull(index)?.label ?: ""
        } else {
            ""
        }
    }

    XYGraph(
        modifier = Modifier.padding(horizontal = 10.dp),
        xAxisModel = rememberFloatLinearAxisModel(
            range = 0f..maxX,
            minimumMajorTickIncrement = 1f,
            minorTickCount = 0
        ),
        yAxisModel = rememberFloatLinearAxisModel(minY..maxY),
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
        // Draw total sleep (light sleep color) as bottom layer
        AreaPlot2(
            data = smoothedTotalSleep,
            lineStyle = LineStyle(
                brush = SolidColor(lightSleepColor),
                strokeWidth = 2.dp
            ),
            areaBaseline = AreaBaseline.ConstantLine(minY),
            areaStyle = AreaStyle(
                brush = SolidColor(lightSleepColor),
                alpha = 0.6f
            )
        )
        // Draw deep sleep on top
        AreaPlot2(
            data = smoothedDeepSleep,
            lineStyle = LineStyle(
                brush = SolidColor(deepSleepColor),
                strokeWidth = 2.dp
            ),
            areaBaseline = AreaBaseline.ConstantLine(minY),
            areaStyle = AreaStyle(
                brush = SolidColor(deepSleepColor),
                alpha = 0.8f
            )
        )
    }
}
