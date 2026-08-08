package coredevices.ring.ui.screens.settings.clickactions

import CoreNav
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.ring.data.entity.room.ClickAction
import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ui.M3Dialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ClickActions(coreNav: CoreNav) {
    val viewModel = koinViewModel<ClickActionsViewModel>()
    val bindings by viewModel.bindings.collectAsState()
    val reserved by viewModel.reservedClickCounts.collectAsState()
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
        ClickActionEditorDialog(
            binding = target,
            clickCountOptions = clickCountOptions(bindings, reserved, editingId = target.id),
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
    ClickAction.Unsupported -> "Unreadable — open to replace it"
}

@Composable
private fun ClickActionEditorDialog(
    binding: ClickActionBinding,
    clickCountOptions: List<ClickCountOption>,
    onDismiss: () -> Unit,
    onSave: (ClickActionBinding) -> Unit,
) {
    var clickCount by remember(binding.id) { mutableStateOf(binding.clickCount) }
    var enabled by remember(binding.id) { mutableStateOf(binding.enabled) }
    var agentText by remember(binding.id) {
        mutableStateOf((binding.action as? ClickAction.AgentText)?.text.orEmpty())
    }

    val canSave = agentText.isNotBlank() &&
        clickCountOptions.any { it.count == clickCount && it.selectable }

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
                    onSave(
                        binding.copy(
                            clickCount = clickCount,
                            action = ClickAction.AgentText(agentText.trim()),
                            enabled = enabled,
                        )
                    )
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
            OutlinedTextField(
                value = agentText,
                onValueChange = { agentText = it },
                label = { Text("What to send") },
                supportingText = { Text("Ending with '?' routes it to search.") },
                modifier = Modifier.fillMaxWidth(),
            )

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
