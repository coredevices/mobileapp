package coredevices.ring.ui.screens.settings.clickactions

import CoreNav
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.ring.data.entity.room.ClickAction
import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ring.service.CatalogTool
import coredevices.ui.M3Dialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ClickActions(coreNav: CoreNav) {
    val viewModel = koinViewModel<ClickActionsViewModel>()
    val bindings by viewModel.bindings.collectAsState()
    val reserved by viewModel.reservedClickCounts.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val error by viewModel.error.collectAsState()

    var editing by remember { mutableStateOf<ClickActionBinding?>(null) }
    var pendingDelete by remember { mutableStateOf<ClickActionBinding?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom click actions") },
                navigationIcon = {
                    IconButton(onClick = { coreNav.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            val free = clickCountOptions(bindings, reserved, editingId = 0L).firstSelectable()
            if (free != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editing = ClickActionBinding(
                            clickCount = free,
                            action = ClickAction.AgentText(""),
                        )
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Short clicks on Index 01 that don't start a recording. Counts already used " +
                        "by media control are locked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (bindings.isEmpty()) {
                item {
                    Text(
                        "No click actions yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(bindings, key = { it.id }) { binding ->
                BindingCard(
                    binding = binding,
                    shadowedByMedia = binding.clickCount in reserved,
                    onClick = { editing = binding },
                    onDelete = { pendingDelete = binding },
                )
            }
        }
    }

    editing?.let { target ->
        LaunchedEffect(target.id) { viewModel.loadTools() }
        ClickActionEditorDialog(
            binding = target,
            clickCountOptions = clickCountOptions(bindings, reserved, editingId = target.id),
            catalog = catalog,
            onRetryTools = { viewModel.loadTools(force = true) },
            onDismiss = { editing = null },
            onSave = { updated -> viewModel.save(updated) { editing = null } },
        )
    }

    pendingDelete?.let { target ->
        M3Dialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove click action") },
            buttons = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    pendingDelete = null
                }) { Text("Remove") }
            },
        ) {
            Text("${target.clickCount} clicks will no longer do anything.")
        }
    }

    error?.let { message ->
        M3Dialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Couldn't save") },
            buttons = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
        ) {
            Text(message)
        }
    }
}

