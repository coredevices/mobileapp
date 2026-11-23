package coredevices.ring.ui.viewmodel

import PlatformUiContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.encryption_vm_checking_backup
import coreapp.ring.generated.resources.encryption_vm_disabled
import coreapp.ring.generated.resources.encryption_vm_enabled
import coreapp.ring.generated.resources.encryption_vm_failed
import coreapp.ring.generated.resources.encryption_vm_imported_from_qr
import coreapp.ring.generated.resources.encryption_vm_imported_from_text
import coreapp.ring.generated.resources.encryption_vm_invalid_key
import coreapp.ring.generated.resources.encryption_vm_invalid_qr_key
import coreapp.ring.generated.resources.encryption_vm_key_generated
import coreapp.ring.generated.resources.encryption_vm_key_generated_qr_saved
import coreapp.ring.generated.resources.encryption_vm_key_generation_failed
import coreapp.ring.generated.resources.encryption_vm_key_import_failed
import coreapp.ring.generated.resources.encryption_vm_key_mismatch
import coreapp.ring.generated.resources.encryption_vm_key_restored
import coreapp.ring.generated.resources.encryption_vm_key_unusable
import coreapp.ring.generated.resources.encryption_vm_no_local_key
import coreapp.ring.generated.resources.encryption_vm_no_qr_found
import coreapp.ring.generated.resources.encryption_vm_qr_import_failed
import coreapp.ring.generated.resources.encryption_vm_qr_import_failed_reason
import coreapp.ring.generated.resources.encryption_vm_verify_backup_failed
import coredevices.ring.encryption.EnableEncryptionResult
import coredevices.ring.encryption.EncryptionManager
import coredevices.ring.encryption.EncryptionSetupState
import coredevices.ring.encryption.KeyStorageStatus
import coredevices.ring.ui.components.QrPhotoPickResult
import coredevices.ring.ui.components.pickQrCodeFromPhotos
import coredevices.ring.ui.components.saveQrCodeToPhotos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** Encryption key management and the enable/disable flows. Keying ops live in [EncryptionManager]. */
class EncryptionViewModel(
    private val encryptionManager: EncryptionManager,
) : ViewModel() {
    private val _encryptionKeyStatus = MutableStateFlow<String?>(null)
    val encryptionKeyStatus = _encryptionKeyStatus.asStateFlow()
    private val _encryptionKeyLoading = MutableStateFlow(false)
    val encryptionKeyLoading = _encryptionKeyLoading.asStateFlow()
    val keyStorageStatus = encryptionManager.keyStorageStatus
    val generatedKey = encryptionManager.generatedKey
    val useEncryption = encryptionManager.useEncryption
    private val _encryptionStatus = MutableStateFlow<String?>(null)
    val encryptionStatus = _encryptionStatus.asStateFlow()
    private val _enablingEncryption = MutableStateFlow(false)
    val enablingEncryption = _enablingEncryption.asStateFlow()

    private val _showKeyNotBackedUpDialog = MutableStateFlow(false)
    val showKeyNotBackedUpDialog = _showKeyNotBackedUpDialog.asStateFlow()

    fun generateAndStoreKey(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            _encryptionKeyLoading.value = true
            try {
                val key = encryptionManager.generateAndStoreKey(uiContext)
                // Save the QR (and let iOS show its photos prompt) before
                // revealing the key dialog, which would cover the prompt.
                val qrSaved = trySaveKeyQrToPhotos(uiContext, key)
                encryptionManager.revealGeneratedKey(key)
                _encryptionKeyStatus.value = if (qrSaved) {
                    getString(Res.string.encryption_vm_key_generated_qr_saved)
                } else {
                    getString(Res.string.encryption_vm_key_generated)
                }
            } catch (e: Exception) {
                _encryptionKeyStatus.value = getString(Res.string.encryption_vm_failed, e.message.toString())
            } finally {
                _encryptionKeyLoading.value = false
            }
        }
    }

    /** Back the key up as a QR code in the photo library; never fails the key flow. */
    private suspend fun trySaveKeyQrToPhotos(
        uiContext: PlatformUiContext,
        keyBase64: String,
    ): Boolean = try {
        saveQrCodeToPhotos(uiContext, keyBase64, "Index encryption key.png")
    } catch (e: Exception) {
        Logger.withTag("Encryption").w(e) { "Could not save key QR code to photos" }
        false
    }

    fun readKeyFromCloudKeychain(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            _encryptionKeyLoading.value = true
            try {
                encryptionManager.readKeyFromCloudKeychain(uiContext)
                _encryptionKeyStatus.value = getString(Res.string.encryption_vm_key_restored)
            } catch (e: Exception) {
                _encryptionKeyStatus.value = e.message
            } finally {
                _encryptionKeyLoading.value = false
            }
        }
    }

    fun clearEncryptionKeyStatus() { _encryptionKeyStatus.value = null }
    fun clearGeneratedKey() = encryptionManager.clearGeneratedKey()

    /** Enable encryption, but first verify the key is backed up to the cloud
     *  keychain; if not, raise a confirmation dialog instead. */
    fun requestEnableEncryption(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            _enablingEncryption.value = true
            _encryptionStatus.value = getString(Res.string.encryption_vm_checking_backup)
            try {
                val backedUp = encryptionManager.isLocalKeyBackedUpToCloud(uiContext)
                if (backedUp) {
                    enableEncryptionNow()
                } else {
                    _encryptionStatus.value = null
                    _enablingEncryption.value = false
                    _showKeyNotBackedUpDialog.value = true
                }
            } catch (e: Exception) {
                _encryptionStatus.value = getString(Res.string.encryption_vm_verify_backup_failed, e.message.toString())
                _enablingEncryption.value = false
            }
        }
    }

    fun confirmEnableEncryption() {
        _showKeyNotBackedUpDialog.value = false
        viewModelScope.launch { enableEncryptionNow() }
    }

    fun dismissKeyNotBackedUpDialog() {
        _showKeyNotBackedUpDialog.value = false
    }

    private suspend fun enableEncryptionNow() {
        _encryptionStatus.value = try {
            when (val result = encryptionManager.enableEncryption()) {
                EnableEncryptionResult.Enabled ->
                    getString(Res.string.encryption_vm_enabled)
                EnableEncryptionResult.NoLocalKey ->
                    getString(Res.string.encryption_vm_no_local_key)
                is EnableEncryptionResult.KeyFingerprintMismatch ->
                    getString(Res.string.encryption_vm_key_mismatch)
                is EnableEncryptionResult.KeyUnusable ->
                    getString(Res.string.encryption_vm_key_unusable, result.reason)
            }
        } finally {
            _enablingEncryption.value = false
        }
    }

    fun disableEncryption() {
        encryptionManager.disableEncryption()
        viewModelScope.launch { _encryptionStatus.value = getString(Res.string.encryption_vm_disabled) }
    }

    // Guided setup: a single switch that walks the user through generating or
    // restoring a key before turning encryption on.

    private val _encryptionSetup = MutableStateFlow<EncryptionSetupState>(EncryptionSetupState.Hidden)
    val encryptionSetup = _encryptionSetup.asStateFlow()

    /** User flipped the encrypt switch on: enable directly if a usable
     *  local key is present, otherwise drive the setup dialog. */
    fun beginEncryptionSetup(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            when (encryptionManager.keyStorageStatus.value) {
                KeyStorageStatus.KeyLocallyAvailable -> requestEnableEncryption(uiContext)
                KeyStorageStatus.NoKeyStored ->
                    _encryptionSetup.value = EncryptionSetupState.PromptGenerate
                KeyStorageStatus.KeyGeneratedBefore -> {
                    _encryptionSetup.value = EncryptionSetupState.Restoring
                    val restored = try {
                        encryptionManager.readKeyFromCloudKeychain(uiContext)
                    } catch (e: Exception) {
                        Logger.withTag("Encryption").w(e) { "Cloud keychain restore failed" }
                        false
                    }
                    if (restored) finishSetupAndEnable()
                    else _encryptionSetup.value = EncryptionSetupState.PasteKey()
                }
            }
        }
    }

    /** "Generate Key" inside the setup dialog. */
    fun generateKeyForSetup(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            _encryptionSetup.value = EncryptionSetupState.Generating
            try {
                val key = encryptionManager.generateAndStoreKey(uiContext)
                // Hide the wizard while the QR is saved — on iOS the photos
                // permission prompt would otherwise show underneath it.
                _encryptionSetup.value = EncryptionSetupState.Hidden
                val qrSaved = trySaveKeyQrToPhotos(uiContext, key)
                _encryptionSetup.value = EncryptionSetupState.ShowKey(
                    keyBase64 = key,
                    qrSavedToPhotos = qrSaved,
                )
            } catch (e: Exception) {
                _encryptionSetup.value =
                    EncryptionSetupState.Failed(e.message ?: getString(Res.string.encryption_vm_key_generation_failed))
            }
        }
    }

    /** User pasted their backup key on the paste step. */
    fun restoreFromPastedKey(keyBase64: String) {
        viewModelScope.launch {
            _encryptionSetup.value = EncryptionSetupState.Restoring
            val restored = try {
                encryptionManager.restoreKeyFromString(keyBase64)
            } catch (e: Exception) {
                Logger.withTag("Encryption").w(e) { "Pasted key restore failed" }
                false
            }
            if (restored) {
                finishSetupAndEnable()
            } else {
                _encryptionSetup.value =
                    EncryptionSetupState.PasteKey(getString(Res.string.encryption_vm_invalid_key))
            }
        }
    }

    /** "Import from QR" on the paste step — pick the key QR photo and restore it. */
    fun restoreFromQrPhoto(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            try {
                when (val result = pickQrCodeFromPhotos(uiContext)) {
                    is QrPhotoPickResult.Found -> {
                        _encryptionSetup.value = EncryptionSetupState.Restoring
                        if (encryptionManager.restoreKeyFromString(result.data)) {
                            finishSetupAndEnable()
                        } else {
                            _encryptionSetup.value = EncryptionSetupState.PasteKey(
                                getString(Res.string.encryption_vm_invalid_qr_key)
                            )
                        }
                    }
                    QrPhotoPickResult.NoQrFound ->
                        _encryptionSetup.value =
                            EncryptionSetupState.PasteKey(getString(Res.string.encryption_vm_no_qr_found))
                    QrPhotoPickResult.Cancelled -> {}
                }
            } catch (e: Exception) {
                Logger.withTag("Encryption").w(e) { "QR key import failed" }
                _encryptionSetup.value =
                    EncryptionSetupState.PasteKey(e.message ?: getString(Res.string.encryption_vm_qr_import_failed))
            }
        }
    }

    /** Settings-list QR import, reporting through [encryptionKeyStatus]. */
    fun importKeyFromQrPhoto(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            _encryptionKeyLoading.value = true
            try {
                when (val result = pickQrCodeFromPhotos(uiContext)) {
                    is QrPhotoPickResult.Found ->
                        _encryptionKeyStatus.value =
                            if (encryptionManager.restoreKeyFromString(result.data)) {
                                getString(Res.string.encryption_vm_imported_from_qr)
                            } else {
                                getString(Res.string.encryption_vm_invalid_qr_key)
                            }
                    QrPhotoPickResult.NoQrFound ->
                        _encryptionKeyStatus.value = getString(Res.string.encryption_vm_no_qr_found)
                    QrPhotoPickResult.Cancelled -> {}
                }
            } catch (e: Exception) {
                _encryptionKeyStatus.value = getString(Res.string.encryption_vm_qr_import_failed_reason, e.message.toString())
            } finally {
                _encryptionKeyLoading.value = false
            }
        }
    }

    /** Settings-list manual key entry, reporting through [encryptionKeyStatus]. */
    fun importKeyFromText(keyBase64: String) {
        val key = keyBase64.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            _encryptionKeyLoading.value = true
            try {
                _encryptionKeyStatus.value =
                    if (encryptionManager.restoreKeyFromString(key)) {
                        getString(Res.string.encryption_vm_imported_from_text)
                    } else {
                        getString(Res.string.encryption_vm_invalid_key)
                    }
            } catch (e: Exception) {
                _encryptionKeyStatus.value = getString(Res.string.encryption_vm_key_import_failed, e.message.toString())
            } finally {
                _encryptionKeyLoading.value = false
            }
        }
    }

    /** "Done" after the key was shown — enable encryption and close. */
    fun completeEncryptionSetup() {
        viewModelScope.launch { finishSetupAndEnable() }
    }

    private suspend fun finishSetupAndEnable() {
        _enablingEncryption.value = true
        enableEncryptionNow()
        _encryptionSetup.value = EncryptionSetupState.Hidden
    }

    fun cancelEncryptionSetup() {
        encryptionManager.clearGeneratedKey()
        _encryptionSetup.value = EncryptionSetupState.Hidden
    }
}
