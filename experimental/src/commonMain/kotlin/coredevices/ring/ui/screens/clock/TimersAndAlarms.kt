package coredevices.ring.ui.screens.clock

import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.ring.data.entity.room.clock.LocalClockEntry
import coredevices.ring.ui.UITimeUtil
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.LocalClockViewModel
import coredevices.ring.ui.viewmodel.formatDuration
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Timers and alarms set by the Index agent on this device, with a cancel button on each. */
@Composable
fun TimersAndAlarms(coreNav: CoreNav) {
    val vm = koinViewModel<LocalClockViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = IndexTheme.colors
    val statusBarPad = WindowInsets.statusBars.asPaddingValues()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(top = statusBarPad.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = coreNav::goBack) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, "Back", tint = colors.onSurface)
            }
            Text(
                "Timers & alarms",
                color = colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        ) {
            if (state.isEmpty) {
                item("empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No timers or alarms. Ask your Index to set one.",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            if (state.timers.isNotEmpty()) {
                item("timers") { SectionHeader("Timers") }
                items(items = state.timers, key = { it.entry.id }) { row ->
                    ClockRow(
                        glyph = Icons.Outlined.Timer,
                        primary = LocalClockViewModel.formatCountdown(row.remaining),
                        secondary = timerSubtitle(row.entry),
                        onCancel = { vm.cancel(row.entry.id) },
                    )
                }
            }
            if (state.alarms.isNotEmpty()) {
                item("alarms") { SectionHeader("Alarms") }
                items(items = state.alarms, key = { it.id }) { entry ->
                    ClockRow(
                        glyph = Icons.Outlined.Alarm,
                        primary = fireTimeText(entry.fireAt, withDate = false),
                        secondary = alarmSubtitle(entry),
                        onCancel = { vm.cancel(entry.id) },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun timerSubtitle(entry: LocalClockEntry): String {
    val ends = fireTimeText(entry.fireAt, withDate = false)
    val what = entry.label
        ?: entry.durationMillis?.let { "${formatDuration(it.milliseconds)} timer" }
        ?: "Timer"
    return "$what · Ends $ends"
}

@Composable
private fun alarmSubtitle(entry: LocalClockEntry): String {
    val day by produceState("", entry.fireAt) {
        value = UITimeUtil.humanDate(entry.fireAt.toLocalDateTime(TimeZone.currentSystemDefault()).date)
    }
    return listOfNotNull(entry.label, day.takeIf { it.isNotEmpty() }).joinToString(" · ")
}

@Composable
private fun fireTimeText(fireAt: Instant, withDate: Boolean): String {
    val text by produceState("", fireAt, withDate) {
        val local = fireAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val time = local.time.format(UITimeUtil.timeFormat())
        value = if (withDate) "${UITimeUtil.humanDate(local.date)}, $time" else time
    }
    return text
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        color = IndexTheme.colors.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun ClockRow(glyph: ImageVector, primary: String, secondary: String, onCancel: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(glyph, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                primary,
                color = colors.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary.isNotEmpty()) {
                Text(
                    secondary,
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, "Cancel", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
