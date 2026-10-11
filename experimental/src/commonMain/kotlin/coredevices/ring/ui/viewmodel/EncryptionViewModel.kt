package coredevices.ring.ui.viewmodel

import PlatformUiContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
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
                    "Encryption key generated — QR code saved to your photos"
                } else {
                    "Encryption key generated and saved"
                }
            } catch (e: Exception) {
                _encryptionKeyStatus.value = "Failed: ${e.message}"
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
                _encryptionKeyStatus.value = "Key restored from Password Manager"
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
            _encryptionStatus.value = "Checking key backup..."
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
                _encryptionStatus.value = "Could not verify key backup: ${e.message}"
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
                    "Encryption enabled — future uploads encrypted"
                EnableEncryptionResult.NoLocalKey ->
                    "Can't enable encryption: no key on this device"
                is EnableEncryptionResult.KeyFingerprintMismatch ->
                    "Can't enable encryption: key on this device doesn't match your account key"
                is EnableEncryptionResult.KeyUnusable ->
                    "Can't enable encryption: key is unusable (${result.reason})"
            }
        } finally {
            _enablingEncryption.value = false
        }
    }

    fun disableEncryption() {
        encryptionManager.disableEncryption()
        _encryptionStatus.value = "Encryption disabled — future uploads will be unencrypted"
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
                    EncryptionSetupState.Failed(e.message ?: "Key generation failed")
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
                    EncryptionSetupState.PasteKey("That key isn't valid for this account")
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
                                "That QR code isn't a valid key for this account"
                            )
                        }
                    }
                    QrPhotoPickResult.NoQrFound ->
                        _encryptionSetup.value =
                            EncryptionSetupState.PasteKey("No QR code found in that photo")
                    QrPhotoPickResult.Cancelled -> {}
                }
            } catch (e: Exception) {
                Logger.withTag("Encryption").w(e) { "QR key import failed" }
                _encryptionSetup.value =
                    EncryptionSetupState.PasteKey(e.message ?: "QR import failed")
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
                                "Key imported from QR code"
                            } else {
                                "That QR code isn't a valid key for this account"
                            }
                    QrPhotoPickResult.NoQrFound ->
                        _encryptionKeyStatus.value = "No QR code found in that photo"
                    QrPhotoPickResult.Cancelled -> {}
                }
            } catch (e: Exception) {
                _encryptionKeyStatus.value = "QR import failed: ${e.message}"
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
                        "Key imported from text"
                    } else {
                        "That key isn't valid for this account"
                    }
            } catch (e: Exception) {
                _encryptionKeyStatus.value = "Key import failed: ${e.message}"
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
