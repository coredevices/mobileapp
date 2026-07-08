package coredevices.ring.external.indexwebhook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import coredevices.ui.dismissKeyboardOnTapOutside
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexWebhookSettingsDialog(
    viewModel: IndexWebhookSettingsViewModel = koinViewModel()
) {
    val urlInput by viewModel.urlInput.collectAsState()
    val headerInputs by viewModel.headerInputs.collectAsState()
    val payloadMode by viewModel.payloadModeInput.collectAsState()
    val trigger by viewModel.triggerInput.collectAsState()
    val isLinked = viewModel.isLinked

    BasicAlertDialog(
        onDismissRequest = viewModel::closeDialog,
    ) {
        Surface(
            // Cap the height so a long list of headers stays on screen; the
            // title and action buttons stay fixed while the body scrolls.
            modifier = Modifier.wrapContentWidth().heightIn(max = 600.dp)
                .dismissKeyboardOnTapOutside(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            // Resolved inside the dialog: dialogs have their own focus manager.
            val focusManager = LocalFocusManager.current
            val dismissKeyboard = KeyboardActions(onDone = { focusManager.clearFocus() })
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Webhook Configuration",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable body so the dialog never grows past its max height.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                Text(
                    "Send Index recording data to an HTTP endpoint on each recording.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                // URL field
                TextField(
                    value = urlInput,
                    onValueChange = viewModel::updateUrlInput,
                    singleLine = true,
                    label = { Text("Webhook URL") },
                    placeholder = { Text("https://example.com/webhook") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = dismissKeyboard,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Headers editor
                Text(
                    "Headers",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                headerInputs.forEachIndexed { index, headerEntry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = headerEntry.name,
                            onValueChange = { viewModel.updateHeaderName(index, it) },
                            singleLine = true,
                            label = { Text("Name") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = headerEntry.value,
                            onValueChange = { viewModel.updateHeaderValue(index, it) },
                            singleLine = true,
                            label = { Text("Value") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeHeader(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove header")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TextButton(onClick = viewModel::addHeader) {
                    Text("Add header")
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Payload mode dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    TextField(
                        value = payloadMode.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Send") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        IndexWebhookPayloadMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.displayName()) },
                                onClick = {
                                    viewModel.updatePayloadMode(mode)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trigger selector
                Text(
                    "Trigger",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                IndexWebhookTrigger.entries.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateTrigger(t) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = t == trigger,
                            onClick = { viewModel.updateTrigger(t) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(t.title())
                            Text(
                                t.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                } // end scrollable body

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    if (isLinked) {
                        TextButton(onClick = viewModel::clearAll) {
                            Text("Unlink")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = viewModel::closeDialog) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = viewModel::save) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun IndexWebhookPayloadMode.displayName(): String = when (this) {
    IndexWebhookPayloadMode.RecordingOnly -> "Recording only"
    IndexWebhookPayloadMode.TranscriptionOnly -> "Transcription only"
    IndexWebhookPayloadMode.Both -> "Recording + Transcription"
}

private fun IndexWebhookTrigger.title(): String = when (this) {
    IndexWebhookTrigger.SingleClick -> "Single click & hold"
    IndexWebhookTrigger.DoubleClickHold -> "Double click & hold"
    IndexWebhookTrigger.Both -> "Both"
}

private fun IndexWebhookTrigger.description(): String = when (this) {
    IndexWebhookTrigger.SingleClick ->
        "Send only on single click & hold."
    IndexWebhookTrigger.DoubleClickHold ->
        "Send only on double click & hold."
    IndexWebhookTrigger.Both ->
        "Send on every recording."
}
