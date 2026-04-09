package coredevices.ring.ui.screens.settings

import BugReportButton
import CoreNav
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.ring_wireframe
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.settings
import coredevices.EnableExperimentalDevices
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.external.vermillion.VermillionSettingsDialog
import coredevices.ring.external.vermillion.VermillionSettingsViewModel
import coredevices.ring.ui.PreviewWrapper
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ui.M3Dialog
import coredevices.ui.ModelDownloadDialog
import coredevices.ui.SignInDialog
import coredevices.util.Platform
import coredevices.util.isAndroid
import coredevices.util.isIOS
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun IndexSettings(coreNav: CoreNav) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val vermillionViewModel = koinViewModel<VermillionSettingsViewModel>()
    val useCactusAgent by viewModel.useCactusAgent.collectAsState()
    val showModelDialog by viewModel.showModelDownloadDialog.collectAsState()
    val showMusicControlDialog by viewModel.showMusicControlDialog.collectAsState()
    val debugDetailsEnabled by viewModel.debugDetailsEnabled.collectAsState()
    val showContactsDialog by viewModel.showContactsDialog.collectAsState()
    val showSecondaryModeDialog by viewModel.showSecondaryModeDialog.collectAsState()
    val showNoteShortcutDialog by viewModel.showNoteShortcutDialog.collectAsState()
    val platform = koinInject<Platform>()
    val vermillionToken by vermillionViewModel.token.collectAsState()
    val vermillionDialogOpen by vermillionViewModel.dialogOpen.collectAsState()
    val experimentalDevicesEnabled by koinInject<EnableExperimentalDevices>().enabled.collectAsState()
    val currentRingFirmware by viewModel.currentRingFirmware.collectAsStateWithLifecycle()
    val currentRing by viewModel.currentRingName.collectAsStateWithLifecycle()
    val currentRingPaired = viewModel.ringPaired.collectAsStateWithLifecycle()
    val ringPaired by remember { derivedStateOf { currentRingPaired.value != null } }
    val accountUsername by viewModel.username.collectAsStateWithLifecycle()
    val preferences = koinInject<Preferences>()
    val musicControlMode by viewModel.musicControlMode.collectAsState()
    val secondaryMode by viewModel.secondaryMode.collectAsState()
    val noteShortcut by viewModel.noteShortcut.collectAsState()
    var showSignInDialog by remember { mutableStateOf(false) }

    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }
    if (showModelDialog != null) {
        ModelDownloadDialog(
            onDismissRequest = { success ->
                viewModel.onModelDownloadDialogDismissed(success)
            },
            models = setOf(showModelDialog!!)
        )
    }
    if (showContactsDialog && platform.isAndroid) {
        SettingsBeeperContactsDialog(
            onDismissRequest = viewModel::closeContactsDialog
        )
    }
    if (showMusicControlDialog) {
        val mode = viewModel.musicControlMode.collectAsState()
        MusicControlDialog(
            currentMode = mode.value,
            onModeSelected = { mode ->
                viewModel.setMusicControlMode(mode)
                viewModel.closeMusicControlDialog()
            },
            onDismissRequest = {
                viewModel.closeMusicControlDialog()
            }
        )
    }
    if (showSecondaryModeDialog) {
        val mode = viewModel.secondaryMode.collectAsState()
        SecondaryModeDialog(
            currentMode = mode.value,
            onModeSelected = {
                viewModel.setSecondaryMode(it)
                viewModel.closeSecondaryModeDialog()
            },
            onDismissRequest = {
                viewModel.closeSecondaryModeDialog()
            },
            vermillionEnabled = vermillionToken != null,
            vermillionShown = experimentalDevicesEnabled
        )
    }
    if (showNoteShortcutDialog) {
        val shortcut = viewModel.noteShortcut.collectAsState()
        NoteShortcutDialog(
            currentShortcut = shortcut.value,
            onShortcutSelected = {
                viewModel.setNoteShortcut(it)
                viewModel.closeNoteShortcutDialog()
            },
            onDismissRequest = {
                viewModel.closeNoteShortcutDialog()
            }
        )
    }
    if (vermillionDialogOpen) {
        VermillionSettingsDialog(vermillionViewModel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = coreNav::goBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(coreapp.util.generated.resources.Res.string.back)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(UtilRes.string.settings),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "Settings",
                            "useCactusAgent" to useCactusAgent.toString(),
                            "username" to (accountUsername ?: "null")
                        )
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxHeight()
        ) {
            // Device card
            item {
                IndexDeviceListItem(
                    headline = when {
                        ringPaired && currentRing != null -> (currentRing ?: "")
                        ringPaired -> "Paired to Index 01"
                        else -> "No Ring Paired"
                    },
                    buttons = {
                        val text = when {
                            accountUsername == null -> "Log in before pairing"
                            ringPaired -> "Pair different device"
                            else -> "Pair"
                        }
                        Button(
                            onClick = {
                                if (accountUsername == null) {
                                    showSignInDialog = true
                                } else {
                                    coreNav.navigateTo(RingRoutes.RingPairing)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(text)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // --- Button Actions section ---
            item {
                SectionHeader(
                    leadingContent = {
                        Text(
                            "Button Actions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {}
                )
            }
            item {
                val isAndroid = platform.isAndroid
                ListItem(
                    modifier = Modifier.clickable(enabled = isAndroid) {
                        viewModel.showMusicControlDialog()
                    },
                    headlineContent = {
                        Text(
                            if (isAndroid) "Music Play/Pause" else "Music Play/Pause (Only on Android)",
                            color = if (isAndroid) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    },
                    supportingContent = {
                        Text(
                            when {
                                !isAndroid -> "Not available on this platform"
                                else -> when (musicControlMode) {
                                    MusicControlMode.Disabled -> "Disabled"
                                    MusicControlMode.SingleClick -> "Single click: Play/Pause · Double click: Next track"
                                    MusicControlMode.DoubleClick -> "Double click: Play/Pause · Triple click: Next track"
                                }
                            },
                            color = if (isAndroid) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.showSecondaryModeDialog()
                    },
                    headlineContent = { Text("Double click and hold") },
                    supportingContent = {
                        Text(
                            when (secondaryMode) {
                                SecondaryMode.Disabled -> "Disabled"
                                SecondaryMode.Search -> "Search"
                                SecondaryMode.Vermillion -> "Vermillion"
                            }
                        )
                    }
                )
            }

            // --- Accounts section ---
            item {
                SectionHeader(
                    leadingContent = {
                        Text(
                            "Accounts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { coreNav.navigateTo(RingRoutes.AddIntegration) },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Account")
                        }
                    }
                )
            }
            item {
                AuthorizedIntegrations(preferences)
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.showNoteShortcutDialog()
                    },
                    headlineContent = { Text("Notification Shortcut") },
                    supportingContent = {
                        Text(
                            when (noteShortcut) {
                                is NoteShortcutType.SendToMe -> "Email me"
                                is NoteShortcutType.SendToNoteProvider -> (noteShortcut as NoteShortcutType.SendToNoteProvider).provider.title
                                is NoteShortcutType.SendToReminderProvider -> (noteShortcut as NoteShortcutType.SendToReminderProvider).provider.title
                            }
                        )
                    }
                )
            }

            // --- Advanced section ---
            item {
                SectionHeader(
                    leadingContent = {
                        Text(
                            "Advanced",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {}
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        coreNav.navigateTo(RingRoutes.McpSandboxSettings)
                    },
                    headlineContent = { Text("MCP & Tool Settings") },
                    supportingContent = {
                        Text("Configure MCP sandbox and built-in functionality")
                    }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = viewModel::showContactsDialog, enabled = platform.isAndroid),
                    headlineContent = { Text("Beeper Contacts") },
                    supportingContent = {
                        if (platform.isAndroid) {
                            Text("Approve Index to send messages to specific contacts")
                        } else {
                            Text("Available on Android only")
                        }
                    }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = viewModel::toggleCactusAgent),
                    headlineContent = { Text("Use Cactus Agent") },
                    supportingContent = {
                        Text("Experimental agent with enhanced capabilities")
                    },
                    trailingContent = {
                        Switch(
                            checked = useCactusAgent,
                            onCheckedChange = { viewModel.toggleCactusAgent() }
                        )
                    }
                )
            }
            if (experimentalDevicesEnabled) {
                item {
                    ListItem(
                        modifier = Modifier.clickable(onClick = vermillionViewModel::openDialog),
                        headlineContent = { Text("Vermillion") },
                        supportingContent = {
                            Text(
                                if (vermillionToken != null) "Linked, tap to modify"
                                else "Not Linked"
                            )
                        }
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Ring Firmware Version") },
                    supportingContent = {
                        Text(currentRingFirmware ?: "Not yet seen device")
                    }
                )
            }

            // --- Debug section ---
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                ListItem(
                    modifier = Modifier.clickable { viewModel.toggleDebugDetailsEnabled() },
                    headlineContent = { Text("Enable Debug Details") },
                    supportingContent = {
                        Text("Version: ${viewModel.version}")
                    },
                    trailingContent = {
                        Switch(
                            checked = debugDetailsEnabled,
                            onCheckedChange = { viewModel.toggleDebugDetailsEnabled() }
                        )
                    }
                )
            }
            if (debugDetailsEnabled) {
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            coreNav.navigateTo(RingRoutes.RingSyncInspector)
                        },
                        headlineContent = { Text("Ring Sync Inspector") }
                    )
                }
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            coreNav.navigateTo(RingRoutes.RingDebug)
                        },
                        headlineContent = { Text("Ring Debug") }
                    )
                }
            }
            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun MusicControlDialog(
    currentMode: MusicControlMode,
    onModeSelected: (MusicControlMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    var targetMode by remember { mutableStateOf(currentMode) }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Headphones, contentDescription = null) },
        title = { Text("Music Control Mode") },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    onModeSelected(targetMode)
                }
            ) {
                Text("OK")
            }
        }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(MusicControlMode.Disabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = MusicControlMode.Disabled
                        }
                ) {
                    RadioButton(
                        selected = targetMode == MusicControlMode.Disabled,
                        onClick = {
                            targetMode = MusicControlMode.Disabled
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Disabled")
                }
            }
            item(MusicControlMode.SingleClick) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = MusicControlMode.SingleClick
                        }
                ) {
                    RadioButton(
                        selected = targetMode == MusicControlMode.SingleClick,
                        onClick = {
                            targetMode = MusicControlMode.SingleClick
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Single click")
                        Text(
                            "Single click: Play/Pause. Double click: Next track.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item(MusicControlMode.DoubleClick) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = MusicControlMode.DoubleClick
                        }
                ) {
                    RadioButton(
                        selected = targetMode == MusicControlMode.DoubleClick,
                        onClick = {
                            targetMode = MusicControlMode.DoubleClick
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Double click")
                        Text(
                            "Double click: Play/Pause. Triple click: Next track.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecondaryModeDialog(
    currentMode: SecondaryMode,
    onModeSelected: (SecondaryMode) -> Unit,
    onDismissRequest: () -> Unit,
    vermillionEnabled: Boolean,
    vermillionShown: Boolean,
) {
    var targetMode by remember { mutableStateOf(currentMode) }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Search, contentDescription = null) },
        title = { Text("Secondary Mode") },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    onModeSelected(targetMode)
                }
            ) {
                Text("OK")
            }
        }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(SecondaryMode.Disabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = SecondaryMode.Disabled
                        }
                ) {
                    RadioButton(
                        selected = targetMode == SecondaryMode.Disabled,
                        onClick = {
                            targetMode = SecondaryMode.Disabled
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Disabled")
                        Text(
                            "Double click & hold will be the same as a normal click.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item(SecondaryMode.Search) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = SecondaryMode.Search
                        }
                ) {
                    RadioButton(
                        selected = targetMode == SecondaryMode.Search,
                        onClick = {
                            targetMode = SecondaryMode.Search
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Search")
                        Text(
                            "Get web search results on a double click & hold.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (vermillionShown) {
                item(SecondaryMode.Vermillion) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = vermillionEnabled) {
                                targetMode = SecondaryMode.Vermillion
                            }
                    ) {
                        RadioButton(
                            selected = targetMode == SecondaryMode.Vermillion,
                            onClick = {
                                targetMode = SecondaryMode.Vermillion
                            },
                            enabled = vermillionEnabled
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Vermillion")
                            Text(
                                "Additionally send recorded audio to Vermillion on a double click & hold.",
                                fontSize = 12.sp,
                            )
                            if (!vermillionEnabled) {
                                Text(
                                    "Link Vermillion account first.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteShortcutDialog(
    currentShortcut: NoteShortcutType,
    onShortcutSelected: (NoteShortcutType) -> Unit,
    onDismissRequest: () -> Unit
) {
    var targetShortcut by remember { mutableStateOf(currentShortcut) }
    val options = buildList {
        add(NoteShortcutType.SendToMe)
        NoteProvider.entries.forEach { add(NoteShortcutType.SendToNoteProvider(it)) }
        ReminderProvider.entries.forEach { add(NoteShortcutType.SendToReminderProvider(it)) }
    }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Notification Shortcut") },
        buttons = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
            TextButton(onClick = { onShortcutSelected(targetShortcut) }) {
                Text("OK")
            }
        }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options.size) { index ->
                val option = options[index]
                val label = when (option) {
                    is NoteShortcutType.SendToMe -> "Email me"
                    is NoteShortcutType.SendToNoteProvider -> option.provider.title
                    is NoteShortcutType.SendToReminderProvider -> option.provider.title
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { targetShortcut = option }
                ) {
                    RadioButton(
                        selected = targetShortcut == option,
                        onClick = { targetShortcut = option }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
fun AuthorizedIntegrations(preferences: Preferences) {
    val gTasks = koinInject<GTasksIntegration>()
    val notion = koinInject<NotionIntegration>()
    val gTasksAuth by flow { emit(gTasks.isAuthorized()) }.collectAsState(false)
    val notionAuth by flow { emit(notion.isAuthorized()) }.collectAsState(false)
    val currentReminderProvider by preferences.reminderProvider.collectAsState()
    val currentNoteProvider by preferences.noteProvider.collectAsState()

    val platform = koinInject<Platform>()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (platform.isIOS) {
            IntegrationItem(
                title = "Built-in",
                hasReminder = false,
                hasNotes = true,
                selectedReminderProvider = false,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Builtin,
                onSelectReminderProvider = {},
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Builtin) }
            )
            IntegrationItem(
                title = "iOS",
                hasReminder = true,
                hasNotes = false,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.Native,
                selectedNoteProvider = false,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.Native) },
                onSelectNoteProvider = {}
            )
        } else {
            IntegrationItem(
                title = "Built-in",
                hasReminder = true,
                hasNotes = true,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.Native,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Builtin,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.Native) },
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Builtin) }
            )
        }
        if (gTasksAuth) {
            IntegrationItem(
                title = GTasksIntegration.DEFINITION.title,
                hasReminder = true,
                hasNotes = false,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.GoogleTasks,
                selectedNoteProvider = false,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.GoogleTasks) },
                onSelectNoteProvider = {}
            )
        }
        if (notionAuth) {
            IntegrationItem(
                title = NotionIntegration.DEFINITION.title,
                hasReminder = false,
                hasNotes = true,
                selectedReminderProvider = false,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Notion,
                onSelectReminderProvider = {},
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Notion) }
            )
        }
    }
}

