package coredevices.ring.ui.screens.settings

import BugReportButton
import CoreNav
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.Surface
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
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.back
import coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.notes.TASKER_DEFINITION
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianMode
import coredevices.ring.agent.integrations.obsidian.ObsidianPreferences
import coredevices.ring.data.IntegrationDefinition
import coredevices.ring.database.Preferences
import coredevices.ui.M3Dialog
import coredevices.util.Platform
import coredevices.util.isAndroid
import coredevices.util.rememberUiContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

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
                            contentDescription = stringResource(coreapp.util.generated.resources.Res.string.back)
                        )
                    }
                },
                title = {
                    Text("Add integration")
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
                val def = remember { GTasksIntegration.DEFINITION }
                Item(def) {
                    dialog = {
                        GTasksDialog(
                            onDismiss = { dialog = null }
                        )
                    }
                }
            }
            item {
                val def = remember { NotionIntegration.DEFINITION }
                Item(def) {
                    dialog = {
                        NotionDialog(
                            onDismiss = { dialog = null }
                        )
                    }
                }
            }
            item {
                val def = remember { ObsidianIntegration.DEFINITION }
                Item(def) {
                    dialog = {
                        ObsidianDialog(
                            onDismiss = { dialog = null }
                        )
                    }
                }
            }
            if (platform.isAndroid) {
                item {
                    val def = remember { TASKER_DEFINITION }
                    Item(def) {
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
private fun Item(def: IntegrationDefinition, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(def.title) },
        supportingContent = { Text(
            buildList {
                if (def.reminder != null) {
                    add("Reminders")
                }
                if (def.notes != null) {
                    add("Notes")
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
                Text(if (state is SignInState.Success) "Done" else "Cancel")
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
                                    SignInState.Error("Sign in was cancelled.")
                                }
                            } catch (e: Throwable) {
                                Logger.w("AddIntegration", e) { "Error during Google Tasks sign in: ${e.message}" }
                                SignInState.Error(e.message ?: "Unknown error")
                            }
                        }
                    }
                ) {
                    Text("Sign In")
                }
            }
        }
    ) {
        when (val s = state) {
            is SignInState.Idle -> {
                Text("Sign in with Google to sync reminders to Google Tasks.")
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
                Text("Successfully signed in.")
            }
            is SignInState.Error -> {
                Text(
                    "Error signing in: ${s.message}",
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
                        SignInState.Error("No page found for notes. Please give access to a single page in Notion for your notes.")
                    }
                } else {
                    SignInState.Error("Sign in was cancelled.")
                }
            } catch (e: Throwable) {
                Logger.w("AddIntegration", e) { "Error during Notion sign in: ${e.message}" }
                SignInState.Error(e.message ?: "Unknown error")
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
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = state !is SignInState.SigningIn,
                onClick = ::onSignIn
            ) {
                Text("Sign In")
            }
        }
    ) {
        Column {
            when (val s = state) {
                is SignInState.Idle -> {
                    Text("Sign in to send notes to Notion.")
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
                        "Error signing in:\n${s.message}",
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
    val preferences = koinInject<Preferences>()
    val scope = rememberCoroutineScope()

    val alreadyConfigured = remember { integration.hasVault() }

    var picking by remember { mutableStateOf(false) }
    var vaultName by remember { mutableStateOf(if (alreadyConfigured) integration.vaultDisplayName() ?: "Obsidian vault" else null) }
    var error by remember { mutableStateOf<String?>(null) }

    var mode by remember { mutableStateOf(if (alreadyConfigured) integration.currentMode() else ObsidianMode.TIMESTAMPED_FILES) }
    var subfolder by remember { mutableStateOf(if (alreadyConfigured) integration.currentSubfolder().ifEmpty { ObsidianPreferences.DEFAULT_SUBFOLDER } else ObsidianPreferences.DEFAULT_SUBFOLDER) }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedNote by remember { mutableStateOf(if (alreadyConfigured) integration.currentTargetNote().ifEmpty { null } else null) }

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
                    vaultName = integration.vaultDisplayName() ?: "Obsidian vault"
                    notes = integration.listNotes()
                    selectedNote = null
                } else {
                    error = "No folder selected."
                }
            } catch (e: Throwable) {
                Logger.w("ObsidianDialog", e) { "Vault pick failed: ${e.message}" }
                error = e.message ?: "Unknown error"
            } finally {
                picking = false
            }
        }
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Obsidian") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            if (vaultName != null) {
                Spacer(Modifier.width(8.dp))
                val canSave = mode != ObsidianMode.NAMED_NOTE || selectedNote != null
                TextButton(
                    enabled = canSave,
                    onClick = {
                        scope.launch {
                            integration.saveConfig(
                                mode = mode,
                                targetNote = selectedNote ?: "",
                                subfolder = subfolder,
                            )
                            preferences.setNoteProvider(NoteProvider.Obsidian)
                            onDismiss()
                        }
                    }
                ) { Text("Save") }
            }
        }
    ) {
        Column {
            if (vaultName == null) {
                Text("Pick your Obsidian vault folder. Notes will be written there as Markdown files.")
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = !picking,
                    onClick = ::pickFolder,
                ) { Text(if (picking) "Picking…" else "Pick vault folder") }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "VAULT FOLDER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(vaultName ?: "", style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(
                        enabled = !picking,
                        onClick = ::pickFolder,
                    ) { Text(if (picking) "Picking…" else "Change") }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "How should each note be saved?",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                ObsidianModeSelector(
                    mode = mode,
                    onModeChange = { mode = it },
                    subfolder = subfolder,
                    onSubfolderChange = { subfolder = it },
                    notes = notes,
                    selectedNote = selectedNote,
                    onSelectNote = { selectedNote = it },
                )
            }
            if (error != null) {
                Text(
                    "Error: $error",
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
    notes: List<String>,
    selectedNote: String?,
    onSelectNote: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ModeOption(
            title = "A separate file for each note",
            description = "Saved as its own timestamped file",
            selected = mode == ObsidianMode.TIMESTAMPED_FILES,
        ) { onModeChange(ObsidianMode.TIMESTAMPED_FILES) }
        ModeOption(
            title = "Add to one main note",
            description = "Everything is appended to Pebble Index.md",
            selected = mode == ObsidianMode.MAIN_NOTE,
        ) { onModeChange(ObsidianMode.MAIN_NOTE) }
        ModeOption(
            title = "Add to an existing note",
            description = "Append to a note you choose",
            selected = mode == ObsidianMode.NAMED_NOTE,
        ) { onModeChange(ObsidianMode.NAMED_NOTE) }

        // Mode-specific input, visually separated from the choices above.
        Spacer(Modifier.height(12.dp))
        when (mode) {
            ObsidianMode.TIMESTAMPED_FILES -> {
                OutlinedTextField(
                    value = subfolder,
                    onValueChange = onSubfolderChange,
                    label = { Text("Subfolder") },
                    placeholder = { Text(ObsidianPreferences.DEFAULT_SUBFOLDER) },
                    supportingText = { Text("A folder inside your vault. Created if it doesn't exist.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ObsidianMode.NAMED_NOTE -> {
                if (notes.isEmpty()) {
                    Text(
                        "No notes found in this vault yet. Create one in Obsidian first, or choose another option.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    NotePickerField(
                        notes = notes,
                        selectedNote = selectedNote,
                        onSelectNote = onSelectNote,
                    )
                }
            }
            ObsidianMode.MAIN_NOTE -> {
                Text(
                    "Creates Pebble Index.md in your vault if it doesn't exist yet.",
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

/** Compact dropdown for choosing which existing note to append to. */
@Composable
private fun NotePickerField(
    notes: List<String>,
    selectedNote: String?,
    onSelectNote: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Note to append to",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selectedNote ?: "Choose a note",
                        modifier = Modifier.weight(1f),
                        color = if (selectedNote == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                notes.forEach { note ->
                    DropdownMenuItem(
                        text = { Text(note) },
                        onClick = {
                            onSelectNote(note)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun TaskerDialog(
    onDismiss: () -> Unit
) {
    val uiContext = rememberUiContext()!!
    val noteIntegrationFactory = koinInject<NoteIntegrationFactory>()
    var state by remember { mutableStateOf<SignInState>(SignInState.Idle) }
    val scope = rememberCoroutineScope()

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Tasker") },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(if (state is SignInState.Success) "Done" else "Cancel")
            }
            if (state !is SignInState.Success) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = state !is SignInState.SigningIn,
                    onClick = {
                        state = SignInState.SigningIn
                        scope.launch {
                            state = try {
                                val client = noteIntegrationFactory.createNoteClient(NoteProvider.Tasker)
                                if (client.signIn(uiContext)) {
                                    SignInState.Success
                                } else {
                                    SignInState.Error("Tasker is not installed. Install Tasker, then try again.")
                                }
                            } catch (e: Throwable) {
                                Logger.w("AddIntegration", e) { "Error connecting Tasker: ${e.message}" }
                                SignInState.Error(e.message ?: "Unknown error")
                            }
                        }
                    }
                ) {
                    Text("Connect")
                }
            }
        }
    ) {
        when (val s = state) {
            is SignInState.Idle -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Sends your notes and reminders to Tasker as a shared intent " +
                            "(text/plain), so you can route them to any app or action."
                    )
                    Text("To receive them, in Tasker:", fontWeight = FontWeight.Bold)
                    Text("1.  Add a profile, pick Event → Received Share.")
                    Text("2.  Attach a task — your text arrives in the %rs_text variable.")
                    Text(
                        "3.  Optional: limit it to this app with %rs_package_name = " +
                            "coredevices.coreapp."
                    )
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
                Text("Tasker connected. Choose it for notes or reminders in Index settings.")
            }
            is SignInState.Error -> {
                Text(
                    "Error: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}