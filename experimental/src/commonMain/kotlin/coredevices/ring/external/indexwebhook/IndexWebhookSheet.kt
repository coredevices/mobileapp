package coredevices.ring.external.indexwebhook

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.webhook_sheet_add_header
import coreapp.ring.generated.resources.webhook_sheet_copy_from
import coreapp.ring.generated.resources.webhook_sheet_header_value
import coreapp.ring.generated.resources.webhook_sheet_headers
import coreapp.ring.generated.resources.webhook_sheet_hide_secret
import coreapp.ring.generated.resources.webhook_sheet_mode_both
import coreapp.ring.generated.resources.webhook_sheet_mode_recording
import coreapp.ring.generated.resources.webhook_sheet_mode_transcription
import coreapp.ring.generated.resources.webhook_sheet_no_runs_click
import coreapp.ring.generated.resources.webhook_sheet_no_runs_recording
import coreapp.ring.generated.resources.webhook_sheet_paste_secret
import coreapp.ring.generated.resources.webhook_sheet_payload
import coreapp.ring.generated.resources.webhook_sheet_payload_hint
import coreapp.ring.generated.resources.webhook_sheet_recent_runs
import coreapp.ring.generated.resources.webhook_sheet_remove
import coreapp.ring.generated.resources.webhook_sheet_remove_header
import coreapp.ring.generated.resources.webhook_sheet_saving
import coreapp.ring.generated.resources.webhook_sheet_secret_hint
import coreapp.ring.generated.resources.webhook_sheet_secret_required
import coreapp.ring.generated.resources.webhook_sheet_security
import coreapp.ring.generated.resources.webhook_sheet_send_json
import coreapp.ring.generated.resources.webhook_sheet_send_json_hint
import coreapp.ring.generated.resources.webhook_sheet_send_test
import coreapp.ring.generated.resources.webhook_sheet_sending
import coreapp.ring.generated.resources.webhook_sheet_show_secret
import coreapp.ring.generated.resources.webhook_sheet_sign_requests
import coreapp.ring.generated.resources.webhook_sheet_sign_requests_hint
import coreapp.ring.generated.resources.webhook_sheet_signing_secret
import coreapp.ring.generated.resources.webhook_sheet_title
import coreapp.ring.generated.resources.webhook_sheet_url
import coreapp.ring.generated.resources.webhook_sheet_what_to_send
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.loading
import coreapp.util.generated.resources.name
import coreapp.util.generated.resources.retry
import coreapp.util.generated.resources.save
import coredevices.ring.ui.screens.settings.gestureLabelRes
import coredevices.util.relativeTime
import coredevices.ring.ui.theme.IndexTheme
import org.jetbrains.compose.resources.stringResource
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
    val clickPayload by viewModel.clickPayloadInput.collectAsState()
    val sendJson by viewModel.sendJsonInput.collectAsState()
    val signRequests by viewModel.signRequestsInput.collectAsState()
    val signingSecret by viewModel.signingSecretInput.collectAsState()
    val signingSecretLoading by viewModel.signingSecretLoading.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val copyable by viewModel.copyableGesture.collectAsState()
    val canRemove by viewModel.canRemove.collectAsState()
    val runs by viewModel.runs.collectAsState()
    val focusManager = LocalFocusManager.current
    val dismissKeyboard = KeyboardActions(onDone = { focusManager.clearFocus() })
    val colors = IndexTheme.colors
    var revealSigningSecret by remember(openFor) { mutableStateOf(false) }
    val signingReady = !signRequests || signingSecret.isNotBlank()
    val isClick = openFor.isClick

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
                stringResource(Res.string.webhook_sheet_title, stringResource(openFor.gestureLabelRes)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            copyable?.let { other ->
                OutlinedButton(
                    onClick = viewModel::copyFromOtherGesture,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(Res.string.webhook_sheet_copy_from, stringResource(other.gestureLabelRes)), style = MaterialTheme.typography.labelMedium)
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
                SectionLabel(stringResource(Res.string.webhook_sheet_url))
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

            if (isClick) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringResource(Res.string.webhook_sheet_payload))
                OutlinedTextField(
                    value = clickPayload,
                    onValueChange = viewModel::updateClickPayload,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("""{"entity_id": "light.desk"}""") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.webhook_sheet_payload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(Res.string.webhook_sheet_what_to_send))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.webhook_sheet_send_json), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.webhook_sheet_send_json_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Switch(checked = sendJson, onCheckedChange = viewModel::updateSendJson)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(Res.string.webhook_sheet_headers))
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
                            placeholder = { Text(stringResource(UtilRes.string.name), style = MaterialTheme.typography.bodyMedium) },
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
                            placeholder = { Text(stringResource(Res.string.webhook_sheet_header_value), style = MaterialTheme.typography.bodyMedium) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeHeader(index) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.webhook_sheet_remove_header),
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
                    Text(stringResource(Res.string.webhook_sheet_add_header), style = MaterialTheme.typography.labelLarge)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(Res.string.webhook_sheet_security))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.webhook_sheet_sign_requests), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.webhook_sheet_sign_requests_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = signRequests,
                        onCheckedChange = viewModel::updateSignRequests,
                        enabled = !signingSecretLoading,
                    )
                }
                if (signRequests) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(stringResource(Res.string.webhook_sheet_signing_secret))
                        OutlinedTextField(
                            value = signingSecret,
                            onValueChange = viewModel::updateSigningSecret,
                            enabled = !signingSecretLoading,
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            placeholder = {
                                Text(if (signingSecretLoading) stringResource(UtilRes.string.loading) else stringResource(Res.string.webhook_sheet_paste_secret))
                            },
                            isError = !signingSecretLoading && signingSecret.isBlank(),
                            visualTransformation = if (revealSigningSecret) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { revealSigningSecret = !revealSigningSecret },
                                    enabled = !signingSecretLoading,
                                ) {
                                    Icon(
                                        if (revealSigningSecret) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = if (revealSigningSecret) {
                                            stringResource(Res.string.webhook_sheet_hide_secret)
                                        } else {
                                            stringResource(Res.string.webhook_sheet_show_secret)
                                        },
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = dismissKeyboard,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (!signingSecretLoading && signingSecret.isBlank()) {
                                stringResource(Res.string.webhook_sheet_secret_required)
                            } else {
                                stringResource(Res.string.webhook_sheet_secret_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!signingSecretLoading && signingSecret.isBlank()) {
                                colors.error
                            } else {
                                colors.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::sendTestEvent,
                    enabled = urlInput.isNotBlank() && signingReady && !signingSecretLoading &&
                        testState != WebhookTestState.Sending,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.webhook_sheet_send_test), style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = when (val state = testState) {
                        WebhookTestState.Idle -> ""
                        WebhookTestState.Sending -> stringResource(Res.string.webhook_sheet_sending)
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
                enabled = urlInput.isNotBlank() && signingReady && !signingSecretLoading && !saving,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (saving) stringResource(Res.string.webhook_sheet_saving) else stringResource(UtilRes.string.save))
            }

            if (canRemove) {
                TextButton(
                    onClick = viewModel::removeCurrentGesture,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.webhook_sheet_remove), color = colors.error)
                }
            }

            Column {
                SectionLabel(stringResource(Res.string.webhook_sheet_recent_runs), Modifier.padding(bottom = 4.dp))
                if (runs.isEmpty()) {
                    Text(
                        if (isClick) stringResource(Res.string.webhook_sheet_no_runs_click)
                        else stringResource(Res.string.webhook_sheet_no_runs_recording),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.outline,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                runs.forEach { run ->
                    RunRow(run, onRetry = { viewModel.retry(run) })
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: IndexWebhookRun, onRetry: () -> Unit) {
    val colors = IndexTheme.colors
    val color =
        if (run.ok) colors.onSurfaceVariant else colors.error
    var expanded by remember(run) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !run.ok) { expanded = !expanded }
            .padding(vertical = 7.dp),
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
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            relativeTime(run.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.outline,
            maxLines = 1,
        )
        if (run.retryDeliveryId != null) {
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(stringResource(UtilRes.string.retry))
            }
        }
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

@Composable
private fun IndexWebhookPayloadMode.segmentLabel(): String = when (this) {
    IndexWebhookPayloadMode.RecordingOnly -> stringResource(Res.string.webhook_sheet_mode_recording)
    IndexWebhookPayloadMode.TranscriptionOnly -> stringResource(Res.string.webhook_sheet_mode_transcription)
    IndexWebhookPayloadMode.Both -> stringResource(Res.string.webhook_sheet_mode_both)
}