@Composable
private fun BindingCard(
    binding: ClickActionBinding,
    shadowedByMedia: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${binding.clickCount} clicks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    binding.action.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!binding.enabled) {
                    Text(
                        "Disabled",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (shadowedByMedia) {
                    Text(
                        "Won't fire — media control uses this many clicks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}

private fun ClickAction.summary(): String = when (this) {
    is ClickAction.AgentText -> "Ask the agent · \"$text\""
    is ClickAction.ToolCall -> "Run tool · $integrationName/$toolName"
    ClickAction.Unsupported -> "Unreadable — open to replace it"
}

private enum class ActionKind { Agent, Tool }

private fun ClickAction.kind(): ActionKind = when (this) {
    is ClickAction.AgentText -> ActionKind.Agent
    is ClickAction.ToolCall -> ActionKind.Tool
    // Nothing to restore, so open on the simplest form and make the user re-pick.
    ClickAction.Unsupported -> ActionKind.Agent
}

@Composable
private fun ClickActionEditorDialog(
    binding: ClickActionBinding,
    clickCountOptions: List<ClickCountOption>,
    catalog: ToolCatalogState,
    onRetryTools: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ClickActionBinding) -> Unit,
) {
    var clickCount by remember(binding.id) { mutableStateOf(binding.clickCount) }
    var enabled by remember(binding.id) { mutableStateOf(binding.enabled) }
    var kind by remember(binding.id) { mutableStateOf(binding.action.kind()) }
    var agentText by remember(binding.id) {
        mutableStateOf((binding.action as? ClickAction.AgentText)?.text.orEmpty())
    }
    var selectedTool by remember(binding.id) { mutableStateOf<CatalogTool?>(null) }
    var paramValues by remember(binding.id) { mutableStateOf(emptyMap<String, String>()) }
    var showToolPicker by remember { mutableStateOf(false) }

    // Re-attach a saved tool to its live schema once the catalog arrives, so an existing
    // binding opens with its arguments filled in rather than blank.
    val existingToolCall = binding.action as? ClickAction.ToolCall
    LaunchedEffect(catalog, binding.id) {
        if (existingToolCall == null || selectedTool != null) return@LaunchedEffect
        val tools = (catalog as? ToolCatalogState.Loaded)?.tools ?: return@LaunchedEffect
        val match = tools.firstOrNull {
            it.integrationName == existingToolCall.integrationName &&
                it.toolName == existingToolCall.toolName
        } ?: return@LaunchedEffect
        selectedTool = match
        paramValues = existingToolCall.arguments.toFormValues(match.definition.parameterFields())
    }

    val fields = selectedTool?.definition?.parameterFields().orEmpty()
    val missing = missingRequired(fields, paramValues)
    val badJson = invalidJsonFields(fields, paramValues)
    val canSave = when (kind) {
        ActionKind.Agent -> agentText.isNotBlank()
        ActionKind.Tool -> selectedTool != null && missing.isEmpty() && badJson.isEmpty()
    } && clickCountOptions.any { it.count == clickCount && it.selectable }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text(if (binding.id == 0L) "New click action" else "Edit click action") },
        scrollableContent = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = canSave,
                onClick = {
                    val action = when (kind) {
                        ActionKind.Agent -> ClickAction.AgentText(agentText.trim())
                        ActionKind.Tool -> selectedTool!!.let {
                            ClickAction.ToolCall(
                                integrationName = it.integrationName,
                                toolName = it.toolName,
                                arguments = buildArguments(fields, paramValues),
                            )
                        }
                    }
                    onSave(binding.copy(clickCount = clickCount, action = action, enabled = enabled))
                },
            ) { Text("Save") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Clicks")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                clickCountOptions.forEach { option ->
                    FilterChip(
                        selected = option.count == clickCount,
                        enabled = option.selectable,
                        onClick = { clickCount = option.count },
                        label = { Text("${option.count}") },
                        leadingIcon = if (option.selectable) null else {
                            { Icon(Icons.Default.Lock, contentDescription = "Unavailable") }
                        },
                    )
                }
            }
            ClickCountLegend(clickCountOptions)

            HorizontalDivider()
            SectionLabel("Action")
            ActionKind.entries.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { kind = option },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = kind == option, onClick = { kind = option })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (option) {
                            ActionKind.Agent -> "Ask the agent"
                            ActionKind.Tool -> "Run a tool"
                        }
                    )
                }
            }

            when (kind) {
                ActionKind.Agent -> {
                    OutlinedTextField(
                        value = agentText,
                        onValueChange = { agentText = it },
                        label = { Text("What to send") },
                        supportingText = { Text("Ending with '?' routes it to search.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ActionKind.Tool -> {
                    ToolSelector(
                        catalog = catalog,
                        selected = selectedTool,
                        onPick = { showToolPicker = true },
                        onRetry = onRetryTools,
                    )
                    if (existingToolCall != null && selectedTool == null &&
                        catalog is ToolCatalogState.Loaded
                    ) {
                        Text(
                            "${existingToolCall.integrationName}/${existingToolCall.toolName} " +
                                "is no longer available. Pick another tool — the saved " +
                                "parameters can't be kept.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (fields.isNotEmpty()) {
                        SectionLabel("Parameters")
                        fields.forEach { field ->
                            ToolParameterInput(
                                field = field,
                                value = paramValues[field.name].orEmpty(),
                                isInvalidJson = field.name in badJson,
                                onValueChange = { paramValues = paramValues + (field.name to it) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }
    }

    if (showToolPicker && catalog is ToolCatalogState.Loaded) {
        ToolPickerDialog(
            tools = catalog.tools,
            onDismiss = { showToolPicker = false },
            onSelect = { tool ->
                selectedTool = tool
                paramValues = emptyMap()
                showToolPicker = false
            },
        )
    }
}

/** Explains any locked counts, so a missing option never looks like a bug. */
@Composable
private fun ClickCountLegend(options: List<ClickCountOption>) {
    val byMedia = options.filter { it.availability == ClickCountAvailability.UsedByMediaControl }
    val byAction = options.filter { it.availability == ClickCountAvailability.UsedByAnotherAction }
    if (byMedia.isEmpty() && byAction.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (byMedia.isNotEmpty()) {
            Text(
                "${byMedia.joinToString(", ") { it.count.toString() }} used by media control. " +
                    "Change Music play/pause in Index settings to free them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (byAction.isNotEmpty()) {
            Text(
                "${byAction.joinToString(", ") { it.count.toString() }} already bound to another action.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToolSelector(
    catalog: ToolCatalogState,
    selected: CatalogTool?,
    onPick: () -> Unit,
    onRetry: () -> Unit,
) {
    when (catalog) {
        ToolCatalogState.Idle, ToolCatalogState.Loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Loading tools…", style = MaterialTheme.typography.bodyMedium)
        }
        is ToolCatalogState.Failed -> Column {
            Text(
                "Couldn't load tools: ${catalog.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        is ToolCatalogState.Loaded -> OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPick),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    selected?.toolName ?: "Select a tool",
                    style = MaterialTheme.typography.titleSmall,
                )
                selected?.let {
                    Text(
                        it.integrationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolPickerDialog(
    tools: List<CatalogTool>,
    onDismiss: () -> Unit,
    onSelect: (CatalogTool) -> Unit,
) {
    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a tool") },
        scrollableContent = true,
        buttons = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column {
            if (tools.isEmpty()) {
                Text("No tools available. Add an MCP server in Index settings.")
            }
            tools.groupBy { it.integrationName }.forEach { (integration, integrationTools) ->
                Text(
                    integration.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                integrationTools.forEach { tool ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tool) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(tool.toolName, style = MaterialTheme.typography.bodyLarge)
                        tool.definition.description?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolParameterInput(
    field: ToolParameterField,
    value: String,
    isInvalidJson: Boolean,
    onValueChange: (String) -> Unit,
) {
    val label = if (field.required) "${field.name} *" else field.name
    when (val kind = field.kind) {
        is ToolParameterField.Kind.Bool -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label)
                field.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = value.equals("true", ignoreCase = true),
                onCheckedChange = { onValueChange(it.toString()) },
            )
        }
        is ToolParameterField.Kind.Choice -> Column {
            Text(label)
            field.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kind.options.forEach { option ->
                    FilterChip(
                        selected = value == option,
                        onClick = { onValueChange(option) },
                        label = { Text(option) },
                    )
                }
            }
        }
        else -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isInvalidJson,
            singleLine = kind !is ToolParameterField.Kind.Json,
            keyboardOptions = if (kind is ToolParameterField.Kind.Number) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions.Default
            },
            supportingText = {
                Text(
                    when {
                        isInvalidJson -> "Not valid JSON"
                        kind is ToolParameterField.Kind.Json -> field.description ?: "JSON value"
                        else -> field.description.orEmpty()
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
