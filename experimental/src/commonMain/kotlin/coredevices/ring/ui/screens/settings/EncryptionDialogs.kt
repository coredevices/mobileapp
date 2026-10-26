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
import coredevices.ring.encryption.EncryptionSetupState
import coredevices.ring.ui.components.QrCodeImage
import coredevices.ring.ui.viewmodel.EncryptionViewModel
import coredevices.ui.dismissKeyboardOnTapOutside
import coredevices.util.rememberUiContext
import org.koin.compose.viewmodel.koinViewModel

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
            title = { Text("Encryption key not backed up") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("You're attempting to enable backup encryption without a confirmed keychain backup of your key, ")
                        append("please ensure you've saved your key either via QR code or copied it to your clipboard and saved it elsewhere.\n\n")
                        withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                            append("If you lose this key, you will not be able to sync or restore anything from backups and will have to start fresh.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmEnableEncryption() }) {
                    Text("I confirm, my key is saved elsewhere")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissKeyNotBackedUpDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (generatedKey != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearGeneratedKey() },
            title = { Text("Encryption Key Backup") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Save this key securely. You will need it to decrypt your backups.",
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
                    Text("Copy Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearGeneratedKey() }) {
                    Text("Done")
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
        title = { Text("Set up backup encryption") },
        text = {
            when (val s = current) {
                EncryptionSetupState.Hidden -> {}
                EncryptionSetupState.PromptGenerate -> Text(
                    "Generate an encryption key so only you can read your backups. " +
                        "You'll save a copy so you can restore on another device."
                )

                EncryptionSetupState.Generating -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Generating your key and saving it to your password manager…")
                }

                is EncryptionSetupState.ShowKey -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        buildString {
                            append("Saved to your password manager. ")
                            if (s.qrSavedToPhotos) {
                                append("A QR code of your key was also saved to your photos. ")
                            }
                            append(
                                "Save this key somewhere safe too — you'll need it to " +
                                    "restore your backups and it can't be recovered."
                            )
                        },
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
                    Text("Looking for your key in your password manager…")
                }

                is EncryptionSetupState.PasteKey -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val focusManager = LocalFocusManager.current
                    Text(
                        "Your key was generated on another device and isn't in this " +
                            "password manager. Paste your backup key, or import the " +
                            "QR code saved in your photos."
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = true,
                        label = { Text("Encryption key") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = uiContext != null,
                        onClick = { uiContext?.let { viewModel.restoreFromQrPhoto(it) } }
                    ) { Text("Import QR code from photos") }
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
                ) { Text("Generate Key") }

                is EncryptionSetupState.ShowKey -> TextButton(
                    onClick = { viewModel.completeEncryptionSetup() }
                ) { Text("Done") }

                is EncryptionSetupState.PasteKey -> TextButton(
                    enabled = pasted.isNotBlank(),
                    onClick = { viewModel.restoreFromPastedKey(pasted) }
                ) { Text("Restore") }

                is EncryptionSetupState.Failed -> TextButton(
                    enabled = uiContext != null,
                    onClick = { uiContext?.let { viewModel.generateKeyForSetup(it) } }
                ) { Text("Retry") }

                else -> {}
            }
        },
        dismissButton = {
            when (val s = current) {
                is EncryptionSetupState.ShowKey -> TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(s.keyBase64)) }
                ) { Text("Copy Key") }

                EncryptionSetupState.Generating,
                EncryptionSetupState.Restoring,
                EncryptionSetupState.Hidden -> {}

                else -> TextButton(
                    onClick = { viewModel.cancelEncryptionSetup() }
                ) { Text("Cancel") }
            }
        }
    )
}
