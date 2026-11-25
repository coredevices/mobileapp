package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_backup_encryption_key
import coreapp.ring.generated.resources.settings_encryption_confirm_saved
import coreapp.ring.generated.resources.settings_encryption_copy_key
import coreapp.ring.generated.resources.settings_encryption_generate_key
import coreapp.ring.generated.resources.settings_encryption_import_qr
import coreapp.ring.generated.resources.settings_encryption_key_backup_description
import coreapp.ring.generated.resources.settings_encryption_key_backup_title
import coreapp.ring.generated.resources.settings_encryption_not_backed_up_message
import coreapp.ring.generated.resources.settings_encryption_not_backed_up_title
import coreapp.ring.generated.resources.settings_encryption_not_backed_up_warning
import coreapp.ring.generated.resources.settings_encryption_restore
import coreapp.ring.generated.resources.settings_encryption_setup_generating
import coreapp.ring.generated.resources.settings_encryption_setup_paste
import coreapp.ring.generated.resources.settings_encryption_setup_prompt
import coreapp.ring.generated.resources.settings_encryption_setup_qr_saved
import coreapp.ring.generated.resources.settings_encryption_setup_restoring
import coreapp.ring.generated.resources.settings_encryption_setup_save_safe
import coreapp.ring.generated.resources.settings_encryption_setup_saved
import coreapp.ring.generated.resources.settings_encryption_setup_title
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.done
import coreapp.util.generated.resources.retry
import coredevices.ring.encryption.EncryptionSetupState
import coredevices.ring.ui.components.QrCodeImage
import coredevices.ring.ui.viewmodel.EncryptionViewModel
import coredevices.ui.dismissKeyboardOnTapOutside
import coredevices.util.rememberUiContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import coreapp.util.generated.resources.Res as UtilRes

/**
 * The "key not backed up" warning and generated-key QR/backup sheet,
 * driven by [EncryptionViewModel] so any host of the key flow can render them.
 */
@Composable
fun EncryptionKeyResultDialogs(viewModel: EncryptionViewModel = koinViewModel()) {
    val showKeyNotBackedUpDialog by viewModel.showKeyNotBackedUpDialog.collectAsState()
    val generatedKey by viewModel.generatedKey.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    if (showKeyNotBackedUpDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissKeyNotBackedUpDialog() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text(stringResource(Res.string.settings_encryption_not_backed_up_title)) },
            text = {
                Text(
                    buildAnnotatedString {
                        append(stringResource(Res.string.settings_encryption_not_backed_up_message))
                        append("\n\n")
                        withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                            append(stringResource(Res.string.settings_encryption_not_backed_up_warning))
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmEnableEncryption() }) {
                    Text(stringResource(Res.string.settings_encryption_confirm_saved))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissKeyNotBackedUpDialog() }) {
                    Text(stringResource(UtilRes.string.cancel))
                }
            }
        )
    }
    if (generatedKey != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearGeneratedKey() },
            title = { Text(stringResource(Res.string.settings_encryption_key_backup_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(Res.string.settings_encryption_key_backup_description),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeImage(
                            data = generatedKey!!,
                            size = 220.dp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(generatedKey!!))
                }) {
                    Text(stringResource(Res.string.settings_encryption_copy_key))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearGeneratedKey() }) {
                    Text(stringResource(UtilRes.string.done))
                }
            }
        )
    }
}

/**
 * Guided "turn on encryption" dialog, driven by
 * [EncryptionViewModel.encryptionSetup] through generate or restore before
 * encryption is enabled, so it's reusable wherever the switch lives.
 */
@Composable
fun EncryptionSetupDialog(viewModel: EncryptionViewModel = koinViewModel()) {
    val current by viewModel.encryptionSetup.collectAsState()
    if (current is EncryptionSetupState.Hidden) return

    val uiContext = rememberUiContext()
    val clipboardManager = LocalClipboardManager.current
    var pasted by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { viewModel.cancelEncryptionSetup() },
        modifier = Modifier.dismissKeyboardOnTapOutside(),
        title = { Text(stringResource(Res.string.settings_encryption_setup_title)) },
        text = {
            when (val s = current) {
                EncryptionSetupState.Hidden -> {}
                EncryptionSetupState.PromptGenerate -> Text(stringResource(Res.string.settings_encryption_setup_prompt))

                EncryptionSetupState.Generating -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.settings_encryption_setup_generating))
                }

                is EncryptionSetupState.ShowKey -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        listOfNotNull(
                            stringResource(Res.string.settings_encryption_setup_saved),
                            stringResource(Res.string.settings_encryption_setup_qr_saved).takeIf { s.qrSavedToPhotos },
                            stringResource(Res.string.settings_encryption_setup_save_safe),
                        ).joinToString(" "),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        QrCodeImage(data = s.keyBase64, size = 220.dp)
                    }
                }

                EncryptionSetupState.Restoring -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.settings_encryption_setup_restoring))
                }

                is EncryptionSetupState.PasteKey -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val focusManager = LocalFocusManager.current
                    Text(stringResource(Res.string.settings_encryption_setup_paste))
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.settings_backup_encryption_key)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = uiContext != null,
                        onClick = { uiContext?.let { viewModel.restoreFromQrPhoto(it) } }
                    ) { Text(stringResource(Res.string.settings_encryption_import_qr)) }
                    s.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                is EncryptionSetupState.Failed -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            when (val s = current) {
                EncryptionSetupState.PromptGenerate -> TextButton(
                    enabled = uiContext != null,
                    onClick = { uiContext?.let { viewModel.generateKeyForSetup(it) } }
                ) { Text(stringResource(Res.string.settings_encryption_generate_key)) }

                is EncryptionSetupState.ShowKey -> TextButton(
                    onClick = { viewModel.completeEncryptionSetup() }
                ) { Text(stringResource(UtilRes.string.done)) }

                is EncryptionSetupState.PasteKey -> TextButton(
                    enabled = pasted.isNotBlank(),
                    onClick = { viewModel.restoreFromPastedKey(pasted) }
                ) { Text(stringResource(Res.string.settings_encryption_restore)) }

                is EncryptionSetupState.Failed -> TextButton(
                    enabled = uiContext != null,
                    onClick = { uiContext?.let { viewModel.generateKeyForSetup(it) } }
                ) { Text(stringResource(UtilRes.string.retry)) }

                else -> {}
            }
        },
        dismissButton = {
            when (val s = current) {
                is EncryptionSetupState.ShowKey -> TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(s.keyBase64)) }
                ) { Text(stringResource(Res.string.settings_encryption_copy_key)) }

                EncryptionSetupState.Generating,
                EncryptionSetupState.Restoring,
                EncryptionSetupState.Hidden -> {}

                else -> TextButton(
                    onClick = { viewModel.cancelEncryptionSetup() }
                ) { Text(stringResource(UtilRes.string.cancel)) }
            }
        }
    )
}
