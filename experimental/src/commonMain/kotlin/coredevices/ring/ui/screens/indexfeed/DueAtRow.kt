@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.screens.indexfeed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coredevices.ring.ui.theme.IndexTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Tappable due-date row: tap opens a date picker that chains into a time picker. */
@Composable
internal fun DueAtRow(label: String, dueAt: Instant?, onChange: (Instant?) -> Unit) {
    val colors = IndexTheme.colors
    var showDate by remember { mutableStateOf(false) }
    val pretty = dueAt?.let { formatLongDateTime(it) } ?: "Set due date…"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
            .clickable { showDate = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = colors.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(86.dp))
        Text(
            pretty,
            color = if (dueAt != null) colors.onSurface else colors.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (dueAt != null) {
            Text(
                "Clear",
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onChange(null) }
                    .padding(start = 12.dp),
            )
        }
    }

    if (showDate) {
        DueAtPickerFlow(
            initial = dueAt,
            onPicked = {
                showDate = false
                onChange(it)
            },
            onDismiss = { showDate = false },
        )
    }
}

/** Date picker chained into a time picker; [onPicked] fires only on the final Save. */
@Composable
internal fun DueAtPickerFlow(
    initial: Instant?,
    onPicked: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    val tz = TimeZone.currentSystemDefault()
    var draft by remember { mutableStateOf(initial ?: Clock.System.now()) }
    var showTime by remember { mutableStateOf(false) }

    if (!showTime) {
        val initialDate = draft.toLocalDateTime(tz).date
        val initialMs = Instant
            .fromEpochSeconds(initialDate.toEpochDays() * 86_400L)
            .toEpochMilliseconds()
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialMs,
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val ms = datePickerState.selectedDateMillis ?: return@TextButton
                    val existing = draft.toLocalDateTime(tz)
                    val pickedDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                    draft = kotlinx.datetime.LocalDateTime(
                        pickedDate, kotlinx.datetime.LocalTime(existing.hour, existing.minute),
                    ).toInstant(tz)
                    showTime = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        val ldt = draft.toLocalDateTime(tz)
        val timeState = androidx.compose.material3.rememberTimePickerState(
            initialHour = ldt.hour,
            initialMinute = ldt.minute,
            is24Hour = false,
        )
        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceContainer)
                    .padding(20.dp),
            ) {
                androidx.compose.material3.TimePicker(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        onPicked(
                            kotlinx.datetime.LocalDateTime(
                                ldt.date, kotlinx.datetime.LocalTime(timeState.hour, timeState.minute),
                            ).toInstant(tz),
                        )
                    }) { Text("Save") }
                }
            }
        }
    }
}
