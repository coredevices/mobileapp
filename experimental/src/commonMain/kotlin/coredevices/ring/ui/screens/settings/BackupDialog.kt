package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_backup_create_key
import coreapp.ring.generated.resources.settings_backup_delete_cloud
import coreapp.ring.generated.resources.settings_backup_delete_cloud_subtitle
import coreapp.ring.generated.resources.settings_backup_delete_title
import coreapp.ring.generated.resources.settings_backup_delete_warning
import coreapp.ring.generated.resources.settings_backup_disable_encryption
import coreapp.ring.generated.resources.settings_backup_download
import coreapp.ring.generated.resources.settings_backup_download_subtitle
import coreapp.ring.generated.resources.settings_backup_enable_encryption
import coreapp.ring.generated.resources.settings_backup_enabled
import coreapp.ring.generated.resources.settings_backup_enabled_subtitle
import coreapp.ring.generated.resources.settings_backup_encryption
import coreapp.ring.generated.resources.settings_backup_encryption_key
import coreapp.ring.generated.resources.settings_backup_encryption_off
import coreapp.ring.generated.resources.settings_backup_encryption_on
import coreapp.ring.generated.resources.settings_backup_enter_key
import coreapp.ring.generated.resources.settings_backup_enter_key_description
import coreapp.ring.generated.resources.settings_backup_enter_key_subtitle
import coreapp.ring.generated.resources.settings_backup_enter_key_title
import coreapp.ring.generated.resources.settings_backup_generate_key
import coreapp.ring.generated.resources.settings_backup_id
import coreapp.ring.generated.resources.settings_backup_import
import coreapp.ring.generated.resources.settings_backup_import_qr
import coreapp.ring.generated.resources.settings_backup_import_qr_subtitle
import coreapp.ring.generated.resources.settings_backup_items
import coreapp.ring.generated.resources.settings_backup_key_exists
import coreapp.ring.generated.resources.settings_backup_key_generated_before
import coreapp.ring.generated.resources.settings_backup_recordings_count
import coreapp.ring.generated.resources.settings_backup_recordings_count_unknown
import coreapp.ring.generated.resources.settings_backup_restore_key
import coreapp.ring.generated.resources.settings_backup_restore_key_subtitle
import coreapp.ring.generated.resources.settings_backup_title
import coreapp.ring.generated.resources.settings_backup_type_to_confirm
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.delete
import coreapp.util.generated.resources.done
import coreapp.util.generated.resources.loading
import coredevices.ring.encryption.KeyStorageStatus
import coredevices.ring.ui.viewmodel.BackupViewModel
import coredevices.ring.ui.viewmodel.EncryptionViewModel
import coredevices.ring.ui.viewmodel.pickZipFile
import coredevices.ui.M3Dialog
import coredevices.ui.dismissKeyboardOnTapOutside
import coredevices.util.rememberUiContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun BackupDialog(
    debugDetailsEnabled: Boolean,
    onDismiss: () -> Unit,
    backupViewModel: BackupViewModel = koinViewModel(),
    encryptionViewModel: EncryptionViewModel = koinViewModel(),
) {
    val userId by backupViewModel.userId.collectAsState()
    val backupDownloading by backupViewModel.backupDownloading.collectAsState()
    val backupDownloadStatus by backupViewModel.backupDownloadStatus.collectAsState()
    val importing by backupViewModel.importing.collectAsState()
    val importStatus by backupViewModel.importStatus.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val backupCount by backupViewModel.backupCount.collectAsState()
    val backupLoading by backupViewModel.backupLoading.collectAsState()
    val backupStatus by backupViewModel.backupStatus.collectAsState()
    val backupEnabled by backupViewModel.backupEnabled.collectAsState()
    val keyStorageStatus by encryptionViewModel.keyStorageStatus.collectAsState()
    val encryptionKeyStatus by encryptionViewModel.encryptionKeyStatus.collectAsState()
    val encryptionKeyLoading by encryptionViewModel.encryptionKeyLoading.collectAsState()
    val uiContext = rememberUiContext()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteInput by remember { mutableStateOf("") }
    var showDeleteLocalConfirm by remember { mutableStateOf(false) }
    var deleteLocalInput by remember { mutableStateOf("") }
    var showOverwriteKeyConfirm by remember { mutableStateOf(false) }
    var showEnterKeyDialog by remember { mutableStateOf(false) }
    var enterKeyInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        backupViewModel.loadBackupCount()
    }
    DisposableEffect(Unit) {
        onDispose {
            backupViewModel.clearBackupStatus()
            encryptionViewModel.clearEncryptionKeyStatus()
        }
    }

    if (showOverwriteKeyConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteKeyConfirm = false },
            title = { Text("Overwrite Encryption Key?") },
            text = {
                Text(buildAnnotatedString {
                    append("An encryption key already exists. Generating a new one will overwrite it.\n\n")
                    withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                        append("Previously encrypted backups (cloud sync or local) will be permanently lost.")
                    }
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteKeyConfirm = false
                    uiContext?.let { encryptionViewModel.generateAndStoreKey(it) }
                }) {
                    Text("Overwrite", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteKeyConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showEnterKeyDialog) {
        AlertDialog(
            onDismissRequest = { showEnterKeyDialog = false; enterKeyInput = "" },
            modifier = Modifier.dismissKeyboardOnTapOutside(),
            title = { Text(stringResource(Res.string.settings_backup_enter_key_title)) },
            text = {
                Column {
                    val focusManager = LocalFocusManager.current
                    Text(stringResource(Res.string.settings_backup_enter_key_description))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = enterKeyInput,
                        onValueChange = { enterKeyInput = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.settings_backup_encryption_key)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = enterKeyInput.isNotBlank() && !encryptionKeyLoading,
                    onClick = {
                        encryptionViewModel.importKeyFromText(enterKeyInput)
                        showEnterKeyDialog = false
                        enterKeyInput = ""
                    }
                ) {
                    Text(stringResource(Res.string.settings_backup_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnterKeyDialog = false; enterKeyInput = "" }) {
                    Text(stringResource(UtilRes.string.cancel))
                }
            }
        )
    }
    EncryptionKeyResultDialogs(encryptionViewModel)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; deleteInput = "" },
            modifier = Modifier.dismissKeyboardOnTapOutside(),
            title = { Text(stringResource(Res.string.settings_backup_delete_title)) },
            text = {
                Column {
                    val focusManager = LocalFocusManager.current
                    Text(
                        stringResource(Res.string.settings_backup_delete_warning),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(Res.string.settings_backup_type_to_confirm, "delete"))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteInput,
                        onValueChange = { deleteInput = it },
                        singleLine = true,
                        placeholder = { Text("delete") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteInput.equals("delete", ignoreCase = true) && !backupLoading,
                    onClick = {
                        backupViewModel.deleteBackup()
                        showDeleteConfirm = false
                        deleteInput = ""
                    }
                ) {
                    Text(stringResource(UtilRes.string.delete), color = if (deleteInput.equals("delete", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; deleteInput = "" }) {
                    Text(stringResource(UtilRes.string.cancel))
                }
            }
        )
    }
    if (showDeleteLocalConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteLocalConfirm = false; deleteLocalInput = "" },
            modifier = Modifier.dismissKeyboardOnTapOutside(),
            title = { Text("Delete Local Feed") },
            text = {
                Column {
                    val focusManager = LocalFocusManager.current
                    Text(
                        "This will delete all recordings from the local feed on this device. Cloud backup is not affected. You can restore from cloud with Sync or from a backup zip with Import.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Type \"delete\" to confirm:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteLocalInput,
                        onValueChange = { deleteLocalInput = it },
                        singleLine = true,
                        placeholder = { Text("delete") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteLocalInput.equals("delete", ignoreCase = true) && !backupLoading,
                    onClick = {
                        backupViewModel.deleteLocalFeed()
                        showDeleteLocalConfirm = false
                        deleteLocalInput = ""
                    }
                ) {
                    Text("Delete", color = if (deleteLocalInput.equals("delete", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLocalConfirm = false; deleteLocalInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_backup_title)) },
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(UtilRes.string.done)) }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            // Backup count
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_backup_items)) },
                supportingContent = {
                    if (backupLoading && backupCount == null) {
                        Text(stringResource(UtilRes.string.loading))
                    } else {
                        Column {
                            val count = backupCount
                            Text(
                                if (count == null) {
                                    stringResource(Res.string.settings_backup_recordings_count_unknown)
                                } else {
                                    pluralStringResource(Res.plurals.settings_backup_recordings_count, count, count)
                                }
                            )
                            userId?.let { id ->
                                Text(stringResource(Res.string.settings_backup_id, id), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                trailingContent = {
                    if (backupLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Backup enabled toggle
            ListItem(
                modifier = Modifier.clickable { backupViewModel.setBackupEnabled(!backupEnabled) },
                headlineContent = { Text(stringResource(Res.string.settings_backup_enabled)) },
                supportingContent = { Text(stringResource(Res.string.settings_backup_enabled_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = backupEnabled,
                        onCheckedChange = { backupViewModel.setBackupEnabled(it) }
                    )
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Download full backup
            ListItem(
                modifier = Modifier.clickable(enabled = !backupDownloading && uiContext != null) {
                    uiContext?.let { backupViewModel.downloadFullBackup(it) }
                },
                headlineContent = { Text(stringResource(Res.string.settings_backup_download)) },
                supportingContent = {
                    Text(backupDownloadStatus ?: stringResource(Res.string.settings_backup_download_subtitle))
                },
                trailingContent = {
                    if (backupDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )

            // Import backup
            if (debugDetailsEnabled) {
                ListItem(
                    modifier = Modifier.clickable(enabled = !importing && !backupDownloading && uiContext != null) {
                        scope.launch {
                            val path = uiContext?.let { pickZipFile(it) }
                            if (path != null) {
                                backupViewModel.importBackup(path)
                            }
                        }
                    },
                    headlineContent = { Text("Import Backup") },
                    supportingContent = {
                        Text(importStatus ?: "Restore recordings from a backup zip file")
                    },
                    trailingContent = {
                        if (importing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }


            // Delete backup
            ListItem(
                modifier = Modifier.clickable(enabled = !backupLoading) {
                    showDeleteConfirm = true
                },
                headlineContent = {
                    Text(stringResource(Res.string.settings_backup_delete_cloud), color = MaterialTheme.colorScheme.error)
                },
                supportingContent = {
                    Text(backupStatus ?: stringResource(Res.string.settings_backup_delete_cloud_subtitle))
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Encryption section ---
            Text(
                stringResource(Res.string.settings_backup_encryption),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Generate encryption key. A key already exists (here or on
            // another device) unless status is NoKeyStored — regenerating it
            // orphans prior backups, so warn and gate behind debug details.
            val keyExists = keyStorageStatus != KeyStorageStatus.NoKeyStored
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null && (!keyExists || debugDetailsEnabled)) {
                    if (keyExists) {
                        showOverwriteKeyConfirm = true
                    } else {
                        uiContext?.let { encryptionViewModel.generateAndStoreKey(it) }
                    }
                },
                headlineContent = { Text(stringResource(Res.string.settings_backup_generate_key)) },
                supportingContent = {
                    Text(
                        encryptionKeyStatus?.let { AnnotatedString(it) } ?: when (keyStorageStatus) {
                            KeyStorageStatus.KeyLocallyAvailable -> buildAnnotatedString {
                                append(stringResource(Res.string.settings_backup_key_exists))
                                if (debugDetailsEnabled) withStyle(SpanStyle(color = Color.Red)) {
                                    append(" — tap to regenerate")
                                }
                            }
                            KeyStorageStatus.KeyGeneratedBefore -> buildAnnotatedString {
                                append(stringResource(Res.string.settings_backup_key_generated_before))
                                if (debugDetailsEnabled) withStyle(SpanStyle(color = Color.Red)) {
                                    append(", or tap to regenerate")
                                }
                            }
                            KeyStorageStatus.NoKeyStored -> AnnotatedString(stringResource(Res.string.settings_backup_create_key))
                        }
                    )
                },
                trailingContent = {
                    if (encryptionKeyLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )

            // Read key from password manager
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null) {
                    uiContext?.let { encryptionViewModel.readKeyFromCloudKeychain(it) }
                },
                headlineContent = { Text(stringResource(Res.string.settings_backup_restore_key)) },
                supportingContent = {
                    Text(stringResource(Res.string.settings_backup_restore_key_subtitle))
                }
            )

            // Import key from a QR code photo (the backup saved at generation time)
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null) {
                    uiContext?.let { encryptionViewModel.importKeyFromQrPhoto(it) }
                },
                headlineContent = { Text(stringResource(Res.string.settings_backup_import_qr)) },
                supportingContent = {
                    Text(stringResource(Res.string.settings_backup_import_qr_subtitle))
                }
            )

            // Enter the key manually as text (e.g. copied from another device)
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading) {
                    enterKeyInput = ""
                    showEnterKeyDialog = true
                },
                headlineContent = { Text(stringResource(Res.string.settings_backup_enter_key)) },
                supportingContent = {
                    Text(stringResource(Res.string.settings_backup_enter_key_subtitle))
                }
            )

            // Enable/disable encryption
            val useEncryption by encryptionViewModel.useEncryption.collectAsState()
            val encryptionStatus by encryptionViewModel.encryptionStatus.collectAsState()
            val enablingEncryption by encryptionViewModel.enablingEncryption.collectAsState()

            if (keyStorageStatus == KeyStorageStatus.KeyLocallyAvailable) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    modifier = Modifier.clickable(enabled = !enablingEncryption && (useEncryption || uiContext != null)) {
                        if (!useEncryption) {
                            uiContext?.let { encryptionViewModel.requestEnableEncryption(it) }
                        } else {
                            encryptionViewModel.disableEncryption()
                        }
                    },
                    headlineContent = {
                        Text(
                            stringResource(
                                if (useEncryption) Res.string.settings_backup_disable_encryption
                                else Res.string.settings_backup_enable_encryption
                            )
                        )
                    },
                    supportingContent = {
                        Text(
                            encryptionStatus
                                ?: stringResource(
                                    if (useEncryption) Res.string.settings_backup_encryption_on
                                    else Res.string.settings_backup_encryption_off
                                )
                        )
                    },
                    trailingContent = {
                        if (enablingEncryption) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Danger zone ---
            if (debugDetailsEnabled) {
                Text(
                    "Danger Zone",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    modifier = Modifier.clickable(enabled = !backupLoading) {
                        showDeleteLocalConfirm = true
                    },
                    headlineContent = {
                        Text("Delete local feed", color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = {
                        Text("Remove all recordings from this device (cloud unaffected)")
                    }
                )
            }
        }
    }
}
