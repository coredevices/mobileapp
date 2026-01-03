package coredevices.pebble.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import coredevices.pebble.PebbleFeatures
import coredevices.pebble.Platform
import coredevices.pebble.account.BootConfig
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.iconUrlFor
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.database.isAfter
import io.rebble.libpebblecommon.database.dao.AppWithCount
import io.rebble.libpebblecommon.database.entity.MuteState
import kotlin.time.Clock
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class NotificationScreenViewModel : ViewModel() {
    val tab = mutableStateOf(NotificationTab.Apps)
}

enum class NotificationTab(val title: String) {
    Apps("Apps"),
    Contacts("Contacts"),
//    Rules("Rules"),
//    History("History"),
}

enum class NotificationAppSort {
    Name,
    Recent,
    Count,
}

@Composable
fun NotificationsScreen(topBarParams: TopBarParams, nav: NavBarNav, canGoBack: Boolean) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val viewModel = koinViewModel<NotificationScreenViewModel>()
        val pebbleFeatures = koinInject<PebbleFeatures>()

        LaunchedEffect(Unit) {
            topBarParams.title("Notifications")
        }

        Column {
            if (pebbleFeatures.supportsNotificationFiltering()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                    ) {
                        NotificationTab.entries.forEachIndexed { index, tab ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = NotificationTab.entries.size,
                                ),
                                onClick = { viewModel.tab.value = tab },
                                selected = viewModel.tab.value == tab,
                                label = { Text(tab.title) },
                                icon = {},
                            )
                        }
                    }
                }
            }
            when (viewModel.tab.value) {
                NotificationTab.Apps -> NotificationAppsScreen(topBarParams, nav, canGoBack)
                NotificationTab.Contacts -> NotificationContactsScreen(topBarParams, nav, canGoBack)
//            NotificationTab.Rules -> NotificationRulesScreen(topBarParams, nav)
//            NotificationTab.History -> NotificationHistoryScreen(topBarParams, nav)
            }
        }
    }
}

@Preview
@Composable
fun NotificationsScreenPreview() {
    PreviewWrapper {
        NotificationsScreen(
            topBarParams = WrapperTopBarParams,
            nav = NoOpNavBarNav,
            canGoBack = false,
        )
    }
}

val HEIGHT = 42.dp

@Composable
fun NotificationAppCard(
    entry: AppWithCount,
    notificationApps: NotificationApps,
    bootConfig: BootConfig?,
    platform: Platform,
    nav: NavBarNav,
    clickable: Boolean,
    showBadge: Boolean,
) {
    val reallyClickable = clickable && platform == Platform.Android
    val app = entry.app
    val modifier = if (reallyClickable) {
        Modifier.clickable {
            nav.navigateTo(PebbleNavBarRoutes.NotificationAppRoute(app.packageName))
        }
    } else Modifier
    val muted = remember(app) {
        val expiration = app.muteExpiration
        when {
            // Check temporary mute first (takes priority)
            expiration != null -> {
                // Temporary mute: check if it hasn't expired yet
                val now = Clock.System.now()
                expiration.isAfter(now)
            }
            app.muteState == MuteState.Never -> false
            else -> {
                // Permanent or schedule-based mute (Always, Weekdays, Weekends)
                true
            }
        }
    }
    ListItem(
        modifier = modifier,
        leadingContent = {
            AppIconImage(
                entry = entry,
                modifier = Modifier.width(HEIGHT),
                notificationApps = notificationApps,
                bootConfig = bootConfig,
                platform = platform,
                showBadge = showBadge,
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = app.name,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.count > 0) {
                    Badge(modifier = Modifier.padding(horizontal = 7.dp)) {
                        Text(
                            text = "${entry.count}",
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reallyClickable) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        "Details",
                        modifier = Modifier.padding(start = 4.dp, end = 10.dp)
                    )
                }
                Switch(checked = !muted, onCheckedChange = {
                    val toggledState = if (muted) MuteState.Never else MuteState.Always
                    notificationApps.updateNotificationAppMuteState(app.packageName, toggledState)
                })
            }
        },
        shadowElevation = 2.dp,
    )
}

@Composable
fun rememberBootConfig(): BootConfig? {
    val provider = koinInject<BootConfigProvider>()
    val bootConfig by produceState<BootConfig?>(initialValue = null) {
        value = provider.getBootConfig()
    }
    return bootConfig
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconImage(
    entry: AppWithCount,
    modifier: Modifier,
    notificationApps: NotificationApps,
    bootConfig: BootConfig?,
    platform: Platform,
    showBadge: Boolean,
) {
    val app = entry.app
    val contentDescription = remember(app.packageName) { "${app.name} icon" }
    when (platform) {
        // iOS: load from web service
        Platform.IOS -> {
            val url = derivedStateOf {
                bootConfig?.config?.notifications?.iconUrlFor(
                    app.packageName,
                    120
                )
            }
            AsyncImage(
                model = url.value,
                contentDescription = contentDescription,
                modifier = modifier,
            )
        }
        // Android: load from OS
        Platform.Android -> {
            val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
                value = notificationApps.getAppIcon(app.packageName)
            }
            icon.let {
                if (it != null) {
                    Box(modifier = modifier) {
                        Image(
                            it,
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (showBadge && entry.count > 0) {
                            Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                                Text("${entry.count}")
                            }
                        }
                    }
                } else {
                    Box(modifier)
                }
            }
        }
    }
}