@Composable
fun IntegrationItem(
    title: String,
    hasReminder: Boolean,
    hasNotes: Boolean,
    selectedReminderProvider: Boolean,
    selectedNoteProvider: Boolean,
    onSelectReminderProvider: () -> Unit,
    onSelectNoteProvider: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(title)
        Spacer(Modifier.weight(1f))
        RadioButton(
            enabled = hasReminder,
            selected = selectedReminderProvider,
            onClick = {
                onSelectReminderProvider()
            }
        )
        Text("Reminders")
        Spacer(Modifier.width(16.dp))
        RadioButton(
            enabled = hasNotes,
            selected = selectedNoteProvider,
            onClick = {
                onSelectNoteProvider()
            }
        )
        Text("Notes")
    }
}

@Composable
fun IndexDeviceListItem(
    headline: String,
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Image(
                imageResource(Res.drawable.ring_wireframe),
                contentDescription = null,
                modifier = Modifier.size(110.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(headline, style = MaterialTheme.typography.titleLarge)
                Row {
                    buttons()
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewDeviceListItem() {
    PreviewWrapper {
        IndexDeviceListItem(
            headline = "Pebble Index 0A1",
            buttons = {
                Button(
                    onClick = {}
                ) {
                    Text("Unpair")
                }
            }
        )
    }
}
