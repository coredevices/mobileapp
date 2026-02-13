package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble

@Composable
fun CalendarScreen(navBarNav: NavBarNav, topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("Calendar Settings")
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val libPebble = rememberLibPebble()
        val flow = remember { libPebble.calendars() }
        val calendars by flow.collectAsState(emptyList())
        Scaffold { innerPadding ->
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                val groupedCalendars = calendars.groupBy { it.ownerName }
                groupedCalendars.forEach { (ownerName, calendarList) ->
                    item {
                        Text(
                            text = ownerName,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp,
                            )
                        )
                        HorizontalDivider()
                    }

                    items(calendarList.size) { i ->
                        val entry = calendarList[i]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = entry.enabled,
                                onCheckedChange = { isChecked ->
                                    libPebble.updateCalendarEnabled(entry.id, isChecked)
                                }
                            )
                            val notSyncedText = if (entry.syncEvents) {
                                ""
                            } else {
                                " (not synced by Android!)"
                            }
                            Text("${entry.name}$notSyncedText")
                        }
                    }
                }
            }
        }
    }
}