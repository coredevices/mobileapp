package coredevices.pebble.ui

import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.pebble.rememberLibPebble
import org.jetbrains.compose.resources.stringResource

@Composable
fun CalendarScreen(coreNav: CoreNav) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val libPebble = rememberLibPebble()
        val flow = remember { libPebble.calendars() }
        val calendars by flow.collectAsState(emptyList())
        val config by libPebble.config.collectAsState()
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(Res.string.back)
                            )
                        }
                    },
                    title = { Text("Calendar Settings") },
                )
            },

            ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(15.dp),
                ) {
                    Text("Enable Calendar Reminders")
                    Checkbox(
                        checked = config.watchConfig.calendarReminders,
                        onCheckedChange = {
                            libPebble.updateConfig(
                                config.copy(
                                    watchConfig = config.watchConfig.copy(
                                        calendarReminders = it
                                    )
                                )
                            )
                        }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(15.dp),
                ) {
                    Text("Show Declined Events")
                    Checkbox(
                        checked = config.watchConfig.calendarShowDeclinedEvents,
                        onCheckedChange = {
                            libPebble.updateConfig(
                                config.copy(
                                    watchConfig = config.watchConfig.copy(
                                        calendarShowDeclinedEvents = it
                                    )
                                )
                            )
                        }
                    )
                }
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
}