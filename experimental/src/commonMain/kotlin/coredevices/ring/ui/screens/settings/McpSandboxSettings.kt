package coredevices.ring.ui.screens.settings

import BugReportButton
import CoreNav
import NoOpCoreNav
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.back
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.mcp.client.HttpMcpIntegration
import coredevices.mcp.client.HttpMcpProtocol
import coredevices.mcp.data.McpPrompt
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.McpServerEntry
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.delay
import coredevices.ring.ui.PreviewWrapper
import coredevices.ui.M3Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class McpSandboxSettingsViewModel(
    val sandboxGroupId: Long,
    val mcpSandboxRepository: McpSandboxRepository
): ViewModel() {
    val serverEntries = mcpSandboxRepository.getMcpServerEntriesForGroup(sandboxGroupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun removeEntry(entry: McpServerEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            mcpSandboxRepository.removeEntry(entry, sandboxGroupId)
        }
    }

    fun addUpdateEntry(entry: McpServerEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            when (entry) {
                is McpServerEntry.BuiltinMcpEntry -> {
                    error("Adding/updating built-in MCP entries is not supported")
                }
                is McpServerEntry.HttpServerEntry -> {
                    mcpSandboxRepository.addOrUpdateHttpServer(
                        sandboxGroupId,
                        entry.server
                    )
                }
            }
        }
    }
}

@Composable
fun McpSandboxSettings(coreNav: CoreNav, sandboxGroupId: Long) {
    val viewModel = koinViewModel<McpSandboxSettingsViewModel>(key = sandboxGroupId.toString()) {
        parametersOf(sandboxGroupId)
    }
    McpSandboxSettings(
        coreNav = coreNav,
        serverEntries = viewModel.serverEntries,
        onRemoveEntry = viewModel::removeEntry,
        onAddUpdateEntry = viewModel::addUpdateEntry
    )
}

@Composable
private fun McpSandboxSettings(
    coreNav: CoreNav,
    serverEntries: StateFlow<List<McpServerEntry>>,
    onRemoveEntry: (McpServerEntry) -> Unit,
    onAddUpdateEntry: (McpServerEntry) -> Unit
) {
    val entries by serverEntries.collectAsState()
    var editingEntry by remember { mutableStateOf<McpServerEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        EntryEditDialog(
            initialEntry = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                onAddUpdateEntry(entry)
                showAddDialog = false
            },
            onDelete = { }
        )
    }

    if (editingEntry != null) {
        EntryEditDialog(
            initialEntry = editingEntry,
            onDismiss = { editingEntry = null },
            onConfirm = { entry ->
                onAddUpdateEntry(entry)
                editingEntry = null
            },
            onDelete = { entry ->
                onRemoveEntry(entry)
                editingEntry = null
            }
        )
    }

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
                    Text("MCP Sandbox Settings")
                },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "McpSandboxSettings",
                        )
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add MCP Server")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding)
        ) {
            items(entries.size) { index ->
                val entry = entries[index]
                McpServerEntryItem(
                    entry = entry,
                    onClick = { editingEntry = entry }
                )
            }
        }
    }
}

@Composable
private fun EntryEditDialog(
    initialEntry: McpServerEntry?,
    onDismiss: () -> Unit,
    onConfirm: (McpServerEntry) -> Unit,
    onDelete: (McpServerEntry) -> Unit
) {
    when (initialEntry) {
        is McpServerEntry.BuiltinMcpEntry -> {
            BuiltinEntryDeleteDialog(
                entry = initialEntry,
                onDismiss = onDismiss,
                onDelete = { onDelete(initialEntry) }
            )
        }
        is McpServerEntry.HttpServerEntry -> {
            HttpServerEditDialog(
                initialServer = initialEntry.server,
                onDismiss = onDismiss,
                onConfirm = { server ->
                    onConfirm(McpServerEntry.HttpServerEntry(server))
                },
                onDelete = { onDelete(initialEntry) }
            )
        }
        null -> {
            HttpServerEditDialog(
                initialServer = null,
                onDismiss = onDismiss,
                onConfirm = { server ->
                    onConfirm(McpServerEntry.HttpServerEntry(server))
                },
                onDelete = null
            )
        }
    }
}

@Composable
private fun BuiltinEntryDeleteDialog(
    entry: McpServerEntry.BuiltinMcpEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    M3Dialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Remove Built-in MCP")
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {}, enabled = false) {
                Text("Remove")
            }
        }
    ) {
        Text("Remove \"${entry.builtinMcpName}\" from this group?")
    }
}

