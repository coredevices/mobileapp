package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.ring.agent.DefaultCaptureType
import coredevices.ring.agent.IndexAction
import coredevices.ring.agent.NOT_CONNECTED_REASON
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderServlet
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.screens.settings.mcp.HttpServerEditDialog
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ui.M3Dialog
import coredevices.util.Platform
import coredevices.util.isAndroid
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Actions are only reachable through the Index agent, so they are off when no gesture routes there. */
internal fun nothingRoutesToIndexAgent(routes: Map<RingGesture, GestureDestination>): Boolean =
    routes.values.none { it is GestureDestination.IndexAgent }

internal fun captureTypeLabel(type: DefaultCaptureType): String = type.title.lowercase()

/** The built-in providers are branded "Index Notes" / "Index Reminders" on this screen. The enum
 *  titles are shown verbatim elsewhere, so this stays display-only. */
internal val NoteProvider.settingsTitle: String
    get() = if (this == NoteProvider.Builtin) "Index Notes" else title

internal val ReminderProvider.settingsTitle: String
    get() = if (this == ReminderProvider.BuiltIn) "Index Reminders" else title

internal fun contactsBadge(count: Int): String =
    "$count ${if (count == 1) "contact" else "contacts"}"

internal fun captureDestinationLabel(
    type: DefaultCaptureType,
    noteProvider: NoteProvider,
    reminderProvider: ReminderProvider,
): String = when (type) {
    DefaultCaptureType.Note -> noteProvider.settingsTitle
    DefaultCaptureType.Reminder -> reminderProvider.settingsTitle
}

internal data class DestOption<T>(val provider: T, val title: String, val connected: Boolean)

internal fun noteDestOptions(
    available: List<NoteProvider>,
    isAndroid: Boolean,
): List<DestOption<NoteProvider>> = NoteProvider.entries
    .filter { it != NoteProvider.Tasker || isAndroid }
    .map { DestOption(it, it.settingsTitle, it in available) }

internal fun reminderDestOptions(
    available: List<ReminderProvider>,
    isAndroid: Boolean,
): List<DestOption<ReminderProvider>> = ReminderProvider.entries
    .filter {
        when (it) {
            ReminderProvider.Tasker -> isAndroid
            ReminderProvider.IOSReminders -> !isAndroid
            else -> true
        }
    }
    .map { DestOption(it, it.settingsTitle, it in available) }

/** Actions that are switched off because they still need linking get a Connect button. */
internal fun actionConnectDialog(action: IndexAction): ActionsDialog? =
    ActionsDialog.PhoneCalendar.takeIf {
        action.name == CalendarServlet.NAME && action.disabledReason == NOT_CONNECTED_REASON
    }

internal fun noteConnectDialog(provider: NoteProvider): ActionsDialog? = when (provider) {
    NoteProvider.Notion -> ActionsDialog.Notion
    NoteProvider.Obsidian -> ActionsDialog.Obsidian
    NoteProvider.Tasker -> ActionsDialog.Tasker
    NoteProvider.Builtin -> null
}

internal fun reminderConnectDialog(provider: ReminderProvider): ActionsDialog? = when (provider) {
    ReminderProvider.GoogleTasks -> ActionsDialog.GoogleTasks
    ReminderProvider.Tasker -> ActionsDialog.Tasker
    ReminderProvider.BuiltIn, ReminderProvider.IOSReminders -> null
}

private data class ActionPresentation(val icon: ImageVector, val title: String, val example: String)

private fun presentationFor(action: IndexAction): ActionPresentation = when (action.name) {
    NoteServlet.NAME -> ActionPresentation(
        Icons.Default.EditNote,
        "Notes",
        "“Remember I parked on level 3”",
    )
    ReminderServlet.NAME -> ActionPresentation(
        Icons.Default.Checklist,
        "Reminders",
        "“Remind me to call Mom at 5pm”",
    )
    CalendarServlet.NAME -> ActionPresentation(
        Icons.Default.CalendarMonth,
        "Calendar",
        "“Create new event - lunch with Sarah at noon on Friday” → create event on phone calendar",
    )
    ClockServlet.name -> ActionPresentation(
        Icons.Default.Alarm,
        "Timers & Alarms",
        "“Set an alarm for 7:30” → alarm on this phone",
    )
    MessagingServlet.name -> ActionPresentation(
        Icons.AutoMirrored.Filled.Chat,
        "Beeper Messaging",
        "“Tell Jake I’m running late” → message to an approved contact",
    )
    else -> ActionPresentation(Icons.Default.Extension, action.title, "")
}

