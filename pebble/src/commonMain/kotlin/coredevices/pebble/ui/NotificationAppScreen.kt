package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import kotlin.time.Clock
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DensitySmall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.rebble.libpebblecommon.database.entity.MatchField
import io.rebble.libpebblecommon.database.entity.MatchType
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.database.entity.NotificationRuleEntity
import io.rebble.libpebblecommon.database.entity.TargetType
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
            topBarParams.searchAvailable(null)
            topBarParams.actions {}
            topBarParams.title("App Notifications")
        }
        appWrapper?.let { appWrapper ->
            val app = appWrapper.app
            val bootConfig = rememberBootConfig()
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    NotificationAppCard(
                        entry = appWrapper,
                        notificationApps = notificationApps,
                        bootConfig = bootConfig,
                        platform = platform,
                        nav = nav,
                        clickable = false,
                        showBadge = false,
                    )
                }
                item {
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
                }
                item {
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
                }
                item {
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
                }
                item {
                    NotificationRulesSection(
                        app = appWrapper.app,
                        notificationApps = notificationApps,
                    )
                }
                // Channels section - only available on Android (iOS doesn't have notification channels)
                if (platform == Platform.Android) {
                    item {
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
                    }
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

                if (platform == Platform.IOS) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                val expiration = app.muteExpiration
                val now = Clock.System.now()
                val isTemporaryMuted = expiration != null && expiration.isAfter(now)

                val muteReasonText = when {
                    isTemporaryMuted -> {
                        val duration = expiration!!.instant - now
                        val timeString = duration.toComponents { hours, minutes, _, _ ->
                            if (hours > 0) {
                                "${hours}h ${minutes}m"
                            } else {
                                "${minutes}m"
                            }
                        }
                        if (duration.inWholeHours >= 2) {
                            "Status: Muted for the day ($timeString left)"
                        } else {
                            "Status: Muted for 1 hour ($timeString left)"
                        }
                    }
                    app.muteState == MuteState.Always -> "Status: Muted (Always)"
                    app.muteState == MuteState.Weekdays -> "Status: Muted (Weekdays)"
                    app.muteState == MuteState.Weekends -> "Status: Muted (Weekends)"
                    else -> null
                }

                if (muteReasonText != null) {
                    Text(
                        text = muteReasonText,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRulesSection(
    app: NotificationAppItem,
    notificationApps: NotificationApps,
) {
    val query = remember(app) { notificationApps.notificationRulesForApp(app.packageName) }
    val rules by query.collectAsState(emptyList())
    var editingRule by remember { mutableStateOf<NotificationRuleEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.padding(10.dp).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Notification filter rules", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                SegmentedButton(
                    selected = !app.filterIsAllowlist,
                    onClick = {
                        if (app.filterIsAllowlist) {
                            notificationApps.updateFilterIsAllowlist(app.packageName, false)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Block if matching") }
                SegmentedButton(
                    selected = app.filterIsAllowlist,
                    onClick = {
                        if (!app.filterIsAllowlist) {
                            notificationApps.updateFilterIsAllowlist(app.packageName, true)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Allow if matching") }
            }
            rules.forEach { rule ->
                ListItem(
                    headlineContent = {
                        Text(rule.pattern, maxLines = 1)
                    },
                    supportingContent = {
                        val matchTypeLabel = if (rule.matchType == MatchType.Regex) "Regex" else "Text"
                        val fieldLabel = when (rule.matchField) {
                            MatchField.Title -> "Title"
                            MatchField.Body -> "Body"
                            MatchField.Both -> "Title+Body"
                        }
                        val caseLabel = if (rule.caseSensitive) "Case-sensitive" else "Case-insensitive"
                        Text("$matchTypeLabel on $fieldLabel, $caseLabel")
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                editingRule = rule
                                showDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit rule")
                            }
                            IconButton(onClick = {
                                notificationApps.deleteNotificationRule(rule)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
                            }
                        }
                    },
                )
            }
            AssistChip(
                onClick = {
                    editingRule = null
                    showDialog = true
                },
                label = { Text("Add rule") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (showDialog) {
        NotificationRuleDialog(
            existing = editingRule,
            onDismiss = { showDialog = false },
            onSave = { rule ->
                val entity = rule.copy(
                    targetType = TargetType.App,
                    target = app.packageName,
                )
                notificationApps.upsertNotificationRule(entity)
                showDialog = false
            },
        )
    }
}

@Composable
private fun NotificationRuleDialog(
    existing: NotificationRuleEntity?,
    onDismiss: () -> Unit,
    onSave: (NotificationRuleEntity) -> Unit,
) {
    var matchType by remember(existing) { mutableStateOf(existing?.matchType ?: MatchType.Text) }
    var matchField by remember(existing) { mutableStateOf(existing?.matchField ?: MatchField.Both) }
    var pattern by remember(existing) { mutableStateOf(existing?.pattern ?: "") }
    var caseSensitive by remember(existing) { mutableStateOf(existing?.caseSensitive ?: false) }

    val regexError = remember(pattern, matchType) {
        if (matchType == MatchType.Regex && pattern.isNotEmpty()) {
            try { Regex(pattern); null } catch (e: Exception) { e.message ?: "Invalid regex" }
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit rule" else "Add rule") },
        text = {
            Column {
                Text("Match type", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = matchType == MatchType.Text,
                        onClick = { matchType = MatchType.Text },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Text") }
                    SegmentedButton(
                        selected = matchType == MatchType.Regex,
                        onClick = { matchType = MatchType.Regex },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Regex") }
                }
                Spacer(Modifier.height(12.dp))
                Text("Match field", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = matchField == MatchField.Both,
                        onClick = { matchField = MatchField.Both },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    ) { Text("Both") }
                    SegmentedButton(
                        selected = matchField == MatchField.Title,
                        onClick = { matchField = MatchField.Title },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    ) { Text("Title") }
                    SegmentedButton(
                        selected = matchField == MatchField.Body,
                        onClick = { matchField = MatchField.Body },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    ) { Text("Body") }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    isError = regexError != null,
                    supportingText = if (regexError != null) {{ Text(regexError) }} else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                        .clickable { caseSensitive = !caseSensitive },
                ) {
                    Checkbox(
                        checked = caseSensitive,
                        onCheckedChange = null,
                    )
                    Text("Case sensitive", modifier = Modifier.padding(start = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        NotificationRuleEntity(
                            id = existing?.id ?: 0,
                            targetType = existing?.targetType ?: TargetType.App,
                            target = existing?.target,
                            matchType = matchType,
                            matchField = matchField,
                            pattern = pattern,
                            caseSensitive = caseSensitive,
                        )
                    )
                },
                enabled = pattern.isNotBlank() && regexError == null,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
    val now = Clock.System.now()
    val appMuted = when {
        expiration != null && expiration.isAfter(now) -> true
        app.muteState == MuteState.Never -> false
        else -> true
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