@Composable
private fun HttpServerEditDialog(
    initialServer: HttpMcpServerEntity?,
    onDismiss: () -> Unit,
    onConfirm: (HttpMcpServerEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialServer?.name ?: "") }
    var url by remember { mutableStateOf(initialServer?.url ?: "") }
    var streamable by remember { mutableStateOf(initialServer?.streamable ?: false) }
    var authHeader by remember { mutableStateOf(initialServer?.authHeader ?: "") }
    var showAuthSection by remember { mutableStateOf(initialServer?.authHeader?.isNotBlank() == true) }
    var cachedTitle by remember { mutableStateOf(initialServer?.cachedTitle ?: "") }
    var isFetchingTitle by remember { mutableStateOf(false) }
    var availablePrompts by remember { mutableStateOf<List<McpPrompt>>(emptyList()) }
    var selectedPrompts by remember { mutableStateOf(initialServer?.includedPrompts?.toSet() ?: emptySet()) }
    var showPromptsSection by remember { mutableStateOf(initialServer?.includedPrompts?.isNotEmpty() == true) }

    // Debounced fetch of server title and prompts when URL, protocol, or auth header changes
    LaunchedEffect(url, streamable, authHeader) {
        if (url.isBlank()) {
            cachedTitle = ""
            availablePrompts = emptyList()
            return@LaunchedEffect
        }
        delay(500) // Debounce
        isFetchingTitle = true
        try {
            val protocol = if (streamable) HttpMcpProtocol.Streaming else HttpMcpProtocol.Sse
            val integration = HttpMcpIntegration(
                name = "title-fetch",
                implementation = Implementation(name = "CoreApp", version = "1.0.0"),
                url = url,
                protocol = protocol,
                authHeader = authHeader.ifBlank { null }
            )
            integration.connect()
            cachedTitle = integration.title ?: ""
            availablePrompts = integration.listPrompts()
            integration.close()
        } catch (e: Exception) {
            Logger.withTag("HttpServerEditDialog").w("Failed to fetch MCP server title", e)
            cachedTitle = ""
            availablePrompts = emptyList()
        } finally {
            isFetchingTitle = false
        }
    }

    val isEditing = initialServer != null
    val canSave = name.isNotBlank() && url.isNotBlank() && cachedTitle.isNotBlank()

    M3Dialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit HTTP MCP Server" else "Add HTTP MCP Server")
        },
        buttons = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    onConfirm(
                        HttpMcpServerEntity(
                            id = initialServer?.id ?: 0L,
                            cachedTitle = cachedTitle,
                            name = name,
                            url = url,
                            streamable = streamable,
                            authHeader = authHeader.ifBlank { null },
                            includedPrompts = selectedPrompts.toList()
                        )
                    )
                },
                enabled = canSave
            ) {
                Text("Save")
            }
        }
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                supportingText = if (isFetchingTitle) {
                    { Text("Fetching server info...") }
                } else if (cachedTitle.isNotBlank()) {
                    { Text("Server: $cachedTitle") }
                } else {
                    { Text("") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = !streamable,
                    onClick = { streamable = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("SSE")
                }
                SegmentedButton(
                    selected = streamable,
                    onClick = { streamable = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Streamable")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAuthSection = !showAuthSection },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (showAuthSection) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null
                )
                Text("Authorization (optional)")
            }
            AnimatedVisibility(visible = showAuthSection) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = authHeader,
                        onValueChange = { authHeader = it },
                        label = { Text("Authorization Header") },
                        placeholder = { Text("Bearer token123...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (availablePrompts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPromptsSection = !showPromptsSection },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showPromptsSection) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = null
                    )
                    Text("Prompts (${selectedPrompts.size}/${availablePrompts.size} selected)")
                }
                AnimatedVisibility(visible = showPromptsSection) {
                    Column {
                        availablePrompts.forEach { prompt ->
                            val description = prompt.description
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPrompts = if (prompt.name in selectedPrompts) {
                                            selectedPrompts - prompt.name
                                        } else {
                                            selectedPrompts + prompt.name
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = prompt.name in selectedPrompts,
                                    onCheckedChange = { checked ->
                                        selectedPrompts = if (checked) {
                                            selectedPrompts + prompt.name
                                        } else {
                                            selectedPrompts - prompt.name
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prompt.title ?: prompt.name)
                                    if (description != null) {
                                        Text(
                                            text = description,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun McpServerEntryItem(
    entry: McpServerEntry,
    onClick: (McpServerEntry) -> Unit = { },
) {
    ListItem(
        modifier = Modifier.clickable {
            onClick(entry)
        },
        overlineContent = {
            Text(
                when (entry) {
                    is McpServerEntry.BuiltinMcpEntry -> "Built-in"
                    is McpServerEntry.HttpServerEntry -> "HTTP"
                }
            )
        },
        headlineContent = {
            Text(
                when (entry) {
                    is McpServerEntry.BuiltinMcpEntry -> entry.builtinMcpName
                    is McpServerEntry.HttpServerEntry -> entry.server.cachedTitle
                }
            )
        },
        supportingContent = {
            when (entry) {
                is McpServerEntry.HttpServerEntry -> {
                    Text(entry.server.url)
                }
                else -> {}
            }
        }
    )
}


@Preview
@Composable
private fun McpSandboxSettingsPreview() {
    PreviewWrapper {
        McpSandboxSettings(
            coreNav = NoOpCoreNav,
            serverEntries = MutableStateFlow(
                listOf(
                    McpServerEntry.BuiltinMcpEntry("builtin-1"),
                )
            ),
            onRemoveEntry = {},
            onAddUpdateEntry = {}
        )
    }
}

@Preview
@Composable
private fun BuiltinEntryDeleteDialogPreview() {
    PreviewWrapper {
        BuiltinEntryDeleteDialog(
            entry = McpServerEntry.BuiltinMcpEntry("clock"),
            onDismiss = {},
            onDelete = {}
        )
    }
}

@Preview
@Composable
private fun HttpServerEditDialogNewPreview() {
    PreviewWrapper {
        HttpServerEditDialog(
            initialServer = null,
            onDismiss = {},
            onConfirm = {},
            onDelete = null
        )
    }
}

@Preview
@Composable
private fun HttpServerEditDialogEditPreview() {
    PreviewWrapper {
        HttpServerEditDialog(
            initialServer = HttpMcpServerEntity(
                id = 1L,
                cachedTitle = "My MCP Server",
                name = "my-server",
                url = "https://example.com/mcp",
                streamable = false,
                authHeader = "Bearer 123abc",
                includedPrompts = listOf("default")
            ),
            onDismiss = {},
            onConfirm = {},
            onDelete = {}
        )
    }
}