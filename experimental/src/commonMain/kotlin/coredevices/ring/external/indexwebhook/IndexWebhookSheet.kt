package coredevices.ring.external.indexwebhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coredevices.ring.service.button.RingGesture
import coredevices.ring.ui.relativeTime
import coredevices.ring.ui.theme.IndexTheme
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexWebhookSheet(
    viewModel: IndexWebhookSettingsViewModel = koinViewModel()
) {
    val gesture by viewModel.gesture.collectAsState()
    val openFor = gesture ?: return
    val urlInput by viewModel.urlInput.collectAsState()
    val headerInputs by viewModel.headerInputs.collectAsState()
    val payloadMode by viewModel.payloadModeInput.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val copyable by viewModel.copyableGesture.collectAsState()
    val canRemove by viewModel.canRemove.collectAsState()
    val runs by viewModel.runs.collectAsState()
    val focusManager = LocalFocusManager.current
    val dismissKeyboard = KeyboardActions(onDone = { focusManager.clearFocus() })
    val colors = IndexTheme.colors

    ModalBottomSheet(
        onDismissRequest = viewModel::closeDialog,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheetSurface,
        // Text here is styled but uncoloured, so without this it inherits the app's M3
        // content colour rather than the Index palette the sheet is painted with.
        contentColor = colors.onSurface,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        ) {
            Icon(
                Icons.Default.Webhook,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Webhook · ${openFor.gestureLabel()}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            copyable?.let { other ->
                OutlinedButton(
                    onClick = viewModel::copyFromOtherGesture,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Copy from ${other.gestureLabel()}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Webhook URL")
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = viewModel::updateUrlInput,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text("https://example.com/webhook") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = dismissKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("What to send")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    IndexWebhookPayloadMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == payloadMode,
                            onClick = { viewModel.updatePayloadMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = IndexWebhookPayloadMode.entries.size,
                            ),
                        ) {
                            Text(mode.segmentLabel())
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Headers")
                headerInputs.forEachIndexed { index, header ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = header.name,
                            onValueChange = { viewModel.updateHeaderName(index, it) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text("Name", style = MaterialTheme.typography.bodyMedium) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = header.value,
                            onValueChange = { viewModel.updateHeaderValue(index, it) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text("Value", style = MaterialTheme.typography.bodyMedium) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeHeader(index) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove header",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = viewModel::addHeader,
                    contentPadding = PaddingValues(4.dp),
                ) {
                    Text("Add header", style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::sendTestEvent,
                    enabled = urlInput.isNotBlank() && testState != WebhookTestState.Sending,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Send test event", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = when (val state = testState) {
                        WebhookTestState.Idle -> ""
                        WebhookTestState.Sending -> "Sending..."
                        is WebhookTestState.Done -> state.label
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if ((testState as? WebhookTestState.Done)?.ok == false) {
                        colors.error
                    } else {
                        colors.onSurfaceVariant
                    },
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = urlInput.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Save")
            }

            if (canRemove) {
                TextButton(
                    onClick = viewModel::removeCurrentGesture,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove webhook", color = colors.error)
                }
            }

            Column {
                SectionLabel("Recent runs", Modifier.padding(bottom = 4.dp))
                if (runs.isEmpty()) {
                    Text(
                        "No runs yet. They'll show up here after your first recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.outline,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                runs.forEach { run ->
                    RunRow(run)
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: IndexWebhookRun) {
    val colors = IndexTheme.colors
    val color =
        if (run.ok) colors.onSurfaceVariant else colors.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
    ) {
        Icon(
            if (run.ok) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            run.status,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1,
            modifier = Modifier.width(118.dp),
        )
        Text(
            run.detailLine(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            relativeTime(run.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.outline,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = IndexTheme.colors.onSurfaceVariant,
        modifier = modifier,
    )
}

private fun IndexWebhookRun.detailLine(): String =
    if (ok) "$detail · ${formatBytes(byteSize)} · $durationMs ms" else detail

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

private fun IndexWebhookPayloadMode.segmentLabel(): String = when (this) {
    IndexWebhookPayloadMode.RecordingOnly -> "Recording"
    IndexWebhookPayloadMode.TranscriptionOnly -> "Transcription"
    IndexWebhookPayloadMode.Both -> "Both"
}

private fun RingGesture.gestureLabel(): String = when (this) {
    RingGesture.ClickHold -> "Double click & hold"
    else -> "Hold & Talk"
}
