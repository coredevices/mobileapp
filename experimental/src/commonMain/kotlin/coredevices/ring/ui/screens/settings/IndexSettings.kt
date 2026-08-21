package coredevices.ring.ui.screens.settings

import BugReportButton
import CoreNav
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.settings
import coredevices.ring.agent.LlmMode
import coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.notes.TASKER_DEFINITION
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianIntegration
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.Preferences
import coredevices.ring.encryption.EncryptionSetupState
import coredevices.ring.encryption.KeyStorageStatus
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.ui.PreviewWrapper
import coredevices.ring.ui.components.QrCodeImage
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ring.ui.viewmodel.pickZipFile
import coredevices.ui.M3Dialog
import coredevices.ui.ModelDownloadDialog
import coredevices.ui.dismissKeyboardOnTapOutside
import coredevices.ui.SignInDialog
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.Platform
import coredevices.util.granted
import coredevices.util.isAndroid
import coredevices.util.isIOS
import coredevices.util.rememberUiContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.produceState
import com.cactus.isCactusSupported
import coredevices.util.CoreConfigHolder
import coredevices.util.models.ModelManager
import coredevices.util.transcription.PlatformSpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coredevices.util.models.CactusSTTMode
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import coreapp.util.generated.resources.Res as UtilRes

// openUri throws if nothing can handle the link (no browser, DPC policy);
// don't let a help link tap crash the settings screen.
internal fun UriHandler.openUrlSafely(url: String) {
    try {
        openUri(url)
    } catch (e: Exception) {
        Logger.w(e) { "Failed to open URL: $url" }
    }
}

