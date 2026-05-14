package coredevices.pebble.ui

import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rebble.libpebblecommon.health.OverlayType

internal val ActivityHeaderColor = Color(0xFF00A982)
internal val ActivityBgColor = Color(0xFF009579)
internal val SleepHeaderColor = Color(0xFF065C91)
internal val SleepBgColor = Color(0xFF014981)
internal val HRHeaderColor = Color(0xFF7C33A5)
internal val HRBgColor = Color(0xFF6A1B9A)

internal val ActivityFillColor = Color(0xFF00C896)
internal val ActivityBarColor = Color(0xFFA8E6CF)
internal val TypicalFillColor = Color(0xFFB8D86B)
internal val TypicalBadgeColor = Color(0xFF8BC34A)
internal val LightSleepBarColor = Color(0xFF5B9BD5)
internal val DeepSleepBarColor = Color(0xFF0A1F38)
internal val HRLineColor = Color(0xFFF0437D)
internal val HRZone1Color = Color(0xFFF0437D)
internal val HRZone2Color = Color(0xFF179AC6)
internal val HRZone3Color = Color(0xFF00C3FD)

internal val BarAlpha = 0.85f
internal val BarAlphaSelected = 1.0f
internal val BarAlphaDimmed = 0.45f
internal val ChartOverlayColor = Color.White.copy(alpha = 0.08f)
internal val ScrubLineColor = Color.White.copy(alpha = 0.7f)
internal val AxisLabelColor = Color.White.copy(alpha = 0.5f)
internal val CardShape = RoundedCornerShape(4.dp)

internal class ScrubState { var scrubIndex by mutableStateOf<Int?>(null) }

@Composable
internal fun rememberScrubState() = remember { ScrubState() }

internal fun Modifier.scrubGesture(n: Int, s: ScrubState): Modifier = this
    .pointerInput(n) {
        detectTapGestures { off ->
            if (n <= 0) return@detectTapGestures
            val i = (off.x / size.width * n).toInt().coerceIn(0, n - 1)
            s.scrubIndex = if (s.scrubIndex == i) null else i
        }
    }
    .pointerInput(n) {
        detectHorizontalDragGestures(
            onDragStart = { off -> if (n > 0) s.scrubIndex = (off.x / size.width * n).toInt().coerceIn(0, n - 1) },
            onDragEnd = {}, onDragCancel = {},
        ) { ch, _ -> if (n > 0) s.scrubIndex = (ch.position.x / size.width * n).toInt().coerceIn(0, n - 1) }
    }

