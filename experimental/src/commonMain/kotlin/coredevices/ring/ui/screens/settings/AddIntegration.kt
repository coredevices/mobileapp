package coredevices.ring.ui.screens.settings

import BugReportButton
import CoreNav
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import co.touchlab.kermit.Logger
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_integration_add
import coreapp.ring.generated.resources.settings_integration_calendar
import coreapp.ring.generated.resources.settings_integration_calendar_blocked
import coreapp.ring.generated.resources.settings_integration_calendar_connected
import coreapp.ring.generated.resources.settings_integration_calendar_description
import coreapp.ring.generated.resources.settings_integration_calendar_not_granted
import coreapp.ring.generated.resources.settings_integration_error
import coreapp.ring.generated.resources.settings_integration_gtasks_description
import coreapp.ring.generated.resources.settings_integration_notes
import coreapp.ring.generated.resources.settings_integration_notion_description
import coreapp.ring.generated.resources.settings_integration_notion_no_page
import coreapp.ring.generated.resources.settings_integration_obsidian_change
import coreapp.ring.generated.resources.settings_integration_obsidian_default_vault
import coreapp.ring.generated.resources.settings_integration_obsidian_description
import coreapp.ring.generated.resources.settings_integration_obsidian_existing_note
import coreapp.ring.generated.resources.settings_integration_obsidian_existing_note_description
import coreapp.ring.generated.resources.settings_integration_obsidian_extra_tag
import coreapp.ring.generated.resources.settings_integration_obsidian_extra_tag_help
import coreapp.ring.generated.resources.settings_integration_obsidian_extra_tag_placeholder
import coreapp.ring.generated.resources.settings_integration_obsidian_how_saved
import coreapp.ring.generated.resources.settings_integration_obsidian_main_note
import coreapp.ring.generated.resources.settings_integration_obsidian_main_note_description
import coreapp.ring.generated.resources.settings_integration_obsidian_main_note_help
import coreapp.ring.generated.resources.settings_integration_obsidian_no_folder
import coreapp.ring.generated.resources.settings_integration_obsidian_note_path
import coreapp.ring.generated.resources.settings_integration_obsidian_note_path_help
import coreapp.ring.generated.resources.settings_integration_obsidian_note_path_placeholder
import coreapp.ring.generated.resources.settings_integration_obsidian_pick_folder
import coreapp.ring.generated.resources.settings_integration_obsidian_picking
import coreapp.ring.generated.resources.settings_integration_obsidian_separate_file
import coreapp.ring.generated.resources.settings_integration_obsidian_separate_file_description
import coreapp.ring.generated.resources.settings_integration_obsidian_subfolder
import coreapp.ring.generated.resources.settings_integration_obsidian_subfolder_help
import coreapp.ring.generated.resources.settings_integration_obsidian_vault_folder
import coreapp.ring.generated.resources.settings_integration_phone_calendar
import coreapp.ring.generated.resources.settings_integration_reminders
import coreapp.ring.generated.resources.settings_integration_sign_in_cancelled
import coreapp.ring.generated.resources.settings_integration_sign_in_error
import coreapp.ring.generated.resources.settings_integration_sign_in_error_multiline
import coreapp.ring.generated.resources.settings_integration_signed_in
import coreapp.ring.generated.resources.settings_integration_tasker_connected
import coreapp.ring.generated.resources.settings_integration_tasker_description
import coreapp.ring.generated.resources.settings_integration_tasker_not_installed
import coreapp.ring.generated.resources.settings_integration_tasker_receive
import coreapp.ring.generated.resources.settings_integration_tasker_step1
import coreapp.ring.generated.resources.settings_integration_tasker_step2
import coreapp.ring.generated.resources.settings_integration_tasker_step3
import coreapp.ring.generated.resources.settings_integration_unknown_error
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.connect
import coreapp.util.generated.resources.done
import coreapp.util.generated.resources.save
import coreapp.util.generated.resources.sign_in
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianMode
import coredevices.ring.agent.integrations.obsidian.ObsidianPreferences
import coredevices.ring.agent.integrations.providers.BuiltInIntegrationProviders
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.supportsNotes
import coredevices.ring.agent.integrations.providers.supportsReminders
import coredevices.ring.database.Preferences
import coredevices.ui.M3Dialog
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.PermissionResult
import coredevices.util.Platform
import coredevices.util.isAndroid
import coredevices.util.rememberUiContext
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun AddIntegration(coreNav: CoreNav) {
    var dialog by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    val platform = koinInject<Platform>()
    dialog?.invoke()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = coreNav::goBack
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(UtilRes.string.back)
                        )
                    }
                },
                title = {
                    Text(stringResource(Res.string.settings_integration_add))
                },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "AddIntegration",
                        )
                    )
                }
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Item(BuiltInIntegrationProviders.GoogleTasks) {
                    dialog = {
                        GTasksDialog(
                            onDismiss = { dialog = null }
                        )
                    }
                }
            }
            item {
                Item(BuiltInIntegrationProviders.Notion) {
                    dialog = {
                        NotionDialog(
                            onDismiss = { dialog = null }
                        )
                    }
                }
            }
            item {
                Item(BuiltInIntegrationProviders.Obsidian) {
                    dialog = {
                        ObsidianDialog(
                            onDismiss = { dialog = null }
                        )
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.settings_integration_phone_calendar)) },
                    supportingContent = { Text(stringResource(Res.string.settings_integration_calendar)) },
                    modifier = Modifier.clickable {
                        dialog = {
                            PhoneCalendarDialog(
                                onDismiss = { dialog = null }
                            )
                        }
                    }
                )
            }
            if (platform.isAndroid) {
                item {
                    Item(BuiltInIntegrationProviders.Tasker) {
                        dialog = {
                            TaskerDialog(
                                onDismiss = { dialog = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Item(provider: IntegrationProvider, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(provider.title) },
        supportingContent = { Text(
            buildList {
                if (provider.supportsReminders) {
                    add(stringResource(Res.string.settings_integration_reminders))
                }
                if (provider.supportsNotes) {
                    add(stringResource(Res.string.settings_integration_notes))
                }
            }.joinToString()
        ) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private sealed class SignInState {
    data object Idle : SignInState()
    data object SigningIn : SignInState()
    data object Success : SignInState()
    data class Error(val message: String) : SignInState()
}

/**
 * Connects the opt-in Phone Calendar integration: "connecting" means granting calendar
 * permission and flipping [Preferences.phoneCalendarEnabled]. The agent's calendar tool stays
 * hidden until both are true.
 */
@Composable
fun PhoneCalendarDialog(
    onDismiss: () -> Unit,
    onConnected: () -> Unit = onDismiss,
) {
    val preferences = koinInject<Preferences>()
    val libPebble = koinInject<LibPebble>()
    val permissionRequester = koinInject<PermissionRequester>()
    val uiContext = rememberUiContext()
    var state by remember { mutableStateOf<SignInState>(SignInState.Idle) }
    val scope = rememberCoroutineScope()

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_integration_phone_calendar)) },
        buttons = {
            TextButton(onClick = { if (state is SignInState.Success) onConnected() else onDismiss() }) {
                Text(stringResource(if (state is SignInState.Success) UtilRes.string.done else UtilRes.string.cancel))
            }
            if (state !is SignInState.Success) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = state !is SignInState.SigningIn,
                    onClick = {
                        val ctx = uiContext ?: return@TextButton
                        state = SignInState.SigningIn
                        scope.launch {
                            state = when (permissionRequester.requestPermission(Permission.Calendar, ctx)) {
                                PermissionResult.Granted -> {
                                    preferences.setPhoneCalendarEnabled(true)
                                    // Sync calendars into the db now so the target picker isn't empty.
                                    libPebble.doStuffAfterPermissionsGranted()
                                    SignInState.Success
                                }
                                PermissionResult.RejectedForever -> {
                                    // The system won't show the prompt again; send the user to
                                    // app settings to grant it manually.
                                    permissionRequester.openPermissionsScreen(ctx)
                                    SignInState.Error(getString(Res.string.settings_integration_calendar_blocked))
                                }
                                else -> SignInState.Error(getString(Res.string.settings_integration_calendar_not_granted))
                            }
                        }
                    }
                ) {
                    Text(stringResource(UtilRes.string.connect))
                }
            }
        }
    ) {
        when (val s = state) {
            is SignInState.Idle -> {
                Text(stringResource(Res.string.settings_integration_calendar_description))
            }
            is SignInState.SigningIn -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SignInState.Success -> {
                Text(stringResource(Res.string.settings_integration_calendar_connected))
            }
            is SignInState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun GTasksDialog(
    onDismiss: () -> Unit
) {
    val uiContext = rememberUiContext()!!
    val integration = koinInject<GTasksIntegration> { parametersOf(uiContext) }
    var state by remember { mutableStateOf<SignInState>(SignInState.Idle) }
    val scope = rememberCoroutineScope()

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Tasks") },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (state is SignInState.Success) UtilRes.string.done else UtilRes.string.cancel))
            }
            if (state !is SignInState.Success) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = state !is SignInState.SigningIn,
                    onClick = {
                        state = SignInState.SigningIn
                        scope.launch {
                            state = try {
                                if (integration.signIn(uiContext)) {
                                    SignInState.Success
                                } else {
                                    SignInState.Error(getString(Res.string.settings_integration_sign_in_cancelled))
                                }
                            } catch (e: Throwable) {
                                Logger.w("AddIntegration", e) { "Error during Google Tasks sign in: ${e.message}" }
                                SignInState.Error(e.message ?: getString(Res.string.settings_integration_unknown_error))
                            }
                        }
                    }
                ) {
                    Text(stringResource(UtilRes.string.sign_in))
                }
            }
        }
    ) {
        when (val s = state) {
            is SignInState.Idle -> {
                Text(stringResource(Res.string.settings_integration_gtasks_description))
            }
            is SignInState.SigningIn -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SignInState.Success -> {
                Text(stringResource(Res.string.settings_integration_signed_in))
            }
            is SignInState.Error -> {
                Text(
                    stringResource(Res.string.settings_integration_sign_in_error, s.message),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun NotionDialog(
    onDismiss: () -> Unit
) {
    val uiContext = rememberUiContext()!!
    val integration = koinInject<NotionIntegration>()
    var state by remember { mutableStateOf<SignInState>(SignInState.Idle) }
    val scope = rememberCoroutineScope()
    fun onSignIn() {
        state = SignInState.SigningIn
        scope.launch {
            state = try {
                if (integration.signIn(uiContext)) {
                    if (integration.hasPage()) {
                        SignInState.Success
                    } else {
                        try {
                            integration.unlink()
                        } catch (e: Throwable) {
                            Logger.w("NotionDialog", e) { "Error during Notion unlink: ${e.message}" }
                        }
                        SignInState.Error(getString(Res.string.settings_integration_notion_no_page))
                    }
                } else {
                    SignInState.Error(getString(Res.string.settings_integration_sign_in_cancelled))
                }
            } catch (e: Throwable) {
                Logger.w("AddIntegration", e) { "Error during Notion sign in: ${e.message}" }
                SignInState.Error(e.message ?: getString(Res.string.settings_integration_unknown_error))
            }
        }
    }
    // Once signed in, jump straight into picking the page for the to-do block.
    if (state is SignInState.Success) {
        NotionPageDialog(onDismiss = onDismiss)
        return
    }
    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Notion") },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(UtilRes.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = state !is SignInState.SigningIn,
                onClick = ::onSignIn
            ) {
                Text(stringResource(UtilRes.string.sign_in))
            }
        }
    ) {
        Column {
            when (val s = state) {
                is SignInState.Idle -> {
                    Text(stringResource(Res.string.settings_integration_notion_description))
                }
                is SignInState.SigningIn -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is SignInState.Error -> {
                    Text(
                        stringResource(Res.string.settings_integration_sign_in_error_multiline, s.message),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is SignInState.Success -> {}
            }
        }
    }
}

@Composable
fun ObsidianDialog(
    onDismiss: () -> Unit
) {
    val uiContext = rememberUiContext()!!
    val integration = koinInject<ObsidianIntegration>()
    val providers = koinInject<IntegrationProviderRepository>()
    val scope = rememberCoroutineScope()

    val alreadyConfigured = remember { integration.hasVault() }
    val defaultVaultName = stringResource(Res.string.settings_integration_obsidian_default_vault)

    var picking by remember { mutableStateOf(false) }
    var vaultName by remember { mutableStateOf(if (alreadyConfigured) integration.vaultDisplayName() ?: defaultVaultName else null) }
    var error by remember { mutableStateOf<String?>(null) }

    var mode by remember { mutableStateOf(if (alreadyConfigured) integration.currentMode() else ObsidianMode.TIMESTAMPED_FILES) }
    var subfolder by remember { mutableStateOf(if (alreadyConfigured) integration.currentSubfolder().ifEmpty { ObsidianPreferences.DEFAULT_SUBFOLDER } else ObsidianPreferences.DEFAULT_SUBFOLDER) }
    var customTag by remember { mutableStateOf(if (alreadyConfigured) integration.currentCustomTag() else "") }
    var appendPrefix by remember { mutableStateOf(integration.currentAppendPrefix()) }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var notePath by remember { mutableStateOf(if (alreadyConfigured) integration.currentTargetNote() else "") }

    LaunchedEffect(alreadyConfigured) {
        if (alreadyConfigured) {
            notes = runCatching { integration.listNotes() }.getOrDefault(emptyList())
        }
    }

    fun pickFolder() {
        picking = true
        scope.launch {
            try {
                if (integration.signIn(uiContext)) {
                    vaultName = integration.vaultDisplayName() ?: defaultVaultName
                    notes = integration.listNotes()
                    notePath = ""
                } else {
                    error = getString(Res.string.settings_integration_obsidian_no_folder)
                }
            } catch (e: Throwable) {
                Logger.w("ObsidianDialog", e) { "Vault pick failed: ${e.message}" }
                error = e.message ?: getString(Res.string.settings_integration_unknown_error)
            } finally {
                picking = false
            }
        }
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Obsidian") },
        scrollableContent = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(UtilRes.string.cancel)) }
            if (vaultName != null) {
                Spacer(Modifier.width(8.dp))
                val canSave = mode != ObsidianMode.NAMED_NOTE || notePath.isNotBlank()
                TextButton(
                    enabled = canSave,
                    onClick = {
                        scope.launch {
                            integration.saveConfig(
                                mode = mode,
                                targetNote = notePath,
                                subfolder = subfolder,
                                customTag = customTag,
                                appendPrefix = appendPrefix,
                            )
                            if (!alreadyConfigured) {
                                providers.selectNoteProvider(BuiltInIntegrationProviders.Obsidian)
                            }
                            onDismiss()
                        }
                    }
                ) { Text(stringResource(UtilRes.string.save)) }
            }
        }
    ) {
        Column {
            if (vaultName == null) {
                Text(stringResource(Res.string.settings_integration_obsidian_description))
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = !picking,
                    onClick = ::pickFolder,
                ) {
                    Text(
                        stringResource(
                            if (picking) Res.string.settings_integration_obsidian_picking
                            else Res.string.settings_integration_obsidian_pick_folder
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(Res.string.settings_integration_obsidian_vault_folder),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(vaultName ?: "", style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(
                        enabled = !picking,
                        onClick = ::pickFolder,
                    ) {
                        Text(
                            stringResource(
                                if (picking) Res.string.settings_integration_obsidian_picking
                                else Res.string.settings_integration_obsidian_change
                            )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.settings_integration_obsidian_how_saved),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                ObsidianModeSelector(
                    mode = mode,
                    onModeChange = { mode = it },
                    subfolder = subfolder,
                    onSubfolderChange = { subfolder = it },
                    customTag = customTag,
                    onCustomTagChange = { customTag = it },
                    notes = notes,
                    notePath = notePath,
                    onNotePathChange = { notePath = it },
                )
                if (mode != ObsidianMode.TIMESTAMPED_FILES) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = appendPrefix,
                        onValueChange = { appendPrefix = it },
                        label = { Text("Text before each note") },
                        placeholder = { Text("[[YYYY-MM-DD]] HH:mm: ") },
                        supportingText = {
                            Text("YYYY-MM-DD = date, HH:mm = time. Use \\n or Enter for a newline. Note text follows exactly; include any spaces or bullets. Leave empty for note text only.")
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (error != null) {
                Text(
                    stringResource(Res.string.settings_integration_error, error ?: ""),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ObsidianModeSelector(
    mode: ObsidianMode,
    onModeChange: (ObsidianMode) -> Unit,
    subfolder: String,
    onSubfolderChange: (String) -> Unit,
    customTag: String,
    onCustomTagChange: (String) -> Unit,
    notes: List<String>,
    notePath: String,
    onNotePathChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ModeOption(
            title = stringResource(Res.string.settings_integration_obsidian_separate_file),
            description = stringResource(Res.string.settings_integration_obsidian_separate_file_description),
            selected = mode == ObsidianMode.TIMESTAMPED_FILES,
        ) { onModeChange(ObsidianMode.TIMESTAMPED_FILES) }
        ModeOption(
            title = stringResource(Res.string.settings_integration_obsidian_main_note),
            description = stringResource(Res.string.settings_integration_obsidian_main_note_description),
            selected = mode == ObsidianMode.MAIN_NOTE,
        ) { onModeChange(ObsidianMode.MAIN_NOTE) }
        ModeOption(
            title = stringResource(Res.string.settings_integration_obsidian_existing_note),
            description = stringResource(Res.string.settings_integration_obsidian_existing_note_description),
            selected = mode == ObsidianMode.NAMED_NOTE,
        ) { onModeChange(ObsidianMode.NAMED_NOTE) }

        // Mode-specific input, visually separated from the choices above.
        Spacer(Modifier.height(12.dp))
        when (mode) {
            ObsidianMode.TIMESTAMPED_FILES -> {
                OutlinedTextField(
                    value = subfolder,
                    onValueChange = onSubfolderChange,
                    label = { Text(stringResource(Res.string.settings_integration_obsidian_subfolder)) },
                    placeholder = { Text(ObsidianPreferences.DEFAULT_SUBFOLDER) },
                    supportingText = { Text(stringResource(Res.string.settings_integration_obsidian_subfolder_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customTag,
                    onValueChange = onCustomTagChange,
                    label = { Text(stringResource(Res.string.settings_integration_obsidian_extra_tag)) },
                    placeholder = { Text(stringResource(Res.string.settings_integration_obsidian_extra_tag_placeholder)) },
                    supportingText = { Text(stringResource(Res.string.settings_integration_obsidian_extra_tag_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ObsidianMode.NAMED_NOTE -> {
                NotePathField(
                    notes = notes,
                    notePath = notePath,
                    onNotePathChange = onNotePathChange,
                )
            }
            ObsidianMode.MAIN_NOTE -> {
                Text(
                    stringResource(Res.string.settings_integration_obsidian_main_note_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Free-text note path with suggestions from the vault's existing notes. */
@Composable
private fun NotePathField(
    notes: List<String>,
    notePath: String,
    onNotePathChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(notes, notePath) {
        val q = notePath.trim()
        if (q.isEmpty()) notes else notes.filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = notePath,
            onValueChange = {
                onNotePathChange(it)
                expanded = true
            },
            label = { Text(stringResource(Res.string.settings_integration_obsidian_note_path)) },
            placeholder = { Text(stringResource(Res.string.settings_integration_obsidian_note_path_placeholder)) },
            supportingText = { Text(stringResource(Res.string.settings_integration_obsidian_note_path_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.take(8).forEach { note ->
                DropdownMenuItem(
                    text = { Text(note) },
                    onClick = {
                        onNotePathChange(note)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun TaskerDialog(
    onDismiss: () -> Unit
) {
    val uiContext = rememberUiContext()!!
    var state by remember { mutableStateOf<SignInState>(SignInState.Idle) }
    val scope = rememberCoroutineScope()

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Tasker") },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (state is SignInState.Success) UtilRes.string.done else UtilRes.string.cancel))
            }
            if (state !is SignInState.Success) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = state !is SignInState.SigningIn,
                    onClick = {
                        state = SignInState.SigningIn
                        scope.launch {
                            state = try {
                                val client = BuiltInIntegrationProviders.Tasker.notes()
                                if (client.signIn(uiContext)) {
                                    SignInState.Success
                                } else {
                                    SignInState.Error(getString(Res.string.settings_integration_tasker_not_installed))
                                }
                            } catch (e: Throwable) {
                                Logger.w("AddIntegration", e) { "Error connecting Tasker: ${e.message}" }
                                SignInState.Error(e.message ?: getString(Res.string.settings_integration_unknown_error))
                            }
                        }
                    }
                ) {
                    Text(stringResource(UtilRes.string.connect))
                }
            }
        }
    ) {
        when (val s = state) {
            is SignInState.Idle -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.settings_integration_tasker_description))
                    Text(stringResource(Res.string.settings_integration_tasker_receive), fontWeight = FontWeight.Bold)
                    Text(stringResource(Res.string.settings_integration_tasker_step1))
                    Text(stringResource(Res.string.settings_integration_tasker_step2))
                    Text(stringResource(Res.string.settings_integration_tasker_step3))
                }
            }
            is SignInState.SigningIn -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SignInState.Success -> {
                Text(stringResource(Res.string.settings_integration_tasker_connected))
            }
            is SignInState.Error -> {
                Text(
                    stringResource(Res.string.settings_integration_error, s.message),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
