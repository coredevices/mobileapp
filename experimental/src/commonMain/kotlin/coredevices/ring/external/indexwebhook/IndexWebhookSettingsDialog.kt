package coredevices.ring.external.indexwebhook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexWebhookSettingsDialog(
    viewModel: IndexWebhookSettingsViewModel = koinViewModel()
) {
    val urlInput by viewModel.urlInput.collectAsState()
    val tokenInput by viewModel.tokenInput.collectAsState()
    val payloadMode by viewModel.payloadModeInput.collectAsState()
    val isLinked = viewModel.isLinked

    BasicAlertDialog(
        onDismissRequest = viewModel::closeDialog,
    ) {
        Surface(
            modifier = Modifier.wrapContentWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Webhook Configuration",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Token field
                TextField(
                    value = tokenInput,
                    onValueChange = viewModel::updateTokenInput,
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("Auth Token") },
                    modifier = Modifier.fillMaxWidth()
                )
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

                Spacer(modifier = Modifier.height(24.dp))
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
