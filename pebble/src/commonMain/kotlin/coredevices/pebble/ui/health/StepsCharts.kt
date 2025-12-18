package coredevices.pebble.ui.health

import androidx.compose.foundation.layout.Arrangement
import io.rebble.libpebblecommon.health.HealthTimeRange
import io.rebble.libpebblecommon.health.fetchStepsData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import theme.localHealthColors
import kotlin.math.abs
import kotlin.math.roundToInt
import coredevices.pebble.ui.PreviewWrapper
import org.jetbrains.compose.ui.tooling.preview.Preview
import coredevices.pebble.rememberLibPebble
import io.rebble.libpebblecommon.health.HealthSettings

val topBar = RoundedCornerShape(5.dp, 5.dp, 0.dp, 0.dp)

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun StepsChart(healthDao: HealthDao, timeRange: HealthTimeRange) {
    val libPebble = rememberLibPebble()
    val healthSettings by libPebble.healthSettings.collectAsState(initial = HealthSettings())

    var stepsData by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var totalSteps by remember { mutableStateOf(0L) }
    var metrics by remember { mutableStateOf<StepsMetrics?>(null) }

    LaunchedEffect(timeRange, healthSettings.imperialUnits) {
        val (labels, values, total) = fetchStepsData(healthDao, timeRange)
        stepsData = labels.zip(values)
        totalSteps = total

        // Fetch metrics only if there's data
        if (stepsData.isNotEmpty()) {
            metrics = fetchStepsMetrics(healthDao, timeRange, healthSettings.imperialUnits)
        } else {
            metrics = null
        }
    }

    if (stepsData.isNotEmpty()) {
        Column {
            Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(12.dp)) {
                when (timeRange) {
                    HealthTimeRange.Daily -> StepsDailyChart(stepsData)
                    HealthTimeRange.Weekly -> StepsWeeklyChart(stepsData)
                    HealthTimeRange.Monthly -> StepsMonthlyChart(stepsData)
                }
            }

            // Display metrics if available
            metrics?.let {
                StepsMetricsRow(
                    metrics = it,
                    timeRange = timeRange
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "No steps data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        }
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StepsDailyChart(data: List<Pair<String, Float>>) {
    val healthColors = localHealthColors.current
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
        modifier = Modifier.padding(horizontal = 20.dp),
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
                brush = SolidColor(healthColors.steps),
                strokeWidth = 2.dp
            ),
            areaBaseline = AreaBaseline.ConstantLine(value = 0f),
            areaStyle = AreaStyle(
                brush = SolidColor(healthColors.steps),
                alpha = 0.3f
            )
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StepsWeeklyChart(data: List<Pair<String, Float>>) {
    if (data.isEmpty()) return
    val healthColors = localHealthColors.current
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
                    brush = SolidColor(healthColors.steps),
                    shape = topBar,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StepsMonthlyChart(data: List<Pair<String, Float>>) {
    if (data.isEmpty()) return
    val healthColors = localHealthColors.current

    val points = data.mapIndexed { index, (_, value) ->
        DefaultPoint(index.toFloat(), value)
    }

    val smoothedPoints = catmullRomSmooth(points, segments = 6)
    val maxY = data.maxOfOrNull { it.second }?.let { it * 1.1f } ?: 10f
    val minY = data.minOfOrNull { it.second }?.let { (it * 0.9f).coerceAtLeast(0f) } ?: 0f
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
        AreaPlot2(
            data = smoothedPoints,
            lineStyle = LineStyle(
                brush = SolidColor(healthColors.steps),
                strokeWidth = 2.dp
            ),
            areaBaseline = AreaBaseline.ConstantLine(value = minY),
            areaStyle = AreaStyle(
                brush = SolidColor(healthColors.steps),
                alpha = 0.3f
            )
        )
    }
}

// Preview composables with fake data
@Preview
@Composable
private fun StepsDailyChartPreview() {
    PreviewWrapper {
        val fakeDailyData = listOf(
            "12am" to 0f,
            "3am" to 0f,
            "6am" to 120f,
            "9am" to 850f,
            "12pm" to 2500f,
            "3pm" to 4200f,
            "6pm" to 6800f,
            "9pm" to 8500f,
            "11pm" to 9200f
        )
        Column {
            Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(12.dp)) {
                StepsDailyChart(fakeDailyData)
            }
            Row (modifier = Modifier.padding(10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                StatsTile("Distance", "8.3mi")
                StatsTile("Calories", "2345")
                StatsTile("Active", "2h 38m")
            }
        }
    }
}

@Preview
@Composable
private fun StepsWeeklyChartPreview() {
    PreviewWrapper {
        val fakeData = listOf(
            "Mon" to 8500f,
            "Tue" to 9200f,
            "Wed" to 7800f,
            "Thu" to 10500f,
            "Fri" to 8900f,
            "Sat" to 12000f,
            "Sun" to 6500f
        )
        Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(12.dp)) {
            StepsWeeklyChart(fakeData)
        }
    }
}

@Preview
@Composable
private fun StepsMonthlyChartPreview() {
    PreviewWrapper {
        val fakeData = listOf(
            "W1" to 8200f,
            "W2" to 9500f,
            "W3" to 8800f,
            "W4" to 10200f
        )
        Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(12.dp)) {
            StepsMonthlyChart(fakeData)
        }
    }
}
