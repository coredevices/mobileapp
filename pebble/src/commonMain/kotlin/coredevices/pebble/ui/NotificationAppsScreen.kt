package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.SpaceEvenly
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coredevices.pebble.PebbleFeatures
import coredevices.pebble.Platform
import coredevices.pebble.rememberLibPebble
import coredevices.ui.PebbleElevatedButton
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.everNotified
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class NotificationAppsScreenViewModel : ViewModel() {
    val onlyNotified = mutableStateOf(false)
    val sortBy = mutableStateOf(NotificationAppSort.Recent)
    val searchState = SearchState()
}

@Composable
fun NotificationAppsScreen(topBarParams: TopBarParams, nav: NavBarNav, gotoDefaultTab: () -> Unit) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val viewModel = koinViewModel<NotificationAppsScreenViewModel>()
        val listState = rememberLazyListState()
        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(viewModel.searchState)
            launch {
                topBarParams.scrollToTop.collect {
                    if (listState.firstVisibleItemIndex > 0) {
                        listState.animateScrollToItem(0)
                    } else {
                        gotoDefaultTab()
                    }
                }
            }
        }
        val notificationApi: NotificationApps = koinInject()
        val platform = koinInject<Platform>()
        val appsFlow = remember { notificationApi.notificationApps() }
        val apps by appsFlow.collectAsState(emptyList())
        val bootConfig = rememberBootConfig()
        val pebbleFeatures: PebbleFeatures = koinInject()
        val libPebble = rememberLibPebble()
        val libPebbleConfig by libPebble.config.collectAsState()
        val filteredAndSortedApps by remember(
            apps,
            viewModel.searchState,
            viewModel.onlyNotified.value,
            viewModel.sortBy.value
        ) {
            derivedStateOf {
                val filtered = apps.asSequence().filter { app ->
                    if (viewModel.searchState.query.isNotEmpty()) {
                        app.app.name.contains(viewModel.searchState.query, ignoreCase = true)
                    } else {
                        app.app.everNotified() || !viewModel.onlyNotified.value
                    }
                }

                when (viewModel.sortBy.value) {
                    NotificationAppSort.Name -> {
                        filtered.toList()
                    }

                    NotificationAppSort.Count -> {
                        filtered.sortedByDescending { it.count }.toList()
                    }

                    NotificationAppSort.Recent -> filtered.sortedByDescending { it.app.lastNotified.instant }
                        .toList()
                }
            }
        }

        Column {
            if (pebbleFeatures.supportsNotificationAppSorting()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val notifiedOnlyEnabled = pebbleFeatures.supportsNotifiedOnlyFilter()
                        ElevatedFilterChip(
                            onClick = {
                                if (notifiedOnlyEnabled) {
                                    viewModel.onlyNotified.value =
                                        !viewModel.onlyNotified.value
                                }
                            },
                            label = {
                                Text("Notified only")
                            },
                            selected = viewModel.onlyNotified.value,
                            enabled = notifiedOnlyEnabled,
                            leadingIcon = if (viewModel.onlyNotified.value) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Done,
                                        contentDescription = "Done icon",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            },
                            elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
                            colors = FilterChipDefaults.elevatedFilterChipColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val expanded = remember { mutableStateOf(false) }
                        ElevatedFilterChip(
                            onClick = {
                                expanded.value = !expanded.value
                            },
                            label = {
                                Text(viewModel.sortBy.value.name)
                            },
                            selected = false,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort Options",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (expanded.value) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (expanded.value) "Collapse" else "Expand",
                                )
                            },
                            elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
                            colors = FilterChipDefaults.elevatedFilterChipColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                        DropdownMenu(
                            expanded = expanded.value,
                            onDismissRequest = { expanded.value = false }
                        ) {
                            NotificationAppSort.entries.filter { 
                                it != NotificationAppSort.Count || pebbleFeatures.supportsNotificationCountSorting()
                            }.forEach { sortOption ->
                                androidx.compose.material3.DropdownMenuItem(
                                    onClick = {
                                        viewModel.sortBy.value = sortOption
                                        expanded.value = false
                                    },
                                    text = { Text(sortOption.name) },
                                    leadingIcon = {
                                        if (viewModel.sortBy.value == sortOption) Icon(
                                            imageVector = Icons.Filled.Done,
                                            contentDescription = "Done"
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            LazyColumn(state = listState) {
                item(key = "toggle_all") {
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("All Apps", fontSize = 17.sp)
                            }
                        },
                        trailingContent = {
                            Row {
                                PebbleElevatedButton(
                                    text = "Mute All",
                                    onClick = {
                                        libPebble.updateNotificationAppMuteState(
                                            packageName = null,
                                            muteState = MuteState.Always,
                                        )
                                    },
                                    primaryColor = true,
                                    modifier = Modifier.padding(horizontal = 5.dp),
                                )
                                PebbleElevatedButton(
                                    text = "Enable All",
                                    onClick = {
                                        libPebble.updateNotificationAppMuteState(
                                            packageName = null,
                                            muteState = MuteState.Never,
                                        )
                                    },
                                    primaryColor = true,
                                    modifier = Modifier.padding(horizontal = 5.dp),
                                )
                            }
                        },
                        shadowElevation = 2.dp,
                    )
                }
                if (pebbleFeatures.supportsNotificationFiltering()) {
                    item(key = "default_state") {
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("New app default", fontSize = 17.sp)
                                }
                            },
                            trailingContent = {
                                Switch(checked = libPebbleConfig.notificationConfig.defaultAppsToEnabled, onCheckedChange = { enabled ->
                                    libPebble.updateConfig(
                                        libPebbleConfig.copy(
                                            notificationConfig = libPebbleConfig.notificationConfig.copy(
                                                defaultAppsToEnabled = enabled
                                            )
                                        )
                                    )
                                })
                            },
                            shadowElevation = 2.dp,
                        )
                    }
                }
                items(
                    items = filteredAndSortedApps,
                    key = { it.app.packageName },
                ) { entry ->
                    NotificationAppCard(
                        entry = entry,
                        notificationApps = notificationApi,
                        bootConfig = bootConfig,
                        platform = platform,
                        nav = nav,
                        clickable = true,
                        showBadge = false,
                    )
                }
            }
        }
    }
}