@Composable
fun IndexSettings(coreNav: CoreNav) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val webhookViewModel = koinViewModel<IndexWebhookSettingsViewModel>()
    val llmMode by viewModel.llmMode.collectAsState()
    val localLlmSupported by viewModel.localLlmSupported.collectAsState()
    val debugDetailsEnabled by viewModel.debugDetailsEnabled.collectAsState()
    val showContactsDialog by viewModel.showContactsDialog.collectAsState()
    val showNoteShortcutDialog by viewModel.showNoteShortcutDialog.collectAsState()
    val autoDismissActionNotifications by viewModel.autoDismissActionNotifications.collectAsState()
    val platform = koinInject<Platform>()
    val coreConfigHolder = koinInject<CoreConfigHolder>()
    val coreConfig by coreConfigHolder.config.collectAsState()
    val modelManager = koinInject<ModelManager>()
    val platformSpeechRecognizer = koinInject<PlatformSpeechRecognizer>()
    val speechPermissionRequester = koinInject<PermissionRequester>()
    val speechUiContext = rememberUiContext()
    val speechScope = rememberCoroutineScope()
    val onDeviceSpeechSupported = remember { isCactusSupported() }
    val modelDownloadStatus by modelManager.modelDownloadStatus.collectAsState()
    val hasOfflineSpeechModels by produceState(false, modelDownloadStatus) {
        value = withContext(Dispatchers.Default) {
            modelManager.getRecommendedSTTModel().modelSlug in modelManager.getDownloadedSTTModelSlugs()
        }
    }
    val platformSttAvailable by produceState(false) {
        value = withContext(Dispatchers.Default) { platformSpeechRecognizer.isAvailable() }
    }
    val webhookUrl by webhookViewModel.webhookUrl.collectAsState()
    val configuredWebhookGesture by webhookViewModel.configuredGesture.collectAsState()
    val webhookIsLinked = !webhookUrl.isNullOrBlank()
    val currentRingFirmware by viewModel.currentRingFirmware.collectAsStateWithLifecycle()
    val currentRing by viewModel.currentRingName.collectAsStateWithLifecycle()
    val currentRingPaired = viewModel.ringPaired.collectAsStateWithLifecycle()
    val ringPaired by remember { derivedStateOf { currentRingPaired.value != null } }
    val accountUsername by viewModel.username.collectAsStateWithLifecycle()
    val noteShortcut by viewModel.noteShortcut.collectAsState()
    var showSignInDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showLlmModeSheet by remember { mutableStateOf(false) }
    val availableNoteProviders by viewModel.availableNoteProviders.collectAsState()
    val availableReminderProviders by viewModel.availableReminderProviders.collectAsState()

    val colors = IndexTheme.colors

    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }
    IndexWebhookSheetHost()
    if (showContactsDialog && platform.isAndroid) {
        SettingsBeeperContactsDialog(
            onDismissRequest = viewModel::closeContactsDialog
        )
    }
    if (showLlmModeSheet) {
        LlmModeSheet(
            current = llmMode,
            localSupported = localLlmSupported,
            onSelect = {
                viewModel.setLlmMode(it)
                showLlmModeSheet = false
            },
            onDismiss = { showLlmModeSheet = false }
        )
    }
    if (showNoteShortcutDialog) {
        val shortcut = viewModel.noteShortcut.collectAsState()
        NoteShortcutDialog(
            availableNoteProviders = availableNoteProviders,
            availableReminderProviders = availableReminderProviders,
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
    if (showBackupDialog) {
        BackupDialog(
            viewModel = viewModel,
            onDismiss = { showBackupDialog = false }
        )
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.primary,
                    navigationIconContentColor = colors.onSurface,
                    actionIconContentColor = colors.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = coreNav::goBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(coreapp.util.generated.resources.Res.string.back)
                        )
                    }
                },
                title = { Text(stringResource(UtilRes.string.settings)) },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "Settings",
                            "llmMode" to llmMode.name,
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
            // Getting Started guide + FAQ — Index 01 is a new kind of device,
            // so steer everyone to the guide. Opens in the system browser.
            item {
                val uriHandler = LocalUriHandler.current
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "New to Index 01?",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "It's a new kind of gadget! A quick read goes a long way.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Inverted colors so the button stands out on the container card
                            Button(
                                onClick = { uriHandler.openUrlSafely("https://pbl.zip/index-guide") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.onPrimaryContainer,
                                    contentColor = colors.primaryContainer,
                                ),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text("Getting Started Guide")
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = { uriHandler.openUrlSafely("https://pbl.zip/index-faq") },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.onPrimaryContainer,
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    colors.onPrimaryContainer.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text("FAQ")
                            }
                        }
                    }
                }
            }

            // Device card
            item {
                IndexDeviceListItem(
                    headline = when {
                        ringPaired && currentRing != null -> (currentRing ?: "")
                        ringPaired -> "Paired to Index 01"
                        else -> "No Ring Paired"
                    },
                    supporting = if (ringPaired) "Paired" else "Not paired",
                )
            }

            // --- Button Actions section ---
            item {
                SectionHeader(
                    title = "Ring Button",
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp, start = 16.dp, end = 16.dp)
                )
            }
            item { RingButtonSection(viewModel) }

            // --- Actions section ---
            item { IndexAgentActionsSection(coreNav, viewModel) }
            item {
                SettingsRow(
                    title = "Agent Model",
                    subtitle = llmModeRowSubtitle(llmMode),
                    onClick = { showLlmModeSheet = true },
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            item {
                SectionHeader(
                    modifier = Modifier.padding(top = 22.dp, bottom = 2.dp, start = 16.dp, end = 16.dp),
                    title = "Speech Recognition",
                )
            }
            item {
                SpeechSection(
                    mode = coreConfig.sttConfig.mode,
                    spokenLanguage = coreConfig.sttConfig.spokenLanguage,
                    onDeviceSupported = onDeviceSpeechSupported,
                    platformSttAvailable = platformSttAvailable,
                    hasOfflineModels = hasOfflineSpeechModels,
                    signedIn = accountUsername != null,
                    onSelectMode = { mode ->
                        coreConfigHolder.update(
                            coreConfig.copy(sttConfig = coreConfig.sttConfig.copy(mode = mode))
                        )
                        if (mode == CactusSTTMode.PlatformOnly) {
                            speechUiContext?.let { ctx ->
                                speechScope.launch {
                                    speechPermissionRequester.requestPermission(
                                        Permission.SpeechRecognizer,
                                        ctx,
                                    )
                                }
                            }
                        }
                    },
                    onSelectModeWithModel = { mode, modelSlug ->
                        coreConfigHolder.update(
                            coreConfig.copy(
                                sttConfig = coreConfig.sttConfig.copy(
                                    mode = mode,
                                    modelName = modelSlug,
                                )
                            )
                        )
                    },
                    onSelectLanguage = { language ->
                        coreConfigHolder.update(
                            coreConfig.copy(
                                sttConfig = coreConfig.sttConfig.copy(spokenLanguage = language)
                            )
                        )
                    },
                    onRequireSignIn = { showSignInDialog = true },
                )
            }
            item {
                SectionHeader(
                    modifier = Modifier.padding(top = 22.dp, bottom = 2.dp, start = 16.dp, end = 16.dp),
                    leadingContent = {
                        Text(
                            "Advanced",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primary
                        )
                    },
                    trailingContent = {}
                )
            }
            item {
                SettingsRow(
                    title = "Backup",
                    subtitle = "Sync, manage, or delete cloud backup",
                    onClick = { showBackupDialog = true },
                )
            }
            item {
                SettingsRow(
                    title = "MCP & Tool Settings",
                    subtitle = "Sandbox groups, models, per-tool config",
                    onClick = { coreNav.navigateTo(RingRoutes.McpSandboxGroups) },
                )
            }
            item {
                SettingsRow(
                    title = "Notification Shortcut",
                    subtitle = when (val shortcut = noteShortcut) {
                        is NoteShortcutType.SendToMe -> "Email me"
                        is NoteShortcutType.SendToNoteProvider -> shortcut.provider.title
                        is NoteShortcutType.SendToReminderProvider -> shortcut.provider.title
                    },
                    onClick = viewModel::showNoteShortcutDialog,
                )
            }
            item {
                SettingsRow(
                    title = "Auto-dismiss Notifications",
                    subtitle = "Dismiss recording action notifications after 5 minutes",
                    onClick = viewModel::toggleAutoDismissActionNotifications,
                ) {
                    Switch(
                        checked = autoDismissActionNotifications,
                        onCheckedChange = { viewModel.toggleAutoDismissActionNotifications() }
                    )
                }
            }
            item {
                SettingsRow(
                    title = "Webhook",
                    subtitle = if (webhookIsLinked) "Configured, tap to modify" else "Not Linked",
                    onClick = {
                        configuredWebhookGesture
                            ?.let { webhookViewModel.openDialog(it) }
                            ?: webhookViewModel.openDialog()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Ring Firmware Version",
                    subtitle = currentRingFirmware ?: "Not yet seen device",
                )
            }

            // --- Debug section ---
            item {
                HorizontalDivider(
                    color = colors.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                SettingsRow(
                    title = "Enable Debug Details",
                    subtitle = "Version: ${viewModel.version}",
                    onClick = viewModel::toggleDebugDetailsEnabled,
                ) {
                    Switch(
                        checked = debugDetailsEnabled,
                        onCheckedChange = { viewModel.toggleDebugDetailsEnabled() }
                    )
                }
            }
            if (debugDetailsEnabled) {
                // Panic Ring button commented out for prod — crashes the app (MOB-7937).
                // item {
                //     ListItem(
                //         modifier = Modifier.clickable(enabled = currentRingFirmware != null && !panicPending) {
                //             viewModel.panicRing()
                //         },
                //         headlineContent = { Text("Panic Ring", color = Color.Red) }
                //     )
                // }
                item {
                    SettingsRow(
                        title = "Restart Pre-emptive Transfer",
                        subtitle = "Available on iOS only".takeIf { platform.isAndroid },
                        enabled = !platform.isAndroid,
                        onClick = viewModel::restartPreemptiveTransfer,
                    )
                }
                item {
                    SettingsRow(
                        title = "Ring Sync Inspector",
                        onClick = { coreNav.navigateTo(RingRoutes.RingSyncInspector) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Advanced / Debug list row. M3 [ListItem] paints its own `colorScheme.surface`, which would
 *  punch white bars through the Index page background. */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(enabled = enabled, onClick = onClick)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.onSurface)
            if (subtitle != null) {
                Text(subtitle, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

fun LlmMode.displayName(): String = when (this) {
    LlmMode.RemoteOnly -> "Cloud LLM"
    LlmMode.RemoteFirst -> "Cloud LLM (with Local fallback)"
    LlmMode.LocalOnly -> "Local LLM"
}

internal fun LlmMode.modeDetail(): String = when (this) {
    LlmMode.RemoteOnly -> "Best performance, requires connection"
    LlmMode.RemoteFirst -> "Uses the Local LLM when the cloud can't be reached"
    LlmMode.LocalOnly -> "Runs on this phone. Experimental, less accurate than Cloud LLM. Does not support all actions."
}

/** Offered most-cloud first; the enum's own order is persistence ids, not a sensible reading order. */
internal val llmModeOptions = listOf(LlmMode.RemoteOnly, LlmMode.RemoteFirst, LlmMode.LocalOnly)

/** The Local LLM cannot drive a sandbox group's model, so local modes need the default
 *  group left on Index Agent. */
internal fun llmModeSelectable(mode: LlmMode, localSupported: Boolean): Boolean =
    localSupported || !mode.usesLocalCactus()

internal fun llmModeRowSubtitle(mode: LlmMode): String =
    if (mode.usesLocalCactus()) "${mode.displayName()} · ${mode.modeDetail()}"
    else mode.displayName()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmModeSheet(
    current: LlmMode,
    localSupported: Boolean,
    onSelect: (LlmMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheetSurface) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Text(
                    "Agent Model",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    "Select which large language model (LLM) to use",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            llmModeOptions.forEach { mode ->
                val selected = mode == current
                val selectable = llmModeSelectable(mode, localSupported)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .selectedSheetRowBackground(selected)
                        .clickable(enabled = selectable) { onSelect(mode) }
                        .alpha(if (selectable) 1f else 0.38f)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        when (mode) {
                            LlmMode.RemoteOnly -> Icons.Default.Cloud
                            LlmMode.RemoteFirst -> Icons.Default.CloudSync
                            LlmMode.LocalOnly -> Icons.Default.Smartphone
                        },
                        contentDescription = null,
                        tint = if (selected) colors.primary else colors.outline,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mode.displayName(), fontSize = 15.sp, color = colors.onSurface)
                        Text(
                            mode.modeDetail(),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
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
            if (!localSupported) {
                Text(
                    "The Local LLM does not support MCP sandboxes. Set the default MCP " +
                        "sandbox group's model to \"Index Agent\" to use it.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                )
            }
        }
    }
}

@Composable
fun NoteShortcutDialog(
    availableNoteProviders: List<NoteProvider>,
    availableReminderProviders: List<ReminderProvider>,
    currentShortcut: NoteShortcutType,
    onShortcutSelected: (NoteShortcutType) -> Unit,
    onDismissRequest: () -> Unit
) {
    var targetShortcut by remember { mutableStateOf(currentShortcut) }
    val options = remember {
        buildList {
            add(NoteShortcutType.SendToMe)
            availableNoteProviders.forEach {
                add(NoteShortcutType.SendToNoteProvider(it))
            }
            availableReminderProviders.forEach {
                add(NoteShortcutType.SendToReminderProvider(it))
            }
        }
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
fun BackupDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val userId by viewModel.userId.collectAsState()
    val syncing by viewModel.syncingFeedHistory.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val backupDownloading by viewModel.backupDownloading.collectAsState()
    val backupDownloadStatus by viewModel.backupDownloadStatus.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val backupCount by viewModel.backupCount.collectAsState()
    val backupLoading by viewModel.backupLoading.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val backupEnabled by viewModel.backupEnabled.collectAsState()
    val keyStorageStatus by viewModel.keyStorageStatus.collectAsState()
    val encryptionKeyStatus by viewModel.encryptionKeyStatus.collectAsState()
    val encryptionKeyLoading by viewModel.encryptionKeyLoading.collectAsState()
    val debugDetailsEnabled by viewModel.debugDetailsEnabled.collectAsState()
    val uiContext = rememberUiContext()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteInput by remember { mutableStateOf("") }
    var showDeleteLocalConfirm by remember { mutableStateOf(false) }
    var deleteLocalInput by remember { mutableStateOf("") }
    var showOverwriteKeyConfirm by remember { mutableStateOf(false) }
    var showEnterKeyDialog by remember { mutableStateOf(false) }
    var enterKeyInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadBackupCount()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearBackupStatus()
            viewModel.clearEncryptionKeyStatus()
        }
    }

    if (showOverwriteKeyConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteKeyConfirm = false },
            title = { Text("Overwrite Encryption Key?") },
            text = {
                Text(buildAnnotatedString {
                    append("An encryption key already exists. Generating a new one will overwrite it.\n\n")
                    withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                        append("Previously encrypted backups (cloud sync or local) will be permanently lost.")
                    }
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteKeyConfirm = false
                    uiContext?.let { viewModel.generateAndStoreKey(it) }
                }) {
                    Text("Overwrite", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteKeyConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showEnterKeyDialog) {
        AlertDialog(
            onDismissRequest = { showEnterKeyDialog = false; enterKeyInput = "" },
            modifier = Modifier.dismissKeyboardOnTapOutside(),
            title = { Text("Enter Encryption Key") },
            text = {
                Column {
                    val focusManager = LocalFocusManager.current
                    Text("Type or paste the encryption key from another device.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = enterKeyInput,
                        onValueChange = { enterKeyInput = it },
                        singleLine = true,
                        label = { Text("Encryption key") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = enterKeyInput.isNotBlank() && !encryptionKeyLoading,
                    onClick = {
                        viewModel.importKeyFromText(enterKeyInput)
                        showEnterKeyDialog = false
                        enterKeyInput = ""
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnterKeyDialog = false; enterKeyInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
    EncryptionKeyResultDialogs(viewModel)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; deleteInput = "" },
            modifier = Modifier.dismissKeyboardOnTapOutside(),
            title = { Text("Delete Backup") },
            text = {
                Column {
                    val focusManager = LocalFocusManager.current
                    Text(
                        "This will permanently delete all your cloud backup data. This cannot be undone.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Type \"delete\" to confirm:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteInput,
                        onValueChange = { deleteInput = it },
                        singleLine = true,
                        placeholder = { Text("delete") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteInput.equals("delete", ignoreCase = true) && !backupLoading,
                    onClick = {
                        viewModel.deleteBackup()
                        showDeleteConfirm = false
                        deleteInput = ""
                    }
                ) {
                    Text("Delete", color = if (deleteInput.equals("delete", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; deleteInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showDeleteLocalConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteLocalConfirm = false; deleteLocalInput = "" },
            modifier = Modifier.dismissKeyboardOnTapOutside(),
            title = { Text("Delete Local Feed") },
            text = {
                Column {
                    val focusManager = LocalFocusManager.current
                    Text(
                        "This will delete all recordings from the local feed on this device. Cloud backup is not affected. You can restore from cloud with Sync or from a backup zip with Import.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Type \"delete\" to confirm:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteLocalInput,
                        onValueChange = { deleteLocalInput = it },
                        singleLine = true,
                        placeholder = { Text("delete") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteLocalInput.equals("delete", ignoreCase = true) && !backupLoading,
                    onClick = {
                        viewModel.deleteLocalFeed()
                        showDeleteLocalConfirm = false
                        deleteLocalInput = ""
                    }
                ) {
                    Text("Delete", color = if (deleteLocalInput.equals("delete", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLocalConfirm = false; deleteLocalInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            // Backup count
            ListItem(
                headlineContent = { Text("Items in backup") },
                supportingContent = {
                    if (backupLoading && backupCount == null) {
                        Text("Loading...")
                    } else {
                        Column {
                            Text("${backupCount ?: "—"} recordings across all devices")
                            if (userId != null) {
                                Text("ID: $userId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                trailingContent = {
                    if (backupLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Backup enabled toggle
            ListItem(
                modifier = Modifier.clickable { viewModel.setBackupEnabled(!backupEnabled) },
                headlineContent = { Text("Backup enabled") },
                supportingContent = { Text("Automatically sync recordings to cloud") },
                trailingContent = {
                    Switch(
                        checked = backupEnabled,
                        onCheckedChange = { viewModel.setBackupEnabled(it) }
                    )
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Download full backup
            ListItem(
                modifier = Modifier.clickable(enabled = !backupDownloading && !syncing && uiContext != null) {
                    uiContext?.let { viewModel.downloadFullBackup(it) }
                },
                headlineContent = { Text("Download Full Backup") },
                supportingContent = {
                    Text(backupDownloadStatus ?: "Download all recordings and data as a zip file")
                },
                trailingContent = {
                    if (backupDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )

            // Import backup
            if (debugDetailsEnabled) {
                ListItem(
                    modifier = Modifier.clickable(enabled = !importing && !backupDownloading && uiContext != null) {
                        scope.launch {
                            val path = uiContext?.let { pickZipFile(it) }
                            if (path != null) {
                                viewModel.importBackup(path)
                            }
                        }
                    },
                    headlineContent = { Text("Import Backup") },
                    supportingContent = {
                        Text(importStatus ?: "Restore recordings from a backup zip file")
                    },
                    trailingContent = {
                        if (importing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }


            // Delete backup
            ListItem(
                modifier = Modifier.clickable(enabled = !backupLoading) {
                    showDeleteConfirm = true
                },
                headlineContent = {
                    Text("Delete cloud backup", color = MaterialTheme.colorScheme.error)
                },
                supportingContent = {
                    Text(backupStatus ?: "Permanently delete all cloud data")
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Encryption section ---
            Text(
                "Encryption",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Generate encryption key. A key already exists (here or on
            // another device) unless status is NoKeyStored — regenerating it
            // orphans prior backups, so warn and gate behind debug details.
            val keyExists = keyStorageStatus != KeyStorageStatus.NoKeyStored
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null && (!keyExists || debugDetailsEnabled)) {
                    if (keyExists) {
                        showOverwriteKeyConfirm = true
                    } else {
                        uiContext?.let { viewModel.generateAndStoreKey(it) }
                    }
                },
                headlineContent = { Text("Generate Encryption Key") },
                supportingContent = {
                    Text(
                        encryptionKeyStatus?.let { AnnotatedString(it) } ?: when (keyStorageStatus) {
                            KeyStorageStatus.KeyLocallyAvailable -> buildAnnotatedString {
                                append("Key exists")
                                if (debugDetailsEnabled) withStyle(SpanStyle(color = Color.Red)) {
                                    append(" — tap to regenerate")
                                }
                            }
                            KeyStorageStatus.KeyGeneratedBefore -> buildAnnotatedString {
                                append("A key was generated before but isn't on this device — restore it instead")
                                if (debugDetailsEnabled) withStyle(SpanStyle(color = Color.Red)) {
                                    append(", or tap to regenerate")
                                }
                            }
                            KeyStorageStatus.NoKeyStored -> AnnotatedString("Create encryption key")
                        }
                    )
                },
                trailingContent = {
                    if (encryptionKeyLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )

            // Read key from password manager
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null) {
                    uiContext?.let { viewModel.readKeyFromCloudKeychain(it) }
                },
                headlineContent = { Text("Restore Key from Password Manager") },
                supportingContent = {
                    Text("Read key from Google Password Manager or iCloud Keychain")
                }
            )

            // Import key from a QR code photo (the backup saved at generation time)
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null) {
                    uiContext?.let { viewModel.importKeyFromQrPhoto(it) }
                },
                headlineContent = { Text("Import Key from QR Code") },
                supportingContent = {
                    Text("Pick your key's QR code from your photos")
                }
            )

            // Enter the key manually as text (e.g. copied from another device)
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading) {
                    enterKeyInput = ""
                    showEnterKeyDialog = true
                },
                headlineContent = { Text("Enter Key Manually") },
                supportingContent = {
                    Text("Type or paste your encryption key as text")
                }
            )

            // Enable/disable encryption
            val useEncryption by viewModel.useEncryption.collectAsState()
            val encryptionStatus by viewModel.encryptionStatus.collectAsState()
            val enablingEncryption by viewModel.enablingEncryption.collectAsState()

            if (keyStorageStatus == KeyStorageStatus.KeyLocallyAvailable) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    modifier = Modifier.clickable(enabled = !enablingEncryption && (useEncryption || uiContext != null)) {
                        if (!useEncryption) {
                            uiContext?.let { viewModel.requestEnableEncryption(it) }
                        } else {
                            viewModel.disableEncryption()
                        }
                    },
                    headlineContent = {
                        Text(if (useEncryption) "Disable Encryption" else "Enable Encryption")
                    },
                    supportingContent = {
                        Text(
                            encryptionStatus
                                ?: if (useEncryption) "All new uploads are encrypted"
                                else "Encrypt all recordings before uploading to cloud"
                        )
                    },
                    trailingContent = {
                        if (enablingEncryption) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Danger zone ---
            if (debugDetailsEnabled) {
                Text(
                    "Danger Zone",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    modifier = Modifier.clickable(enabled = !backupLoading) {
                        showDeleteLocalConfirm = true
                    },
                    headlineContent = {
                        Text("Delete local feed", color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = {
                        Text("Remove all recordings from this device (cloud unaffected)")
                    }
                )
            }
        }
    }
}

/**
 * The "key not backed up" warning and generated-key QR/backup sheet,
 * driven by [SettingsViewModel] so any host of the key flow can render them.
 */
@Composable
fun EncryptionKeyResultDialogs(viewModel: SettingsViewModel) {
    val showKeyNotBackedUpDialog by viewModel.showKeyNotBackedUpDialog.collectAsState()
    val generatedKey by viewModel.generatedKey.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    if (showKeyNotBackedUpDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissKeyNotBackedUpDialog() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("Encryption key not backed up") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("You're attempting to enable backup encryption without a confirmed keychain backup of your key, ")
                        append("please ensure you've saved your key either via QR code or copied it to your clipboard and saved it elsewhere.\n\n")
                        withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                            append("If you lose this key, you will not be able to sync or restore anything from backups and will have to start fresh.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmEnableEncryption() }) {
                    Text("I confirm, my key is saved elsewhere")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissKeyNotBackedUpDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (generatedKey != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearGeneratedKey() },
            title = { Text("Encryption Key Backup") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Save this key securely. You will need it to decrypt your backups.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeImage(
                            data = generatedKey!!,
                            size = 220.dp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(generatedKey!!))
                }) {
                    Text("Copy Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearGeneratedKey() }) {
                    Text("Done")
                }
            }
        )
    }
}

/**
 * Guided "turn on encryption" dialog, driven by
 * [SettingsViewModel.encryptionSetup] through generate or restore before
 * encryption is enabled, so it's reusable wherever the switch lives.
 */
@Composable
fun EncryptionSetupDialog(viewModel: SettingsViewModel) {
    val current by viewModel.encryptionSetup.collectAsState()
    if (current is EncryptionSetupState.Hidden) return

    val uiContext = rememberUiContext()
    val clipboardManager = LocalClipboardManager.current
    var pasted by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { viewModel.cancelEncryptionSetup() },
        modifier = Modifier.dismissKeyboardOnTapOutside(),
        title = { Text("Set up backup encryption") },
        text = {
            when (val s = current) {
                EncryptionSetupState.Hidden -> {}
                EncryptionSetupState.PromptGenerate -> Text(
                    "Generate an encryption key so only you can read your backups. " +
                        "You'll save a copy so you can restore on another device."
                )

                EncryptionSetupState.Generating -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Generating your key and saving it to your password manager…")
                }

                is EncryptionSetupState.ShowKey -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        buildString {
                            append("Saved to your password manager. ")
                            if (s.qrSavedToPhotos) {
                                append("A QR code of your key was also saved to your photos. ")
                            }
                            append(
                                "Save this key somewhere safe too — you'll need it to " +
                                    "restore your backups and it can't be recovered."
                            )
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        QrCodeImage(data = s.keyBase64, size = 220.dp)
                    }
                }

                EncryptionSetupState.Restoring -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Looking for your key in your password manager…")
                }

                is EncryptionSetupState.PasteKey -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val focusManager = LocalFocusManager.current
                    Text(
                        "Your key was generated on another device and isn't in this " +
                            "password manager. Paste your backup key, or import the " +
                            "QR code saved in your photos."
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = true,
                        label = { Text("Encryption key") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = uiContext != null,
                        onClick = { uiContext?.let { viewModel.restoreFromQrPhoto(it) } }
                    ) { Text("Import QR code from photos") }
                    s.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                is EncryptionSetupState.Failed -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            when (val s = current) {
                EncryptionSetupState.PromptGenerate -> TextButton(
                    enabled = uiContext != null,
                    onClick = { uiContext?.let { viewModel.generateKeyForSetup(it) } }
                ) { Text("Generate Key") }

                is EncryptionSetupState.ShowKey -> TextButton(
                    onClick = { viewModel.completeEncryptionSetup() }
                ) { Text("Done") }

                is EncryptionSetupState.PasteKey -> TextButton(
                    enabled = pasted.isNotBlank(),
                    onClick = { viewModel.restoreFromPastedKey(pasted) }
                ) { Text("Restore") }

                is EncryptionSetupState.Failed -> TextButton(
                    enabled = uiContext != null,
                    onClick = { uiContext?.let { viewModel.generateKeyForSetup(it) } }
                ) { Text("Retry") }

                else -> {}
            }
        },
        dismissButton = {
            when (val s = current) {
                is EncryptionSetupState.ShowKey -> TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(s.keyBase64)) }
                ) { Text("Copy Key") }

                EncryptionSetupState.Generating,
                EncryptionSetupState.Restoring,
                EncryptionSetupState.Hidden -> {}

                else -> TextButton(
                    onClick = { viewModel.cancelEncryptionSetup() }
                ) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun AuthorizedIntegrations(preferences: Preferences) {
    val gTasks = koinInject<GTasksIntegration>()
    val notion = koinInject<NotionIntegration>()
    val noteIntegrationFactory = koinInject<NoteIntegrationFactory>()
    val obsidian = koinInject<ObsidianIntegration>()
    val obsidianAuth by flow { emit(obsidian.isAuthorized()) }.collectAsState(false)
    val gTasksAuth by flow { emit(gTasks.isAuthorized()) }.collectAsState(false)
    val notionAuth by flow { emit(notion.isAuthorized()) }.collectAsState(false)
    val taskerAuth by flow {
        emit(noteIntegrationFactory.createNoteClient(NoteProvider.Tasker).isAuthorized())
    }.collectAsState(false)
    val currentReminderProvider by preferences.reminderProvider.collectAsState()
    val currentNoteProvider by preferences.noteProvider.collectAsState()

    val platform = koinInject<Platform>()

    // Phone Calendar is an opt-in integration (Add integration → Phone Calendar). Its dot
    // reflects whether calendar permission is granted; tapping re-requests a missing permission.
    val permissionRequester = koinInject<PermissionRequester>()
    val uiContext = rememberUiContext()
    val scope = rememberCoroutineScope()
    val calendarGranted by permissionRequester.granted(Permission.Calendar).collectAsState(false)
    val phoneCalendarEnabled by preferences.phoneCalendarEnabled.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        IntegrationItem(
            title = "Built-in",
            hasReminder = true,
            hasNotes = true,
            selectedReminderProvider = currentReminderProvider == ReminderProvider.BuiltIn,
            selectedNoteProvider = currentNoteProvider == NoteProvider.Builtin,
            onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.BuiltIn) },
            onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Builtin) },
        )
        if (phoneCalendarEnabled) {
            var showPhoneCalendarConfig by remember { mutableStateOf(false) }
            if (showPhoneCalendarConfig) {
                PhoneCalendarConfigDialog(
                    preferences = preferences,
                    onDismiss = { showPhoneCalendarConfig = false }
                )
            }
            IntegrationItem(
                title = PHONE_CALENDAR_TITLE,
                hasReminder = false,
                hasNotes = false,
                selectedReminderProvider = false,
                selectedNoteProvider = false,
                onSelectReminderProvider = {},
                onSelectNoteProvider = {},
                onConfigure = { showPhoneCalendarConfig = true },
                hasCalendar = true,
                calendarGranted = calendarGranted,
                onToggleCalendar = {
                    if (!calendarGranted) {
                        val ctx = uiContext ?: return@IntegrationItem
                        scope.launch { permissionRequester.requestPermission(Permission.Calendar, ctx) }
                    }
                },
            )
        }
        if (platform.isIOS) {
            IntegrationItem(
                title = "iOS Reminders",
                hasReminder = true,
                hasNotes = false,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.IOSReminders,
                selectedNoteProvider = false,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.IOSReminders) },
                onSelectNoteProvider = {}
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
            var showNotionPageDialog by remember { mutableStateOf(false) }
            if (showNotionPageDialog) {
                NotionPageDialog(onDismiss = { showNotionPageDialog = false })
            }
            IntegrationItem(
                title = NotionIntegration.DEFINITION.title,
                hasReminder = false,
                hasNotes = true,
                selectedReminderProvider = false,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Notion,
                onSelectReminderProvider = {},
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Notion) },
                onConfigure = { showNotionPageDialog = true }
            )
        }
        if (obsidianAuth) {
            var showObsidianConfig by remember { mutableStateOf(false) }
            if (showObsidianConfig) {
                ObsidianDialog(onDismiss = { showObsidianConfig = false })
            }
            IntegrationItem(
                title = ObsidianIntegration.DEFINITION.title,
                hasReminder = false,
                hasNotes = true,
                selectedReminderProvider = false,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Obsidian,
                onSelectReminderProvider = {},
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Obsidian) },
                onConfigure = { showObsidianConfig = true },
            )
        }
        if (platform.isAndroid && taskerAuth) {
            IntegrationItem(
                title = TASKER_DEFINITION.title,
                hasReminder = true,
                hasNotes = true,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.Tasker,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Tasker,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.Tasker) },
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Tasker) }
            )
        }
    }
}

/** Manage dialog for the opt-in Phone Calendar integration: disconnecting hides the row and
 *  disables the agent's calendar tool. (The system permission itself stays granted.) */
@Composable
private fun PhoneCalendarConfigDialog(preferences: Preferences, onDismiss: () -> Unit) {
    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text(PHONE_CALENDAR_TITLE) },
        buttons = {
            TextButton(
                onClick = {
                    preferences.setPhoneCalendarEnabled(false)
                    onDismiss()
                }
            ) { Text("Disconnect") }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    ) {
        Text("Index can add events to your phone's calendar when you explicitly ask for a calendar event.")
    }
}

/**
 * Lets the user pick which Notion page to-do block is placed
 */
@Composable
fun NotionPageDialog(onDismiss: () -> Unit) {
    val notion = koinInject<NotionIntegration>()
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<NotionIntegration.NotionPage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val loaded = notion.listPages()
            selectedId = notion.selectedPageId() ?: loaded.firstOrNull()?.id
            pages = loaded
        } catch (e: Throwable) {
            error = e.message ?: "Failed to load pages"
        }
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        // The page list can be long; scroll it so the buttons stay reachable.
        scrollableContent = true,
        title = { Text("Notion Page") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                enabled = selectedId != null && !saving,
                onClick = {
                    val pageId = selectedId ?: return@TextButton
                    saving = true
                    scope.launch {
                        notion.selectPage(pageId)
                        onDismiss()
                    }
                }
            ) { Text("OK") }
        }
    ) {
        Column {
            Text(
                "Choose the page to place your notes' Reminders list in.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            val loadedPages = pages
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                loadedPages == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                loadedPages.isEmpty() -> Text(
                    "No pages found. Give Index access to a page in Notion, then try again."
                )
                // Plain Column (not LazyColumn): the dialog content scrolls via
                // scrollableContent, and nested lazy lists inside verticalScroll crash.
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    loadedPages.forEach { page ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedId = page.id }
                        ) {
                            RadioButton(
                                selected = selectedId == page.id,
                                onClick = { selectedId = page.id }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(page.title)
                        }
                    }
                }
            }
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
    onConfigure: (() -> Unit)? = null,
    hasCalendar: Boolean = false,
    calendarGranted: Boolean = false,
    onToggleCalendar: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(title)
        if (onConfigure != null) {
            IconButton(onClick = onConfigure, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configure $title",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        OutputTypeOption(
            label = "Reminders",
            visible = hasReminder,
            selected = selectedReminderProvider,
            onSelect = onSelectReminderProvider
        )
        Spacer(Modifier.width(16.dp))
        OutputTypeOption(
            label = "Notes",
            visible = hasNotes,
            selected = selectedNoteProvider,
            onSelect = onSelectNoteProvider
        )
        Spacer(Modifier.width(16.dp))
        // Calendar (Built-in only): the dot reflects calendar permission; tapping requests it.
        OutputTypeOption(
            label = "Calendar",
            visible = hasCalendar,
            selected = calendarGranted,
            onSelect = onToggleCalendar
        )
    }
}

/**
 * A single "Reminders" / "Notes" radio option for a provider row. Output types a provider can't
 * handle aren't shown at all (rather than greyed out), but the slot keeps its footprint so the
 * columns stay aligned across provider rows.
 */
@Composable
private fun OutputTypeOption(
    label: String,
    visible: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (visible) Modifier else Modifier.alpha(0f)
    ) {
        RadioButton(
            enabled = visible,
            selected = selected,
            onClick = onSelect
        )
        Text(label)
    }
}

@Composable
fun IndexDeviceListItem(
    headline: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    val colors = IndexTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .border(2.dp, colors.outlineVariant, CircleShape)
        ) {
            Icon(
                Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Text(headline, fontSize = 16.sp, color = colors.onSurface)
            Text(supporting, fontSize = 14.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
fun PreviewDeviceListItem() {
    PreviewWrapper {
        IndexDeviceListItem(headline = "Pebble Index 0A1", supporting = "Paired")
    }
}
