package coredevices.ring.ui.screens.settings.mcp

import BugReportButton
import CoreNav
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.McpServerEntry
import coredevices.ring.ui.PreviewWrapper
import coredevices.ui.M3Dialog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

class McpSandboxGroupsViewModel(
    val mcpSandboxRepository: McpSandboxRepository
): ViewModel() {
    val sandboxGroups = mcpSandboxRepository.getAllGroupsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
    val defaultGroupId = flow { emit(mcpSandboxRepository.getDefaultGroupId()) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = -1L
    )
    val serverEntries = mcpSandboxRepository.getAllServerEntriesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun updateModelType(groupId: Long, modelType: SandboxModelType) {
        viewModelScope.launch {
            mcpSandboxRepository.updateGroupModelType(groupId, modelType)
        }
    }

    fun createGroup(title: String) {
        viewModelScope.launch {
            mcpSandboxRepository.createGroup(title)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            mcpSandboxRepository.deleteGroup(groupId)
        }
    }

    suspend fun groupIdsForEntry(entry: McpServerEntry): Set<Long> =
        mcpSandboxRepository.getGroupIdsForEntry(entry)

    fun saveHttpServer(server: HttpMcpServerEntity, groupIds: Set<Long>) {
        viewModelScope.launch {
            mcpSandboxRepository.addOrUpdateHttpServer(server, groupIds)
        }
    }

    fun setBuiltinGroups(builtinMcpName: String, groupIds: Set<Long>) {
        viewModelScope.launch {
            mcpSandboxRepository.setGroupsForEntry(
                McpServerEntry.BuiltinMcpEntry(builtinMcpName),
                groupIds
            )
        }
    }

    fun deleteHttpServer(server: HttpMcpServerEntity) {
        viewModelScope.launch {
            mcpSandboxRepository.deleteHttpServer(server)
        }
    }
}

private const val GROUPS_TAB = 0
private const val SERVERS_TAB = 1

@Composable
fun McpSandboxGroups(coreNav: CoreNav) {
    val vm = koinViewModel<McpSandboxGroupsViewModel>()
    val defaultGroupId by vm.defaultGroupId.collectAsState()
    var selectedTab by remember { mutableStateOf(GROUPS_TAB) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showAddServerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = coreNav::goBack
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                title = {
                    Text("MCP Settings")
                },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "McpSandboxGroups",
                        )
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectedTab == GROUPS_TAB) showAddGroupDialog = true
                    else showAddServerDialog = true
                },
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedTab == GROUPS_TAB) "Add Group" else "Add MCP Server")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == GROUPS_TAB,
                    onClick = { selectedTab = GROUPS_TAB },
                    text = { Text("Groups") }
                )
                Tab(
                    selected = selectedTab == SERVERS_TAB,
                    onClick = { selectedTab = SERVERS_TAB },
                    text = { Text("MCP Servers") }
                )
            }
            when (selectedTab) {
                GROUPS_TAB -> McpGroupsTab(
                    sandboxGroups = vm.sandboxGroups,
                    defaultGroupId = defaultGroupId,
                    onUpdateModelType = vm::updateModelType,
                    onDeleteGroup = vm::deleteGroup,
                    showAddGroupDialog = showAddGroupDialog,
                    onDismissAddGroupDialog = { showAddGroupDialog = false },
                    onCreateGroup = vm::createGroup
                )
                SERVERS_TAB -> McpServersTab(
                    serverEntries = vm.serverEntries,
                    allGroups = vm.sandboxGroups,
                    defaultGroupId = defaultGroupId,
                    showAddServerDialog = showAddServerDialog,
                    onDismissAddServerDialog = { showAddServerDialog = false },
                    loadGroupIds = vm::groupIdsForEntry,
                    onSaveHttpServer = vm::saveHttpServer,
                    onSetBuiltinGroups = vm::setBuiltinGroups,
                    onDeleteHttpServer = vm::deleteHttpServer
                )
            }
        }
    }
}

