package coredevices.ring.ui.screens.notes

import BugReportButton
import CoreNav
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.app
import coreapp.ring.generated.resources.date
import coreapp.ring.generated.resources.delete
import coreapp.ring.generated.resources.notification
import coreapp.ring.generated.resources.one_time
import coreapp.ring.generated.resources.repeat
import coreapp.ring.generated.resources.time
import coreapp.util.generated.resources.back
import coredevices.ring.ui.components.notes.ReminderDataListItem
import coredevices.ring.ui.viewmodel.ReminderDetailsViewModel
import coredevices.ring.ui.viewmodel.ReminderNotification
import coredevices.ring.ui.viewmodel.ReminderRepeat
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun ReminderDetails(coreNav: CoreNav, reminderId: Int) {
    val viewModel = koinViewModel<ReminderDetailsViewModel> { parametersOf(coreNav, reminderId) }
    val itemState by viewModel.itemState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = coreNav::goBack
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(
                                UtilRes.string.back
                            )
                        )
                    }
                },
                title = {
                    if (itemState is ReminderDetailsViewModel.ItemState.Loaded) {
                        Text(
                            (itemState as ReminderDetailsViewModel.ItemState.Loaded).item.title,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "ReminderDetails",
                            "state" to itemState.toString(),
                            "reminderId" to reminderId.toString(),
                        ),
                    )
                    IconButton(
                        onClick = viewModel::deleteReminder,
                        enabled = itemState is ReminderDetailsViewModel.ItemState.Loaded
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(Res.string.delete)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        itemState.let { state ->
            when (state) {
                is ReminderDetailsViewModel.ItemState.Loading -> {
                    Text("Loading")
                }

                is ReminderDetailsViewModel.ItemState.Error -> {
                    Text("Error")
                }

                is ReminderDetailsViewModel.ItemState.Loaded -> {
                    Column(modifier = Modifier.padding(paddingValues)) {
                        state.item.time?.let {
                            ReminderDataListItem(
                                data = {
                                    Text(state.item.time)
                                },
                                label = {
                                    Text(stringResource(Res.string.time))
                                }
                            )
                        }
                        state.item.date?.let {
                            ReminderDataListItem(
                                data = {
                                    Text(state.item.date)
                                },
                                label = {
                                    Text(stringResource(Res.string.date))
                                }
                            )
                        }
                        ReminderDataListItem(
                            data = {
                                Text(
                                    when (state.item.repeat) {
                                        ReminderRepeat.Once -> stringResource(Res.string.one_time)
                                    }
                                )
                            },
                            label = {
                                Text(stringResource(Res.string.repeat))
                            }
                        )
                        ReminderDataListItem(
                            data = {
                                val app = stringResource(Res.string.app)
                                Text(
                                    state.item.notification.joinToString {
                                        when (it) {
                                            ReminderNotification.App -> app
                                        }
                                    }
                                )
                            },
                            label = {
                                Text(stringResource(Res.string.notification))
                            }
                        )
                    }
                }
            }
        }
    }
}