@Composable
internal fun AreaChart(
    values: List<Long>, labels: List<String>, typical: List<Long>,
    scrub: ScrubState, tm: androidx.compose.ui.text.TextMeasurer,
    sessions: List<ActivitySessionUi>,
) {
    val maxV = maxOf(values.maxOrNull() ?: 1L, if (typical.isNotEmpty()) typical.max() else 1L).coerceAtLeast(1L)
    val ls = TextStyle(fontSize = 9.sp, color = AxisLabelColor)
    val si = scrub.scrubIndex

    Canvas(Modifier.fillMaxWidth().height(160.dp).scrubGesture(values.size, scrub)) {
        val lH = 14.dp.toPx(); val sH = 6.dp.toPx(); val cH = size.height - lH - sH
        val stepX = size.width / values.size.coerceAtLeast(1)

        drawRect(ChartOverlayColor, Offset.Zero, Size(size.width, cH))

        // Typical fill
        if (typical.size == values.size) {
            val typicalPts = typical.mapIndexed { i, v ->
                Offset(i * stepX + stepX / 2, cH - (v.toFloat() / maxV * cH))
            }
            if (typicalPts.size >= 2) {
                drawPath(smoothFilledPath(typicalPts, cH), TypicalFillColor.copy(alpha = 0.3f))
                drawPath(smoothLinePath(typicalPts), TypicalFillColor.copy(alpha = 0.6f),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
        }

        // Main fill + line
        val mainPts = values.mapIndexed { i, v ->
            Offset(i * stepX + stepX / 2, cH - (v.toFloat() / maxV * cH))
        }
        if (mainPts.size >= 2) {
            drawPath(smoothFilledPath(mainPts, cH), ActivityFillColor.copy(alpha = 0.5f))
            drawPath(smoothLinePath(mainPts), ActivityFillColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }

        // Session markers
        for (s in sessions) {
            val c = when (s.type) { OverlayType.Walk -> Color(0xFF4CAF50); OverlayType.Run -> Color(0xFFFF9800); else -> Color(0xFF2196F3) }
            val sx = s.startIndex * stepX; val ew = ((s.endIndex - s.startIndex + 1) * stepX).coerceAtLeast(stepX)
            drawRect(c, Offset(sx, cH + 1.dp.toPx()), Size(ew, sH))
        }

        // Scrub
        if (si != null && si in values.indices) {
            val x = si * stepX + stepX / 2
            drawLine(ScrubLineColor, Offset(x, 0f), Offset(x, cH), 1.5.dp.toPx())
            val y = cH - (values[si].toFloat() / maxV * cH)
            drawCircle(Color.White, 5.dp.toPx(), Offset(x, y))
            drawCircle(ActivityFillColor, 3.dp.toPx(), Offset(x, y))
        }

        // Labels
        for (i in values.indices step 6) {
            drawText(tm.measure(labels[i], ls), topLeft = Offset(i * stepX, cH + sH + 1.dp.toPx()))
        }
    }
}

@Composable
internal fun BarChart(values: List<Long>, labels: List<String>, color: Color, scrub: ScrubState, tm: androidx.compose.ui.text.TextMeasurer, averageLine: Long = 0) {
    val maxV = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val ls = TextStyle(fontSize = 9.sp, color = AxisLabelColor)
    val si = scrub.scrubIndex

    Canvas(Modifier.fillMaxWidth().height(160.dp).scrubGesture(values.size, scrub)) {
        val sp = 2.dp.toPx(); val lH = 14.dp.toPx(); val cH = size.height - lH
        val bW = (size.width - sp * (values.size - 1)) / values.size
        drawRect(ChartOverlayColor, Offset.Zero, Size(size.width, cH))

        val heights = values.map { (it.toFloat() / maxV * cH).coerceAtLeast(0f) }
        heights.forEachIndexed { i, h ->
            if (h <= 0f) return@forEachIndexed
            val a = when { si == null -> BarAlpha; si == i -> BarAlphaSelected; else -> BarAlphaDimmed }
            val x = i * (bW + sp)
            val prevH = if (i > 0) heights[i - 1] else h
            val nextH = if (i < heights.size - 1) heights[i + 1] else h
            drawPath(smoothBarPath(x, bW, cH, h, prevH, nextH), color.copy(alpha = a))
        }

        // Average reference line
        if (averageLine > 0) {
            val avgY = cH - (averageLine.toFloat() / maxV * cH).coerceIn(0f, cH)
            drawLine(TypicalBadgeColor, Offset(0f, avgY), Offset(size.width, avgY), strokeWidth = 2.dp.toPx())
        }

        if (si != null && si in values.indices) {
            val x = si * (bW + sp) + bW / 2
            drawLine(ScrubLineColor, Offset(x, 0f), Offset(x, cH), 1.5.dp.toPx())
        }
        val step = if (values.size <= 7) 1 else if (values.size <= 14) 2 else 1
        for (i in values.indices step step) {
            if (i < labels.size) drawText(tm.measure(labels[i], ls), topLeft = Offset(i * (bW + sp), cH + 2.dp.toPx()))
        }
    }
}

@Composable
internal fun DayScrubStrip(labels: List<String>, scrub: ScrubState, tm: androidx.compose.ui.text.TextMeasurer) {
    val n = labels.size
    if (n == 0) return
    val si = scrub.scrubIndex
    val ls = TextStyle(fontSize = 10.sp, color = AxisLabelColor)
    Canvas(Modifier.fillMaxWidth().height(28.dp).scrubGesture(n, scrub)) {
        val cellW = size.width / n
        if (si != null && si in 0 until n) {
            drawRect(ChartOverlayColor, Offset(si * cellW, 0f), Size(cellW, size.height))
            val cx = si * cellW + cellW / 2f
            drawLine(ScrubLineColor, Offset(cx, 0f), Offset(cx, size.height), 1.5.dp.toPx())
        }
        for (i in 0 until n) {
            val measured = tm.measure(labels[i], ls)
            val tx = i * cellW + (cellW - measured.size.width) / 2f
            val ty = (size.height - measured.size.height) / 2f
            drawText(measured, topLeft = Offset(tx, ty))
        }
    }
}

@Composable
internal fun StackedBarChart(data: List<StackedSleepEntry>, scrub: ScrubState, tm: androidx.compose.ui.text.TextMeasurer, typicalLine: Float = 0f) {
    if (data.isEmpty()) return
    val mx = data.maxOf { it.totalHours }.coerceAtLeast(0.1f)
    val ls = TextStyle(fontSize = 9.sp, color = AxisLabelColor)
    val si = scrub.scrubIndex

    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp).scrubGesture(data.size, scrub)) {
        val sp = 2.dp.toPx(); val lH = 14.dp.toPx(); val cH = size.height - lH
        val bW = (size.width - sp * (data.size - 1)) / data.size
        drawRect(ChartOverlayColor, Offset.Zero, Size(size.width, cH))

        val totalHeights = data.map { it.totalHours / mx * cH }
        val deepHeights = data.map { it.deepHours / mx * cH }

        data.forEachIndexed { i, _ ->
            val x = i * (bW + sp)
            val th = totalHeights[i]; val dh = deepHeights[i]
            if (th <= 0f) return@forEachIndexed
            val a = when { si == null -> BarAlpha; si == i -> BarAlphaSelected; else -> BarAlphaDimmed }

            val prevTh = if (i > 0) totalHeights[i - 1] else th
            val nextTh = if (i < data.size - 1) totalHeights[i + 1] else th
            drawPath(smoothBarPath(x, bW, cH, th, prevTh, nextTh), LightSleepBarColor.copy(alpha = a))

            if (dh > 0f) {
                drawRect(DeepSleepBarColor.copy(alpha = a), Offset(x, cH - dh), Size(bW, dh))
            }
        }

        if (typicalLine > 0f) {
            val typY = cH - (typicalLine / mx * cH).coerceIn(0f, cH)
            drawLine(TypicalBadgeColor, Offset(0f, typY), Offset(size.width, typY), strokeWidth = 2.dp.toPx())
        }

        if (si != null && si in data.indices) {
            drawLine(ScrubLineColor, Offset(si * (bW + sp) + bW / 2, 0f), Offset(si * (bW + sp) + bW / 2, cH), 1.5.dp.toPx())
        }
        for (i in data.indices) {
            drawText(tm.measure(data[i].label, ls), topLeft = Offset(i * (bW + sp), cH + 2.dp.toPx()))
        }
    }
}

@Composable
internal fun DailySleepTimeline(segments: List<SleepSegmentUi>, totalH: Float, deepH: Float) {
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val scrubTime = scrubFraction?.let { frac ->
        val windowHours = 18f
        val hourOfDay = (18f + frac * windowHours) % 24f
        val h = hourOfDay.toInt(); val m = ((hourOfDay - h) * 60).toInt()
        val ampm = if (h < 12 || h == 24) "AM" else "PM"
        val h12 = if (h == 0 || h == 24) 12 else if (h > 12) h - 12 else h
        "$h12:${m.toString().padStart(2, '0')} $ampm"
    }
    val scrubInSegment = scrubFraction?.let { frac ->
        // Deep segments are drawn after light segments and visually overlap; pick the last
        // matching one so scrubbing reports "Deep sleep" wherever deep is rendered on top.
        segments.lastOrNull { frac >= it.startFraction && frac <= it.startFraction + it.widthFraction }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (scrubTime != null) {
            Text(
                "$scrubTime · ${if (scrubInSegment?.isDeep == true) "Deep sleep" else if (scrubInSegment != null) "Light sleep" else "Awake"}",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            for (l in listOf("6 PM", "12 AM", "6 AM", "12 PM"))
                Text(l, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubFraction = if (scrubFraction != null) null else frac
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> scrubFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {}, onDragCancel = {},
                    ) { change, _ -> scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
                }
        ) {
            drawRect(ChartOverlayColor, size = size)
            for (s in segments) drawRect(
                if (s.isDeep) DeepSleepBarColor else LightSleepBarColor,
                Offset(s.startFraction * size.width, 0f), Size(s.widthFraction * size.width, size.height))
            scrubFraction?.let { frac ->
                val x = frac * size.width
                drawLine(ScrubLineColor, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(24.dp)) {
            LegendDot(LightSleepBarColor, "Light ${formatHours(totalH - deepH)}")
            LegendDot(DeepSleepBarColor, "Deep ${formatHours(deepH)}")
        }
    }
}

@Composable
internal fun HRZoneBar(zones: Map<Int, Long>) {
    val total = zones.values.sum().coerceAtLeast(1)
    val z1 = zones[1] ?: 0L; val z2 = zones[2] ?: 0L; val z3 = zones[3] ?: 0L
    val z1f = z1.toFloat() / total; val z2f = z2.toFloat() / total; val z3f = z3.toFloat() / total

    fun fmt(mins: Long, frac: Float) = "${mins / 60}:${(mins % 60).toString().padStart(2, '0')} (${(frac * 100).roundToInt()}%)"
    Column(Modifier.fillMaxWidth().background(HRBgColor).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
            val w1 = z1f * size.width; val w2 = z2f * size.width; val w3 = z3f * size.width
            drawRect(HRZone1Color, Offset(0f, 0f), Size(w1, size.height))
            drawRect(HRZone2Color, Offset(w1, 0f), Size(w2, size.height))
            drawRect(HRZone3Color, Offset(w1 + w2, 0f), Size(w3, size.height))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            LegendDot(HRZone1Color, "Z1: ${fmt(z1, z1f)}"); LegendDot(HRZone2Color, "Z2: ${fmt(z2, z2f)}"); LegendDot(HRZone3Color, "Z3: ${fmt(z3, z3f)}")
        }
    }
}

@Composable
internal fun HRLineChart(hrs: List<Double?>, scrub: ScrubState, tm: androidx.compose.ui.text.TextMeasurer) {
    val ls = TextStyle(fontSize = 9.sp, color = AxisLabelColor); val si = scrub.scrubIndex
    Canvas(Modifier.fillMaxWidth().height(160.dp).scrubGesture(hrs.size, scrub)) {
        val lH = 14.dp.toPx(); val cH = size.height - lH
        val mn = hrs.filterNotNull().minOrNull()?.let { (it - 10).coerceAtLeast(0.0) } ?: 40.0
        val mx = hrs.filterNotNull().maxOrNull()?.let { it + 10 } ?: 200.0
        val rg = (mx - mn).coerceAtLeast(1.0)
        drawRect(ChartOverlayColor, Offset.Zero, Size(size.width, cH))
        val sx = size.width / (hrs.size - 1).coerceAtLeast(1)
        val pts = hrs.mapIndexedNotNull { i, v -> v?.let { Offset(i * sx, cH - ((it - mn) / rg * cH).toFloat()) } }
        if (pts.size >= 2) {
            drawPath(smoothFilledPath(pts, cH), HRLineColor.copy(alpha = 0.2f))
            drawPath(smoothLinePath(pts), HRLineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (si != null && si in hrs.indices) {
            val x = si * sx; drawLine(ScrubLineColor, Offset(x, 0f), Offset(x, cH), 1.5.dp.toPx())
            hrs[si]?.let { val y = cH - ((it - mn) / rg * cH).toFloat(); drawCircle(Color.White, 5.dp.toPx(), Offset(x, y)); drawCircle(HRLineColor, 3.5.dp.toPx(), Offset(x, y)) }
        }
        val pointsPerHour = (hrs.size / 24).coerceAtLeast(1)
        for (i in hrs.indices step pointsPerHour * 6) { drawText(tm.measure("${i / pointsPerHour}", ls), topLeft = Offset(i * sx, cH + 2.dp.toPx())) }
    }
}

internal fun smoothBarPath(x: Float, w: Float, baseline: Float, h: Float, prevH: Float, nextH: Float): Path {
    val top = baseline - h
    val leftEdgeY = baseline - (prevH * 0.15f + h * 0.85f)
    val rightEdgeY = baseline - (nextH * 0.15f + h * 0.85f)
    val edgeW = w * 0.2f
    return Path().apply {
        moveTo(x, baseline)
        lineTo(x, leftEdgeY)
        cubicTo(x, top, x + edgeW, top, x + edgeW, top)
        lineTo(x + w - edgeW, top)
        cubicTo(x + w - edgeW, top, x + w, top, x + w, rightEdgeY)
        lineTo(x + w, baseline)
        close()
    }
}

internal fun smoothFilledPath(points: List<Offset>, baseline: Float): Path {
    return Path().apply {
        moveTo(points.first().x, baseline)
        lineTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]; val p1 = points[i + 1]
            val midX = (p0.x + p1.x) / 2
            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
        lineTo(points.last().x, baseline)
        close()
    }
}

internal fun smoothLinePath(points: List<Offset>): Path {
    return Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]; val p1 = points[i + 1]
            val midX = (p0.x + p1.x) / 2
            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
    }
}
