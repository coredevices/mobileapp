package coredevices.pebble.ui.health

import io.rebble.libpebblecommon.health.HealthTimeRange
import io.rebble.libpebblecommon.health.fetchHeartRateData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val heartRateColor = Color(0xFFE91E63)

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun HeartRateChart(healthDao: HealthDao, timeRange: HealthTimeRange) {
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
                    shape = topBar,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun HeartRateMonthlyChart(data: List<Pair<String, Float>>) {
    if (data.isEmpty()) return

    val points = data.mapIndexed { index, (_, value) ->
        DefaultPoint(index.toFloat(), value)
    }

    val smoothedPoints = catmullRomSmooth(points, segments = 6)
    val maxY = data.maxOfOrNull { it.second }?.let { it * 1.1f } ?: 100f
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
                brush = SolidColor(heartRateColor),
                strokeWidth = 2.dp
            ),
            areaBaseline = AreaBaseline.ConstantLine(minY),
            areaStyle = AreaStyle(
                brush = SolidColor(heartRateColor),
                alpha = 0.3f
            )
        )
    }
}
