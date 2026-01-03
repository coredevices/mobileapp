package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.DensitySmall
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coredevices.pebble.Platform
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.database.dao.ChannelAndCount
import io.rebble.libpebblecommon.database.isAfter
import io.rebble.libpebblecommon.database.entity.ChannelGroup
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class NotificationAppScreenViewModel : ViewModel() {
    val onlyNotified = mutableStateOf(false)
}

@Composable
fun NotificationAppScreen(
    topBarParams: TopBarParams,
    packageName: String,
    nav: NavBarNav,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val viewModel = koinViewModel<NotificationAppScreenViewModel>()
        val notificationApps: NotificationApps = koinInject()
        val platform = koinInject<Platform>()
        val appWrapperFlow = remember(packageName) {
            notificationApps.notificationApps()
                .map { it.firstOrNull { it.app.packageName == packageName } }
        }
        val appWrapper by appWrapperFlow.collectAsState(null)
        val channelCountsFlow = remember(packageName) {
            notificationApps.notificationAppChannelCounts(packageName)
                .map { it.associateBy { it.channelId } }
        }
        val channelCounts by channelCountsFlow.collectAsState(emptyMap())
        val channelGroups by remember(appWrapper, channelCounts, viewModel.onlyNotified.value) {
            derivedStateOf {
                appWrapper?.let {
                    it.app.channelGroups.mapNotNull { group ->
                        val filteredChannels = group.channels.filter { channel ->
                            if (viewModel.onlyNotified.value) {
                                (channelCounts[channel.id]?.count ?: 0) > 0
                            } else {
                                true
                            }
                        }
                        // Don't show empty groups
                        if (filteredChannels.isNotEmpty()) {
                            ChannelGroup(group.id, group.name, filteredChannels)
                        } else null
                    }
                } ?: emptyList()
            }
        }
        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(false)
            topBarParams.actions {}
            topBarParams.title("App Notifications")
            topBarParams.canGoBack(true)
            topBarParams.goBack.collect {
                nav.goBack()
            }
        }
        appWrapper?.let { appWrapper ->
            val app = appWrapper.app
            val bootConfig = rememberBootConfig()
            Column {
                NotificationAppCard(
                    entry = appWrapper,
                    notificationApps = notificationApps,
                    bootConfig = bootConfig,
                    platform = platform,
                    nav = nav,
                    clickable = false,
                    showBadge = false,
                )
                SelectVibePatternOrNone(
                    currentPattern = appWrapper.app.vibePatternName,
                    onChangePattern = { pattern ->
                        notificationApps.updateNotificationAppState(
                            packageName = appWrapper.app.packageName,
                            vibePatternName = pattern?.name,
                            colorName = appWrapper.app.colorName,
                            iconCode = appWrapper.app.iconCode,
                        )
                    },
                )
                SelectColorOrNone(
                    currentColorName = appWrapper.app.colorName,
                    onChangeColor = { color ->
                        notificationApps.updateNotificationAppState(
                            packageName = appWrapper.app.packageName,
                            vibePatternName = appWrapper.app.vibePatternName,
                            colorName = color?.name,
                            iconCode = appWrapper.app.iconCode,
                        )
                    },
                )
                SelectIconOrNone(
                    currentIcon = TimelineIcon.fromCode(appWrapper.app.iconCode),
                    onChangeIcon = { icon ->
                        notificationApps.updateNotificationAppState(
                            packageName = appWrapper.app.packageName,
                            vibePatternName = appWrapper.app.vibePatternName,
                            colorName = appWrapper.app.colorName,
                            iconCode = icon?.code,
                        )
                    },
                )
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    ElevatedCard(
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 6.dp
                        ),
                        modifier = Modifier.padding(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Channels", fontSize = 20.sp, modifier = Modifier.padding(5.dp))
                            FilterChip(
                                modifier = Modifier.padding(5.dp),
                                onClick = {
                                    viewModel.onlyNotified.value = !viewModel.onlyNotified.value
                                },
                                label = {
                                    Text("Notified only")
                                },
                                selected = viewModel.onlyNotified.value,
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
                            )
                        }
                    }
                }
                LazyColumn {
                    items(
                        items = channelGroups,
                        key = { it.id },
                    ) { group ->
                        if (channelGroups.size > 1) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = group.name ?: "Default Group",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }
                            )
                        }
                        group.channels.forEach { channel ->
                            ChannelCard(
                                channelItem = channel,
                                app = app,
                                notificationApps = notificationApps,
                                channelCounts = channelCounts,
                                nav = nav,
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channelItem: ChannelItem,
    app: NotificationAppItem,
    notificationApps: NotificationApps,
    channelCounts: Map<String, ChannelAndCount>,
    nav: NavBarNav,
) {
    val expiration = app.muteExpiration
    val appMuted = when {
        // Check temporary mute first (takes priority)
        expiration != null -> {
            // Temporary mute: check if it hasn't expired yet
            val now = kotlin.time.Clock.System.now()
            expiration.isAfter(now)
        }
        app.muteState == MuteState.Never -> false
        else -> {
            // Permanent or schedule-based mute (Always, Weekdays, Weekends)
            true
        }
    }
    val channelMuted = channelItem.muteState != MuteState.Never
    val count = channelCounts[channelItem.id]?.count
    val clickable = count != null && count > 0
    val modifier = if (clickable) {
        Modifier.clickable {
            nav.navigateTo(
                PebbleNavBarRoutes.AppNotificationViewerRoute(
                    packageName = app.packageName,
                    channelId = channelItem.id,
                )
            )
        }
    } else Modifier
    ListItem(
        modifier = modifier,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channelItem.name, fontSize = 17.sp)
                if (clickable) {
                    Badge(modifier = Modifier.padding(horizontal = 7.dp)) {
                        Text("$count")
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (clickable) {
                    Icon(
                        Icons.Outlined.DensitySmall,
                        "View Notifications",
                        modifier = Modifier.padding(start = 4.dp, end = 10.dp)
                    )
                }
                Switch(
                    checked = !channelMuted,
                    onCheckedChange = {
                        val toggledState = if (channelMuted) MuteState.Never else MuteState.Always
                        notificationApps.updateNotificationChannelMuteState(
                            packageName = app.packageName,
                            channelId = channelItem.id,
                            muteState = toggledState,
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color.Red,
                        uncheckedTrackColor = Color.White
                    ),
                    enabled = !appMuted,
                )
            }
        }
    )
}

@Preview
@Composable
fun NotificationAppScreenPreview() {
    PreviewWrapper {
        NotificationAppScreen(
            topBarParams = WrapperTopBarParams,
            nav = NoOpNavBarNav,
            packageName = "com.test.app",
        )
    }
}