private enum class ActionsSheet { CaptureType, NoteDestination, ReminderDestination }
internal enum class ActionsDialog { AddServer, Sideload, Notion, GoogleTasks, Obsidian, Tasker, PhoneCalendar }

@Composable
fun IndexActionsSection(
    actions: List<IndexAction>,
    httpMcpDisabledReason: String?,
    availableNoteProviders: List<NoteProvider>,
    availableReminderProviders: List<ReminderProvider>,
    onSetActionEnabled: (name: String, enabled: Boolean) -> Unit,
    onAddIntegration: () -> Unit,
    onOpenBeeperContacts: () -> Unit,
    onGetMoreActions: () -> Unit,
    onProvidersChanged: () -> Unit,
    showHeader: Boolean = true,
) {
    val gestureRouting = koinInject<GestureRoutingPreferences>()
    val routes by gestureRouting.routes.collectAsState()
    if (nothingRoutesToIndexAgent(routes)) {
        NoIndexAgentCard()
        return
    }

    val preferences = koinInject<Preferences>()
    val sandboxRepository = koinInject<McpSandboxRepository>()
    val isAndroid = koinInject<Platform>().isAndroid
    val scope = rememberCoroutineScope()
    val colors = IndexTheme.colors

    val captureType by preferences.defaultCaptureType.collectAsState()
    val noteProvider by preferences.noteProvider.collectAsState()
    val reminderProvider by preferences.reminderProvider.collectAsState()
    val approvedContacts by preferences.approvedBeeperContacts.collectAsState()
    var sheet by remember { mutableStateOf<ActionsSheet?>(null) }
    var dialog by remember { mutableStateOf<ActionsDialog?>(null) }
    // Connecting from a destination sheet is a pick as well as a connect, but the connect dialogs
    // only report dismissal, so the choice is applied once the provider shows up as available.
    var pendingNoteConnect by remember { mutableStateOf<NoteProvider?>(null) }
    var pendingReminderConnect by remember { mutableStateOf<ReminderProvider?>(null) }
    val closeConnectDialog = {
        dialog = null
        onProvidersChanged()
    }

    LaunchedEffect(availableNoteProviders) {
        pendingNoteConnect?.takeIf { it in availableNoteProviders }?.let {
            preferences.setNoteProvider(it)
            pendingNoteConnect = null
        }
    }
    LaunchedEffect(availableReminderProviders) {
        pendingReminderConnect?.takeIf { it in availableReminderProviders }?.let {
            preferences.setReminderProvider(it)
            pendingReminderConnect = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showHeader) {
            SectionHeader(
                title = "Index Agent",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 2.dp),
            )
            Text(
                "Select which actions Index Agent can perform.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
        CaptureSentence(
            typeLabel = captureTypeLabel(captureType),
            destinationLabel = captureDestinationLabel(captureType, noteProvider, reminderProvider),
            onTypeClick = { sheet = ActionsSheet.CaptureType },
            onDestinationClick = {
                sheet = when (captureType) {
                    DefaultCaptureType.Note -> ActionsSheet.NoteDestination
                    DefaultCaptureType.Reminder -> ActionsSheet.ReminderDestination
                }
            },
        )
        actions.forEach { action ->
            ActionRow(
                action = action,
                presentation = presentationFor(action),
                badge = contactsBadge(approvedContacts.size)
                    .takeIf { action.name == MessagingServlet.name && approvedContacts.isNotEmpty() },
                destinationLabel = when (action.name) {
                    NoteServlet.NAME -> noteProvider.settingsTitle
                    ReminderServlet.NAME -> reminderProvider.settingsTitle
                    else -> null
                },
                onDestinationClick = {
                    sheet = if (action.name == NoteServlet.NAME) {
                        ActionsSheet.NoteDestination
                    } else {
                        ActionsSheet.ReminderDestination
                    }
                },
                onSettings = onOpenBeeperContacts
                    .takeIf { action.name == MessagingServlet.name && action.disabledReason == null },
                onConnect = actionConnectDialog(action)?.let { target -> { dialog = target } },
                onToggle = { onSetActionEnabled(action.name, it) },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        // The Actions store is future expansion and ships disabled.
        // GetMoreActionsRow(onClick = onGetMoreActions)
        SectionActionRow(
            icon = Icons.Default.Dns,
            title = "Add MCP server",
            subtitle = httpMcpDisabledReason ?: "Connect a remote server over HTTP",
            enabled = httpMcpDisabledReason == null,
            onClick = { dialog = ActionsDialog.AddServer },
        )
        // Sideloading is future expansion and ships disabled.
        // SectionActionRow(
        //     icon = Icons.Default.UploadFile,
        //     title = "Sideload an action (.pbw)",
        //     subtitle = "Install a plugin you built or downloaded",
        //     enabled = true,
        //     onClick = { dialog = ActionsDialog.Sideload },
        // )
    }

    when (sheet) {
        ActionsSheet.CaptureType -> CaptureTypeSheet(
            current = captureType,
            onSelect = {
                preferences.setDefaultCaptureType(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        ActionsSheet.NoteDestination -> DestinationSheet(
            icon = Icons.Default.EditNote,
            title = "Where notes save",
            options = noteDestOptions(availableNoteProviders, isAndroid),
            selected = noteProvider,
            onSelect = {
                pendingNoteConnect = null
                preferences.setNoteProvider(it)
            },
            onConnect = { provider ->
                pendingNoteConnect = provider
                noteConnectDialog(provider)?.let { dialog = it } ?: onAddIntegration()
            },
            onDismiss = { sheet = null },
        )
        ActionsSheet.ReminderDestination -> DestinationSheet(
            icon = Icons.Default.Checklist,
            title = "Where reminders save",
            options = reminderDestOptions(availableReminderProviders, isAndroid),
            selected = reminderProvider,
            onSelect = {
                pendingReminderConnect = null
                preferences.setReminderProvider(it)
            },
            onConnect = { provider ->
                pendingReminderConnect = provider
                reminderConnectDialog(provider)?.let { dialog = it } ?: onAddIntegration()
            },
            onDismiss = { sheet = null },
        )
        null -> {}
    }

    when (dialog) {
        ActionsDialog.AddServer -> {
            val groups by sandboxRepository.getAllGroupsFlow().collectAsState(emptyList())
            HttpServerEditDialog(
                initialServer = null,
                allGroups = groups,
                initialGroupIds = setOfNotNull(groups.firstOrNull()?.id),
                onDismiss = { dialog = null },
                onConfirm = { server, groupIds ->
                    scope.launch { sandboxRepository.addOrUpdateHttpServer(server, groupIds) }
                    dialog = null
                },
                onDelete = null,
            )
        }
        ActionsDialog.Sideload -> SideloadDialog(onDismiss = { dialog = null })
        ActionsDialog.Notion -> NotionDialog(onDismiss = closeConnectDialog)
        ActionsDialog.GoogleTasks -> GTasksDialog(onDismiss = closeConnectDialog)
        ActionsDialog.Obsidian -> ObsidianDialog(onDismiss = closeConnectDialog)
        ActionsDialog.Tasker -> TaskerDialog(onDismiss = closeConnectDialog)
        ActionsDialog.PhoneCalendar -> PhoneCalendarDialog(onDismiss = { dialog = null })
        null -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureSentence(
    typeLabel: String,
    destinationLabel: String,
    onTypeClick: () -> Unit,
    onDestinationClick: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SentenceText("Any unclear requests become a")
        SentenceLink(typeLabel, onTypeClick)
        SentenceText("in")
        SentenceLink(destinationLabel, onDestinationClick)
    }
}

@Composable
private fun SentenceText(text: String) {
    Text(text, fontSize = 13.sp, color = IndexTheme.colors.onSurfaceVariant)
}

@Composable
private fun SentenceLink(label: String, onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.primary,
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ActionRow(
    action: IndexAction,
    presentation: ActionPresentation,
    badge: String?,
    destinationLabel: String?,
    onDestinationClick: () -> Unit,
    onSettings: (() -> Unit)?,
    onConnect: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
) {
    val colors = IndexTheme.colors
    val interactive = action.disabledReason == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = interactive) { onToggle(!action.enabled) }
            // A connectable row is actionable, so it stays legible rather than dimmed.
            .alpha(if (interactive || onConnect != null) 1f else 0.38f)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            presentation.icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    presentation.title,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                )
                if (badge != null) {
                    Text(
                        badge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .border(
                                1.dp,
                                colors.outlineVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                action.disabledReason ?: presentation.example,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.onSurfaceVariant,
            )
            if (destinationLabel != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .border(
                            1.dp,
                            colors.outlineVariant,
                            RoundedCornerShape(100.dp),
                        )
                        .clickable(enabled = interactive, onClick = onDestinationClick)
                        .padding(start = 10.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Saves to",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        destinationLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (onSettings != null) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onSettings)
                    .padding(8.dp)
                    .size(20.dp),
            )
        }
        if (onConnect != null) {
            TextButton(onClick = onConnect) { Text("Connect") }
        } else {
            Checkbox(
                checked = action.enabled,
                enabled = interactive,
                onCheckedChange = { onToggle(it) },
            )
        }
    }
}

@Composable
private fun SectionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.onSurface)
            Text(
                subtitle,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NoIndexAgentCard() {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .dashedRoundedBorder(colors.outlineVariant, 12.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            buildAnnotatedString {
                append("Nothing routes to the Index agent right now, so actions are off. Point a gesture at ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Index agent") }
                append(" above to turn them back on.")
            },
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureTypeSheet(
    current: DefaultCaptureType,
    onSelect: (DefaultCaptureType) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheetSurface) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Text(
                    "Any unclear requests become",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    "Used when Index can't tell what you meant.",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            DefaultCaptureType.entries.forEach { type ->
                SheetOptionRow(
                    icon = when (type) {
                        DefaultCaptureType.Note -> Icons.Default.EditNote
                        DefaultCaptureType.Reminder -> Icons.Default.Checklist
                    },
                    title = type.title,
                    subtitle = when (type) {
                        DefaultCaptureType.Note -> "A thought to keep, nothing to do"
                        DefaultCaptureType.Reminder -> "Something you intend to do"
                    },
                    selected = type == current,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

@Composable
private fun SheetOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .selectedSheetRowBackground(selected)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.primary
                else colors.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = colors.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        trailing()
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DestinationSheet(
    icon: ImageVector,
    title: String,
    options: List<DestOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onConnect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheetSurface) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
            }
            options.forEach { option ->
                val connect = {
                    onDismiss()
                    onConnect(option.provider)
                }
                SheetOptionRow(
                    title = option.title,
                    subtitle = "Not connected".takeIf { !option.connected },
                    selected = option.provider == selected,
                    onClick = {
                        if (option.connected) {
                            onSelect(option.provider)
                            onDismiss()
                        } else {
                            connect()
                        }
                    },
                    trailing = {
                        if (!option.connected) {
                            TextButton(onClick = connect) { Text("Connect") }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SideloadDialog(onDismiss: () -> Unit) {
    val colors = IndexTheme.colors
    var picked by remember { mutableStateOf(false) }
    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Sideload an action") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Pick a .pbw that includes MCP tools. It runs in a JS sandbox and shows up in " +
                    "your Actions list, unchecked until you enable it.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedRoundedBorder(colors.outline, 12.dp)
                    .clickable { picked = true }
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "Choose a .pbw file",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                )
            }
            if (picked) {
                Text(
                    "Sideloading isn't available yet.",
                    fontSize = 13.sp,
                    color = colors.primary,
                )
            }
        }
    }
}

@Composable
internal fun Modifier.selectedSheetRowBackground(selected: Boolean): Modifier = then(
    if (selected) {
        Modifier.background(IndexTheme.colors.redSurface, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
)

private fun Modifier.dashedRoundedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}

@Composable
private fun GetMoreActionsRow(onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Storefront,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            "Get more actions",
            fontSize = 16.sp,
            color = colors.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
