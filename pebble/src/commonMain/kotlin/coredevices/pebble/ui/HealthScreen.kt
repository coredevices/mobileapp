package coredevices.pebble.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rebble.libpebblecommon.health.HealthTimeRange
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun HealthScreen(topBarParams: TopBarParams, nav: NavBarNav) {
    val vm = koinViewModel<HealthViewModel>()
    val act by vm.activity.collectAsState()
    val slp by vm.sleep.collectAsState()
    val hr by vm.heartRate.collectAsState()
    val dl by vm.dateLabel.collectAsState()
    val imperial by vm.imperialUnits.collectAsState()
    val hasHrmWatch by vm.hasHrmWatch.collectAsState()

    LaunchedEffect(Unit) {
        topBarParams.title("Health")
        topBarParams.actions {}
        topBarParams.searchAvailable(null)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
    ) {
        TimeRangeSelector(vm.selectedTimeRange, vm::onTimeRangeChanged)
        DateNavigator(dl, vm.dateOffset, vm::navigateBack, vm::navigateForward)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActivityCard(act, vm.selectedTimeRange, imperial)
            SleepCard(slp, vm.selectedTimeRange)
            if (hasHrmWatch) HeartRateCard(hr, vm.selectedTimeRange)
            TextButton(
                onClick = {
                    nav.navigateTo(
                        PebbleNavBarRoutes.WatchSettingsCategoryRoute(
                            section = "Health",
                            topLevelType = "Phone"
                        )
                    )
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Health Settings")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TimeRangeSelector(sel: HealthTimeRange, onSel: (HealthTimeRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        HealthTimeRange.entries.forEachIndexed { i, r ->
            SegmentedButton(sel == r, { onSel(r) }, SegmentedButtonDefaults.itemShape(i, HealthTimeRange.entries.size),
                label = { Text(when (r) { HealthTimeRange.Daily -> "Day"; HealthTimeRange.Weekly -> "Week"; HealthTimeRange.Monthly -> "Month" }) })
        }
    }
}

@Composable
private fun DateNavigator(label: String, offset: Int, onBack: () -> Unit, onFwd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
        Text(label, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onFwd, enabled = offset < 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next",
                tint = if (offset < 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun HealthCard(bg: Color, content: @Composable () -> Unit) {
    Surface(shape = CardShape, color = bg, shadowElevation = 2.dp) { Column { content() } }
}

@Composable
private fun CardHeader(color: Color, cardName: String, label: String, subtitle: String, mainValue: String, secondLabel: String? = null, secondValue: String? = null, typicalValue: String? = null) {
    Column(Modifier.fillMaxWidth().background(color).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(cardName, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (label.isNotEmpty()) {
            Text(label.uppercase(), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
            Column {
                Text(mainValue, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (typicalValue != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "TYPICAL $typicalValue",
                        color = TypicalBadgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(TypicalBadgeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (secondLabel != null && secondValue != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(secondLabel.uppercase(), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    Text(secondValue, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActivityCard(st: ActivityUiState, range: HealthTimeRange, imperial: Boolean) {
    val scrub = rememberScrubState()
    val idx = scrub.scrubIndex
    val sv = if (idx != null && idx < st.barValues.size) st.barValues[idx] else null
    val sl = if (idx != null && idx < st.barLabels.size) st.barLabels[idx] else null

    val headerLabel = when (range) {
        HealthTimeRange.Daily -> "Today"
        HealthTimeRange.Weekly -> "Average"
        HealthTimeRange.Monthly -> "Average"
    }
    val headerVal = when {
        sv != null && range == HealthTimeRange.Monthly -> "${sv / 7}"
        sv != null && range == HealthTimeRange.Daily -> "$sv"
        sv != null -> "$sv"
        range == HealthTimeRange.Daily -> "${st.totalSteps}"
        else -> "${st.averageSteps}"
    }
    val headerSub = when {
        sl != null && range == HealthTimeRange.Daily -> "at $sl:00"
        sl != null && range == HealthTimeRange.Monthly -> "daily avg · $sl"
        sl != null -> sl
        else -> ""
    }
    val typicalStr = when {
        range == HealthTimeRange.Daily && st.typicalTotal > 0 -> formatSteps(st.typicalTotal)
        range != HealthTimeRange.Daily && st.averageSteps > 0 -> formatSteps(st.averageSteps)
        else -> null
    }

    // Check if scrub is over an activity session
    val sessionInfo = if (idx != null) st.activitySessions.firstOrNull { idx in it.startIndex..it.endIndex } else null

    HealthCard(ActivityBgColor) {
        CardHeader(
            color = ActivityHeaderColor,
            cardName = "Steps",
            label = headerLabel,
            subtitle = headerSub,
            mainValue = if (sessionInfo != null) sessionInfo.label else headerVal,
            typicalValue = typicalStr,
        )
        if (st.isLoading) { ChartPlaceholder("Loading...", "") }
        else if (st.barValues.all { it == 0L }) { ChartPlaceholder("No activity data", "Wear your watch to start tracking steps") }
        else {
            val tm = rememberTextMeasurer()
            if (range == HealthTimeRange.Daily) {
                AreaChart(st.barValues, st.barLabels, st.typicalSteps, scrub, tm, st.activitySessions)
            } else {
                val avgLine = when (range) {
                    HealthTimeRange.Weekly -> st.averageSteps
                    HealthTimeRange.Monthly -> {
                        val nonZero = st.barValues.filter { it > 0 }
                        if (nonZero.isNotEmpty()) nonZero.average().toLong() else 0L
                    }
                    else -> 0
                }
                BarChart(st.barValues, st.barLabels, ActivityBarColor, scrub, tm, averageLine = avgLine)
            }
            val prefix = if (range == HealthTimeRange.Daily) "" else "Avg "
            StatsRow(ActivityBgColor,
                "${prefix}Distance" to formatDistance(st.totalDistanceM, imperial),
                "${prefix}Calories" to "${st.totalCaloriesKcal}",
                "${prefix}Active" to "${st.totalActiveMinutes / 60}:${(st.totalActiveMinutes % 60).toString().padStart(2, '0')}",
            )
        }
    }
}

@Composable
private fun SleepCard(st: SleepUiState, range: HealthTimeRange) {
    val scrub = rememberScrubState()
    val idx = scrub.scrubIndex
    val scrubEntry = if (idx != null && range != HealthTimeRange.Daily && idx < st.stackedData.size) st.stackedData[idx] else null
    val dh = scrubEntry?.totalHours ?: st.totalSleepHours
    val dd = scrubEntry?.deepHours ?: st.deepSleepHours

    val sleepLabel = when (range) {
        HealthTimeRange.Daily -> "Total"
        else -> "Average"
    }
    val sub = scrubEntry?.label ?: ""
    val typicalStr = if (st.typicalSleepHours > 0f) formatHours(st.typicalSleepHours) else null

    HealthCard(SleepBgColor) {
        CardHeader(
            color = SleepHeaderColor,
            cardName = "Sleep",
            label = sleepLabel,
            subtitle = sub,
            mainValue = formatHours(dh),
            secondLabel = "Deep sleep",
            secondValue = formatHours(dd),
            typicalValue = typicalStr,
        )
        if (st.isLoading) { ChartPlaceholder("Loading...", "") }
        else if (st.totalSleepHours == 0f) { ChartPlaceholder("No sleep data", "Wear your watch to bed to track sleep") }
        else {
            when (range) {
                HealthTimeRange.Daily -> DailySleepTimeline(st.segments, st.totalSleepHours, st.deepSleepHours)
                else -> { val tm = rememberTextMeasurer(); StackedBarChart(st.stackedData, scrub, tm, typicalLine = st.typicalSleepHours) }
            }
            SleepStatsRow(st)
        }
    }
}

@Composable
private fun SleepStatsRow(st: SleepUiState) {
    Row(Modifier.fillMaxWidth().background(SleepBgColor).padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.SpaceEvenly) {
        StatItem("Avg Deep", "${st.avgDeepSleepMins}m")
        if (st.avgFallAsleep.isNotEmpty()) StatItem("Avg Fall Asleep", st.avgFallAsleep)
        if (st.avgWakeUp.isNotEmpty()) StatItem("Avg Wake Up", st.avgWakeUp)
    }
}

@Composable
private fun HeartRateCard(st: HeartRateUiState, range: HealthTimeRange) {
    val scrub = rememberScrubState()
    val rhrScrub = rememberScrubState()
    val idx = scrub.scrubIndex
    val minPerBucket = if (st.hrSamples.isNotEmpty()) 1440 / st.hrSamples.size else 60
    // The chart draws a smoothed line straight through null buckets, so the user can scrub onto
    // visible curve that maps to a null sample. Snap to the nearest non-null within ~1 hour.
    val sv = if (idx != null && idx < st.hrSamples.size) {
        val maxDist = (60 / minPerBucket).coerceAtLeast(1)
        var found: Double? = null
        for (d in 0..maxDist) {
            found = st.hrSamples.getOrNull(idx - d) ?: st.hrSamples.getOrNull(idx + d)
            if (found != null) break
        }
        found?.roundToInt()
    } else null
    val tStr = if (idx != null) { val m = idx * minPerBucket; "${m / 60}:${(m % 60).toString().padStart(2, '0')}" } else ""

    val hv = when { sv != null -> "$sv"; st.latestHR != null -> "${st.latestHR}"; st.averageHR != null -> "${st.averageHR}"; else -> "--" }
    val hs = when {
        sv != null && idx != null -> "bpm at $tStr"
        idx != null -> "no data at $tStr"
        st.latestHR != null -> "latest bpm"
        else -> "avg bpm"
    }

    val rhrIdx = rhrScrub.scrubIndex
        ?.takeIf { range == HealthTimeRange.Weekly && it < st.restingHRSeries.size }
    val rhrValue = if (rhrIdx != null) st.restingHRSeries[rhrIdx] else st.restingHR
    val rhrLabel = when {
        rhrIdx != null && rhrIdx < st.restingHRLabels.size -> "Resting · ${st.restingHRLabels[rhrIdx]}"
        rhrValue != null -> "Resting"
        else -> null
    }
    val rhrDisplayValue = when {
        rhrValue != null -> "$rhrValue"
        rhrIdx != null -> "--"
        else -> null
    }

    HealthCard(HRBgColor) {
        CardHeader(
            color = HRHeaderColor,
            cardName = "Heart Rate",
            label = "",
            subtitle = hs,
            mainValue = hv,
            secondLabel = rhrLabel,
            secondValue = rhrDisplayValue,
        )
        if (st.isLoading) { ChartPlaceholder("Loading...", "") }
        else if (st.averageHR == null) { ChartPlaceholder("No heart rate data", "Heart rate is measured automatically by your watch") }
        else {
            if (range == HealthTimeRange.Daily && st.hrSamples.any { it != null }) {
                val tm = rememberTextMeasurer(); HRLineChart(st.hrSamples, scrub, tm)
            }
            if (range == HealthTimeRange.Weekly && st.restingHRSeries.any { it != null }) {
                val tm = rememberTextMeasurer()
                DayScrubStrip(st.restingHRLabels, rhrScrub, tm)
            }
            if (st.zoneMinutes.isNotEmpty()) HRZoneBar(st.zoneMinutes)
        }
    }
}

@Composable
private fun ChartPlaceholder(title: String, subtitle: String = "Wear your watch to start tracking") {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun LegendDot(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) { drawRect(c) }
        Text(t, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun StatsRow(bg: Color, vararg stats: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.SpaceEvenly) {
        for ((l, v) in stats) StatItem(l, v)
    }
}

private fun formatDistance(m: Long, imperial: Boolean): String {
    return if (imperial) {
        val mi = m * 0.000621371; if (mi >= 1) "${(mi * 10).roundToInt() / 10.0} mi" else "${(m * 3.28084).roundToInt()} ft"
    } else { if (m >= 1000) "${(m / 100).toInt() / 10.0} km" else "$m m" }
}

internal fun formatHours(h: Float): String { val hrs = h.toInt(); val mins = ((h - hrs) * 60).roundToInt(); return "${hrs}h ${mins}m" }
private fun formatSteps(s: Long): String = if (s >= 1000) "${s / 1000}k" else "$s"
internal fun kotlinx.datetime.DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
internal fun kotlinx.datetime.Month.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
internal fun kotlinx.datetime.Month.fullName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

internal fun formatDayLabel(target: LocalDate, today: LocalDate): String {
    val diff = (today.toEpochDays() - target.toEpochDays()).toInt()
    if (diff == 0) return "Today"
    if (diff == 1) return "Yesterday"
    val day = "${target.dayOfMonth}${ordinalSuffix(target.dayOfMonth)}"
    return if (target.month == today.month && target.year == today.year) {
        "${target.dayOfWeek.shortName()} $day"
    } else {
        "${target.dayOfWeek.shortName()} $day ${target.month.shortName()}"
    }
}

internal fun ordinalSuffix(day: Int): String = when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}
