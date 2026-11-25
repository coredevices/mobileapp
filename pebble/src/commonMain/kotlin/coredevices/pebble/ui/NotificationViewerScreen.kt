package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.pebble.Platform
import coredevices.pebble.rememberLibPebble
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.notification.NotificationDecision
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.notif_app_title
import coreapp.pebble.generated.resources.notif_viewer_recent_for
import coreapp.pebble.generated.resources.notif_viewer_recent_for_channel
import coreapp.pebble.generated.resources.notif_viewer_empty
import coreapp.pebble.generated.resources.notif_viewer_displayed
import coreapp.pebble.generated.resources.notif_viewer_not_displayed
import coreapp.pebble.generated.resources.notif_decision_sent
import coreapp.pebble.generated.resources.notif_decision_local_only
import coreapp.pebble.generated.resources.notif_decision_group_summary
import coreapp.pebble.generated.resources.notif_decision_app_muted
import coreapp.pebble.generated.resources.notif_decision_channel_muted
import coreapp.pebble.generated.resources.notif_decision_duplicate
import coreapp.pebble.generated.resources.notif_decision_contact_muted
import coreapp.pebble.generated.resources.notif_decision_screen_on
import coreapp.pebble.generated.resources.notif_decision_rule_filtered
import coreapp.pebble.generated.resources.notif_decision_empty
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppNotificationViewerScreen(
    topBarParams: TopBarParams,
    nav: NavBarNav,
    packageName: String,
    channelId: String?,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(null)
            topBarParams.actions {}
            topBarParams.title(getString(Res.string.notif_app_title))
        }
        val libPebble = rememberLibPebble()
        val appWrapperFlow = remember(packageName) {
            libPebble.notificationApps()
                .map { it.firstOrNull { it.app.packageName == packageName } }
        }
        val appWrapper by appWrapperFlow.collectAsState(null)
        val channel =
            appWrapper?.app?.channelGroups?.flatMap { it.channels }
                ?.firstOrNull { it.id == channelId }
        appWrapper?.let { appWrapper ->
            val bootConfig = rememberBootConfig()
            val platform = koinInject<Platform>()
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(
                        entry = appWrapper,
                        modifier = Modifier.width(100.dp).height(100.dp).padding(15.dp),
                        notificationApps = libPebble,
                        bootConfig = bootConfig,
                        platform = platform,
                        showBadge = false,
                    )
                    val text = if (channelId == null) {
                        stringResource(Res.string.notif_viewer_recent_for, appWrapper.app.name)
                    } else {
                        stringResource(Res.string.notif_viewer_recent_for_channel, appWrapper.app.name, channel?.name.orEmpty())
                    }
                    Text(text, modifier = Modifier.padding(8.dp))
                }
                NotificationHistoryList(
                    packageName = packageName,
                    channelId = channelId,
                    contactId = null,
                    limit = 25,
                    showAppIcon = false,
                )
            }
        }
    }
}

@Composable
fun NotificationHistoryList(
    packageName: String?,
    channelId: String?,
    contactId: String?,
    limit: Int,
    showAppIcon: Boolean,
) {
    val libPebble = rememberLibPebble()
    val notificationsFlow = remember(packageName, channelId) {
        libPebble.mostRecentNotificationsFor(
            pkg = packageName,
            channelId = channelId,
            contactId = contactId,
            limit = limit,
        ).map { it.filter { it.decision != NotificationDecision.NotSentGroupSummary } }
    }
    val bootConfig = rememberBootConfig()
    val notifications by notificationsFlow.collectAsState(emptyList())
    val notificationApi: NotificationApps = koinInject()
    val platform = koinInject<Platform>()

    LazyColumn {
        items(notifications, key = { it.id }) { notification ->
            val appWrapperFlow = remember(packageName) {
                notificationApi.notificationApps()
                    .map { it.firstOrNull { it.app.packageName == notification.pkg } }
            }
            val appWrapper by appWrapperFlow.collectAsState(null)
            val app = appWrapper
            ListItem(
                headlineContent = {
                    Text(
                        text = notification.title ?: stringResource(Res.string.notif_viewer_empty),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = notification.body?.take(100)?.replace("\n", " ")
                            ?: stringResource(Res.string.notif_viewer_empty),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp,
                    )
                },
                leadingContent = {
                    if (showAppIcon && app != null) {
                        AppIconImage(
                            entry = app,
                            modifier = Modifier.width(HEIGHT),
                            notificationApps = notificationApi,
                            bootConfig = bootConfig,
                            platform = platform,
                            showBadge = false,
                        )
                    }
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            notification.timestamp.instant.toLocalDateTime(TimeZone.currentSystemDefault())
                                .format(FORMAT),
                        )
                        val sentText =
                            if (notification.decision == NotificationDecision.SendToWatch) {
                                stringResource(Res.string.notif_viewer_displayed)
                            } else {
                                stringResource(Res.string.notif_viewer_not_displayed, notification.decision.displayName())
                            }
                        Text(sentText, fontSize = 10.sp)
                    }
                }
            )
        }
    }
}

@Composable
fun NotificationDecision.displayName(): String = when (this) {
    NotificationDecision.SendToWatch -> stringResource(Res.string.notif_decision_sent)
    NotificationDecision.NotSentLocalOnly -> stringResource(Res.string.notif_decision_local_only)
    NotificationDecision.NotSentGroupSummary -> stringResource(Res.string.notif_decision_group_summary)
    NotificationDecision.NotSentAppMuted -> stringResource(Res.string.notif_decision_app_muted)
    NotificationDecision.NotSendChannelMuted -> stringResource(Res.string.notif_decision_channel_muted)
    NotificationDecision.NotSentDuplicate -> stringResource(Res.string.notif_decision_duplicate)
    NotificationDecision.NotSendContactMuted -> stringResource(Res.string.notif_decision_contact_muted)
    NotificationDecision.NotSentScreenOn -> stringResource(Res.string.notif_decision_screen_on)
    NotificationDecision.NotSentRuleFiltered -> stringResource(Res.string.notif_decision_rule_filtered)
    NotificationDecision.NotSentEmpty -> stringResource(Res.string.notif_decision_empty)
}

private val FORMAT = LocalDateTime.Format {
    date(LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); dayOfMonth(); chars(
        " "
    );
    })
    chars(" - ")
    time(LocalTime.Format { hour(); char(':'); minute() })
}