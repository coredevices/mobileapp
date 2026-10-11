package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coredevices.ring.database.Preferences
import coredevices.ui.M3Dialog
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/** Writable calendars first, alphabetically; read-only ones trail so they can be greyed out. */
internal fun orderCalendarsForPicker(calendars: List<CalendarEntity>): List<CalendarEntity> =
    calendars.sortedWith(
        compareByDescending<CalendarEntity> { it.writable }.thenBy { it.name.lowercase() }
    )

/** Lets the user pick which phone calendar the agent creates events in. */
@Composable
fun TargetCalendarDialog(onDismiss: () -> Unit) {
    val libPebble = koinInject<LibPebble>()
    val preferences = koinInject<Preferences>()
    val calendars by remember { libPebble.calendars().map(::orderCalendarsForPicker) }
        .collectAsState(emptyList())
    var selectedId by remember { mutableStateOf(preferences.targetCalendar.value) }

    LaunchedEffect(calendars) {
        if (calendars.isEmpty()) return@LaunchedEffect
        val writable = calendars.filter { it.writable }
        if (writable.none { it.id == selectedId }) {
            selectedId = writable.firstOrNull()?.id
        }
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Target Calendar") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                enabled = selectedId != null,
                onClick = {
                    preferences.setTargetCalendar(selectedId)
                    onDismiss()
                }
            ) { Text("OK") }
        }
    ) {
        Column(
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            Text(
                "Choose the calendar Index adds events to.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            if (calendars.isEmpty()) {
                Text("No calendars found.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(calendars.size) { i ->
                        val calendar = calendars[i]
                        CalendarRow(
                            calendar = calendar,
                            selected = selectedId == calendar.id,
                            onSelect = { selectedId = calendar.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(calendar: CalendarEntity, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (calendar.writable) 1f else 0.5f)
            .clickable(enabled = calendar.writable, onClick = onSelect)
    ) {
        RadioButton(
            enabled = calendar.writable,
            selected = selected,
            onClick = onSelect
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(12.dp).background(Color(calendar.color), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(calendar.name)
            if (!calendar.writable) {
                Text(
                    "No write access",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