@Composable
fun McpGroupsTab(
    sandboxGroups: StateFlow<List<McpSandboxGroupEntity>>,
    defaultGroupId: Long,
    onUpdateModelType: (Long, SandboxModelType) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    showAddGroupDialog: Boolean,
    onDismissAddGroupDialog: () -> Unit,
    onCreateGroup: (String) -> Unit
) {
    val groups by sandboxGroups.collectAsState()
    var groupPendingDelete by remember { mutableStateOf<McpSandboxGroupEntity?>(null) }

    if (showAddGroupDialog) {
        AddGroupDialog(
            onDismiss = onDismissAddGroupDialog,
            onConfirm = { title ->
                onCreateGroup(title)
                onDismissAddGroupDialog()
            }
        )
    }

    groupPendingDelete?.let { group ->
        DeleteGroupDialog(
            group = group,
            onDismiss = { groupPendingDelete = null },
            onConfirm = {
                onDeleteGroup(group.id)
                groupPendingDelete = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        items(groups.size) { index ->
            val group = groups[index]
            McpSandboxGroupItem(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
                group = group,
                isDefault = group.id == defaultGroupId,
                onUpdateModelType = { newModelType ->
                    onUpdateModelType(group.id, newModelType)
                },
                onDelete = if (group.id == defaultGroupId) {
                    null
                } else {
                    { groupPendingDelete = group }
                }
            )
        }
    }
}

@Composable
private fun AddGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    M3Dialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Sandbox Group")
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank()
            ) {
                Text("Add")
            }
        }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeleteGroupDialog(
    group: McpSandboxGroupEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    M3Dialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Sandbox Group")
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        }
    ) {
        Text("Delete \"${group.title}\"? MCP servers themselves are kept and can be re-added to other groups.")
    }
}

@Composable
private fun modelTypeName(modelType: SandboxModelType): String {
    return when (modelType) {
        SandboxModelType.Default -> "Default"
        SandboxModelType.HighCapability -> "High Capability"
        SandboxModelType.IndexAgent -> "Index Agent"
    }
}

@Composable
private fun modelTypeDescription(modelType: SandboxModelType): String {
    return when (modelType) {
        SandboxModelType.Default ->
            "Fast at intent recognition and fine for most tasks"
        SandboxModelType.HighCapability ->
            "Slower, but better at comprehending large amounts of MCP tools and/or more complex tasks"
        SandboxModelType.IndexAgent ->
            "Offline-capable and specialised to understand notes and built-in actions, doesn't support customization"
    }
}

@Composable
fun McpSandboxGroupItem(
    modifier: Modifier = Modifier,
    group: McpSandboxGroupEntity,
    isDefault: Boolean,
    onUpdateModelType: (SandboxModelType) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    OutlinedCard(modifier) {
        Row {
            Column(
                modifier = Modifier.padding(16.dp).weight(1.0f)
            ) {
                Text(
                    text = group.title,
                    fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal,
                )
                // Dropdown selection for model type
                Spacer(modifier = Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    TextField(
                        value = modelTypeName(group.modelType),
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Model Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            enabled = isDefault,
                            text = {
                                Column {
                                    Text(modelTypeName(SandboxModelType.IndexAgent))
                                    Text(modelTypeDescription(SandboxModelType.IndexAgent), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(modelTypeName(SandboxModelType.Default))
                                    Text(modelTypeDescription(SandboxModelType.Default), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                onUpdateModelType(SandboxModelType.Default)
                                expanded = false
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(modelTypeName(SandboxModelType.HighCapability))
                                    Text(modelTypeDescription(SandboxModelType.HighCapability), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                onUpdateModelType(SandboxModelType.HighCapability)
                                expanded = false
                            }
                        )
                    }
                }
            }
            if (onDelete != null) {
                IconButton(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    onClick = onDelete,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Group")
                }
            }
        }
    }
}

@Preview
@Composable
fun McpGroupsTabPreview() {
    PreviewWrapper {
        McpGroupsTab(
            sandboxGroups = flowOf(
                listOf(
                    McpSandboxGroupEntity(id = 1, title = "Default Group", modelType = SandboxModelType.IndexAgent),
                    McpSandboxGroupEntity(id = 2, title = "Custom Group", modelType = SandboxModelType.HighCapability)
                )
            ).stateIn(
                scope = GlobalScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            ),
            defaultGroupId = 1L,
            onUpdateModelType = { _, _ -> },
            onDeleteGroup = { },
            showAddGroupDialog = false,
            onDismissAddGroupDialog = { },
            onCreateGroup = { }
        )
    }
}

@Preview
@Composable
private fun AddGroupDialogPreview() {
    PreviewWrapper {
        AddGroupDialog(
            onDismiss = {},
            onConfirm = {}
        )
    }
}
