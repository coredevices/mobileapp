package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.Platform
import coredevices.util.granted
import coredevices.util.isAndroid
import coredevices.util.rememberUiContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
        val scope = rememberCoroutineScope()
        val permissionRequester: PermissionRequester = koinInject()
        val uiContext = rememberUiContext()
        val platform: Platform = koinInject()
        val calendarPermissionGranted by permissionRequester.granted(Permission.Calendar).collectAsState(null)
        val calendarPermissionGrantedSafe = calendarPermissionGranted
        if (calendarPermissionGrantedSafe == null || uiContext == null) {
            return
        }
        Scaffold { innerPadding ->
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                if (!calendarPermissionGrantedSafe) {
                    item {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 6.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Calendar permission missing",
                                    modifier = Modifier.padding(15.dp)
                                        .align(Alignment.CenterHorizontally),
                                    textAlign = TextAlign.Center,
                                )
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            permissionRequester.requestPermission(Permission.Calendar, uiContext)
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Text(
                                        text = "Approve",
                                        modifier = Modifier.padding(15.dp),
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                } else if (calendars.isEmpty() && platform.isAndroid) {
                    // Have permission, but no calendars listed
                    item {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 6.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "Calendar permission is approved, but no calendars were found.\n\n Is sharing enabled in the Google Calendar app? See: Settings -> General -> Share Google Calendar data with other apps",
                                modifier = Modifier.padding(15.dp)
                                    .align(Alignment.CenterHorizontally),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
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