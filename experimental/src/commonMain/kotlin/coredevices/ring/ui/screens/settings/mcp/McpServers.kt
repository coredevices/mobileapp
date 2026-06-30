package coredevices.ring.ui.screens.settings.mcp

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
import coredevices.mcp.client.HttpMcpIntegration
import coredevices.mcp.client.HttpMcpProtocol
import coredevices.mcp.data.McpPrompt
import coredevices.ring.database.room.repository.McpServerEntry
import coredevices.ring.ui.PreviewWrapper
import coredevices.ui.M3Dialog
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

/** "MCP Servers" tab: every known server (builtin and HTTP), each editable
 *  to choose which sandbox groups it belongs to. */
@Composable
fun McpServersTab(
    serverEntries: StateFlow<List<McpServerEntry>>,
    allGroups: StateFlow<List<McpSandboxGroupEntity>>,
    defaultGroupId: Long,
    showAddServerDialog: Boolean,
    onDismissAddServerDialog: () -> Unit,
    loadGroupIds: suspend (McpServerEntry) -> Set<Long>,
    onSaveHttpServer: (HttpMcpServerEntity, Set<Long>) -> Unit,
    onSetBuiltinGroups: (String, Set<Long>) -> Unit,
    onDeleteHttpServer: (HttpMcpServerEntity) -> Unit,
) {
    val entries by serverEntries.collectAsState()
    val groups by allGroups.collectAsState()
    var editingEntry by remember { mutableStateOf<McpServerEntry?>(null) }
    var editingGroupIds by remember { mutableStateOf<Set<Long>?>(null) }

    LaunchedEffect(editingEntry) {
        editingGroupIds = null
        editingGroupIds = editingEntry?.let { loadGroupIds(it) }
    }

    if (showAddServerDialog) {
        HttpServerEditDialog(
            initialServer = null,
            allGroups = groups,
            initialGroupIds = emptySet(),
            onDismiss = onDismissAddServerDialog,
            onConfirm = { server, groupIds ->
                onSaveHttpServer(server, groupIds)
                onDismissAddServerDialog()
            },
            onDelete = null
        )
    }

    val editing = editingEntry
    val editingGroups = editingGroupIds
    if (editing != null && editingGroups != null) {
        when (editing) {
            is McpServerEntry.BuiltinMcpEntry -> {
                BuiltinGroupsDialog(
                    entry = editing,
                    allGroups = groups,
                    defaultGroupId = defaultGroupId,
                    initialGroupIds = editingGroups,
                    onDismiss = { editingEntry = null },
                    onConfirm = { groupIds ->
                        onSetBuiltinGroups(editing.builtinMcpName, groupIds)
                        editingEntry = null
                    }
                )
            }
            is McpServerEntry.HttpServerEntry -> {
                HttpServerEditDialog(
                    initialServer = editing.server,
                    allGroups = groups,
                    initialGroupIds = editingGroups,
                    onDismiss = { editingEntry = null },
                    onConfirm = { server, groupIds ->
                        onSaveHttpServer(server, groupIds)
                        editingEntry = null
                    },
                    onDelete = {
                        onDeleteHttpServer(editing.server)
                        editingEntry = null
                    }
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth()
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

/** Multi-select dropdown of sandbox groups. [optionEnabled] decides per group
 *  whether its checkbox can be toggled, given its current selection state. */
@Composable
private fun GroupSelectionDropdown(
    allGroups: List<McpSandboxGroupEntity>,
    selectedGroupIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    optionEnabled: (McpSandboxGroupEntity, Boolean) -> Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = allGroups
        .filter { it.id in selectedGroupIds }
        .joinToString { it.title }
        .ifEmpty { "None" }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            value = summary,
            onValueChange = { },
            readOnly = true,
            label = { Text("Groups") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryNotEditable
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allGroups.forEach { group ->
                val isSelected = group.id in selectedGroupIds
                val enabled = optionEnabled(group, isSelected)
                DropdownMenuItem(
                    enabled = enabled,
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                enabled = enabled
                            )
                            Text(group.title)
                        }
                    },
                    onClick = {
                        onSelectionChange(
                            if (isSelected) selectedGroupIds - group.id
                            else selectedGroupIds + group.id
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BuiltinGroupsDialog(
    entry: McpServerEntry.BuiltinMcpEntry,
    allGroups: List<McpSandboxGroupEntity>,
    defaultGroupId: Long,
    initialGroupIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    var selectedGroupIds by remember { mutableStateOf(initialGroupIds) }
    M3Dialog(
        onDismissRequest = onDismiss,
        title = {
            Text(entry.builtinMcpName)
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onConfirm(selectedGroupIds) }) {
                Text("Save")
            }
        }
    ) {
        Column {
            Text("Choose which sandbox groups include this built-in MCP. It can't be removed from the default group.")
            Spacer(Modifier.height(8.dp))
            GroupSelectionDropdown(
                allGroups = allGroups,
                selectedGroupIds = selectedGroupIds,
                onSelectionChange = { selectedGroupIds = it },
                // Builtins must stay in the default group
                optionEnabled = { group, isSelected ->
                    !(isSelected && group.id == defaultGroupId)
                }
            )
        }
    }
}

@Composable
private fun HttpServerEditDialog(
    initialServer: HttpMcpServerEntity?,
    allGroups: List<McpSandboxGroupEntity>,
    initialGroupIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (HttpMcpServerEntity, Set<Long>) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialServer?.name ?: "") }
    var url by remember { mutableStateOf(initialServer?.url ?: "") }
    var streamable by remember { mutableStateOf(initialServer?.streamable ?: false) }
    var authHeader by remember { mutableStateOf(initialServer?.authHeader ?: "") }
    var showAuthSection by remember { mutableStateOf(initialServer?.authHeader?.isNotBlank() == true) }
    var cachedTitle by remember { mutableStateOf(initialServer?.cachedTitle ?: "") }
    var isFetchingTitle by remember { mutableStateOf(false) }
    var serverContactable by remember { mutableStateOf<Boolean?>(null) }
    var availablePrompts by remember { mutableStateOf<List<McpPrompt>>(emptyList()) }
    var selectedPrompts by remember { mutableStateOf(initialServer?.includedPrompts?.toSet() ?: emptySet()) }
    var showPromptsSection by remember { mutableStateOf(initialServer?.includedPrompts?.isNotEmpty() == true) }
    var selectedGroupIds by remember { mutableStateOf(initialGroupIds) }

    // Debounced fetch of server title and prompts when URL, protocol, or auth header changes
    LaunchedEffect(url, streamable, authHeader) {
        if (url.isBlank()) {
            cachedTitle = ""
            availablePrompts = emptyList()
            serverContactable = null
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
            serverContactable = true
        } catch (e: Exception) {
            Logger.withTag("HttpServerEditDialog").w("Failed to fetch MCP server title", e)
            cachedTitle = ""
            availablePrompts = emptyList()
            serverContactable = false
        } finally {
            isFetchingTitle = false
        }
    }

    val isEditing = initialServer != null
    // Name and URL are required, and the server must be contactable so we don't save a
    // broken entry.
    val canSave = name.isNotBlank() && url.isNotBlank() && serverContactable == true

    M3Dialog(
        onDismissRequest = onDismiss,
        scrollableContent = true,
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
                        ),
                        selectedGroupIds
                    )
                },
                enabled = canSave
            ) {
                Text("Save")
            }
        }
    ) {
        Column {
            // Resolved inside the dialog: dialogs have their own focus manager.
            val focusManager = LocalFocusManager.current
            val dismissKeyboard = KeyboardActions(onDone = { focusManager.clearFocus() })
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = dismissKeyboard,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                isError = serverContactable == false,
                supportingText = when {
                    isFetchingTitle -> {
                        { Text("Fetching server info...") }
                    }
                    serverContactable == false -> {
                        { Text("Server not contactable") }
                    }
                    cachedTitle.isNotBlank() -> {
                        { Text("Server: $cachedTitle") }
                    }
                    else -> {
                        { Text("") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = dismissKeyboard,
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
            GroupSelectionDropdown(
                allGroups = allGroups,
                selectedGroupIds = selectedGroupIds,
                onSelectionChange = { selectedGroupIds = it },
                // Index Agent groups ignore HTTP servers, so only allow removal there
                // (associations may exist from before a group's model type changed)
                optionEnabled = { group, isSelected ->
                    isSelected || group.modelType != SandboxModelType.IndexAgent
                }
            )
            Text(
                text = "HTTP servers can't be added to Index Agent groups as they would be ignored",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = dismissKeyboard,
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
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    is McpServerEntry.HttpServerEntry ->
                        entry.server.cachedTitle.ifBlank { entry.server.name }
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

private val previewGroups = listOf(
    McpSandboxGroupEntity(id = 1, title = "Default Group", modelType = SandboxModelType.IndexAgent),
    McpSandboxGroupEntity(id = 2, title = "Custom Group", modelType = SandboxModelType.HighCapability)
)

@Preview
@Composable
private fun McpServersTabPreview() {
    PreviewWrapper {
        McpServersTab(
            serverEntries = MutableStateFlow(
                listOf(
                    McpServerEntry.BuiltinMcpEntry("builtin-1"),
                )
            ),
            allGroups = MutableStateFlow(previewGroups),
            defaultGroupId = 1L,
            showAddServerDialog = false,
            onDismissAddServerDialog = {},
            loadGroupIds = { emptySet() },
            onSaveHttpServer = { _, _ -> },
            onSetBuiltinGroups = { _, _ -> },
            onDeleteHttpServer = {}
        )
    }
}

@Preview
@Composable
private fun BuiltinGroupsDialogPreview() {
    PreviewWrapper {
        BuiltinGroupsDialog(
            entry = McpServerEntry.BuiltinMcpEntry("clock"),
            allGroups = previewGroups,
            defaultGroupId = 1L,
            initialGroupIds = setOf(1L),
            onDismiss = {},
            onConfirm = {}
        )
    }
}

@Preview
@Composable
private fun HttpServerEditDialogNewPreview() {
    PreviewWrapper {
        HttpServerEditDialog(
            initialServer = null,
            allGroups = previewGroups,
            initialGroupIds = emptySet(),
            onDismiss = {},
            onConfirm = { _, _ -> },
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
            allGroups = previewGroups,
            initialGroupIds = setOf(2L),
            onDismiss = {},
            onConfirm = { _, _ -> },
            onDelete = {}
        )
    }
}
