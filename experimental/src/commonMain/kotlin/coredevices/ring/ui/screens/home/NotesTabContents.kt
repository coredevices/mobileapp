package coredevices.ring.ui.screens.home

import BugReportButton
import CoreNav
import NoOpCoreNav
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.alarm_set
import coreapp.ring.generated.resources.all
import coreapp.ring.generated.resources.filter
import coreapp.ring.generated.resources.lists
import coreapp.ring.generated.resources.no_reminders_alarms
import coreapp.ring.generated.resources.reminder_added
import coreapp.ring.generated.resources.reminders_alarms
import coreapp.ring.generated.resources.search
import coreapp.ring.generated.resources.upcoming
import coreapp.util.generated.resources.settings
import coredevices.ring.ui.PreviewWrapper
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.components.feed.ContextChip
import coredevices.ring.ui.components.notes.RemindersAlarmsItem
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.viewmodel.NotesViewModel
import coredevices.ring.ui.viewmodel.RemindersAlarmsEntry
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun NotesTabContents(
    windowInsets: PaddingValues,
    coreNav: CoreNav,
) {
    val notesViewModel = koinViewModel<NotesViewModel>() { parametersOf(coreNav) }
    val remindersAlarmsState by notesViewModel.remindersAlarmsState.collectAsState()
    val loading by derivedStateOf { //TODO: include lists loading state when added
        remindersAlarmsState is NotesViewModel.RemindersAlarmsState.Loading
    }
    if (loading) {
        Column(Modifier.fillMaxHeight()) {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.weight(1f))
        }
    } else {
        LazyColumn(
            Modifier.padding(top = 16.dp, bottom = windowInsets.calculateBottomPadding()).fillMaxHeight()
        ) {
            item {
                SectionHeader(
                    modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
                    leadingContent = {
                        Text(
                            stringResource(Res.string.reminders_alarms),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    trailingContent = {
                        Text(
                            stringResource(Res.string.upcoming),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
            (remindersAlarmsState as? NotesViewModel.RemindersAlarmsState.Loaded)?.items?.let {
                if (it.isNotEmpty()) {
                    items(
                        it.size,
                        key = { index -> it[index].id }
                    ) { index ->
                        val entry = it[index]
                        RemindersAlarmsItem(
                            title = entry.title,
                            trailing = {
                                ContextChip(
                                    icon = {
                                        val modifier = Modifier.size(16.dp)
                                        when (entry.type) {
                                            RemindersAlarmsEntry.Type.Reminder -> Icon(
                                                Icons.Outlined.Notifications,
                                                contentDescription = stringResource(Res.string.reminder_added),
                                                modifier
                                            )

                                            RemindersAlarmsEntry.Type.LocationReminder -> Icon(
                                                Icons.Outlined.LocationOn,
                                                contentDescription = stringResource(Res.string.reminder_added),
                                                modifier
                                            )

                                            RemindersAlarmsEntry.Type.Alarm -> Icon(
                                                Icons.Outlined.AlarmOn,
                                                contentDescription = stringResource(Res.string.alarm_set),
                                                modifier
                                            )
                                        }
                                    },
                                    text = entry.triggerDetail,
                                    background = MaterialTheme.colorScheme.surfaceContainer,
                                    onBackground = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                notesViewModel.openDetails(entry.id)
                            }
                        )
                    }
                } else {
                    item {
                        Text(
                            stringResource(Res.string.no_reminders_alarms),
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    modifier = Modifier.padding(
                        top = 16.dp,
                        bottom = 8.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    leadingContent = {
                        Text(
                            stringResource(Res.string.lists),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    trailingContent = {
                        Text(
                            stringResource(Res.string.all),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
            item {
                Text(
                    "Lists & Notes not yet implemented",
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            /*item {
                ListsGrid(modifier = Modifier.heightIn(max = 2000.dp))
            }*/
        }
    }
}

@Preview
@Composable
fun NotesTabContentsPreview() {
    PreviewWrapper {
        NotesTabContents(
            windowInsets = PaddingValues(),
            coreNav = NoOpCoreNav
        )
    }
}