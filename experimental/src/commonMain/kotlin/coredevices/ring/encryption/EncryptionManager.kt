package coredevices.ring.encryption

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.firestore.EncryptionInfo
import coredevices.firestore.UsersDao
import coredevices.ring.database.Preferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.util.Platform
import coredevices.util.isAndroid
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/** Outcome of [EncryptionManager.enableEncryption]. */
sealed interface EnableEncryptionResult {
    data object Enabled : EnableEncryptionResult
    /** No key in the key manager for the current account. */
    data object NoLocalKey : EnableEncryptionResult
    /** Local key doesn't match the fingerprint recorded for this account. */
    data class KeyFingerprintMismatch(
        val localFingerprint: String,
        val expectedFingerprint: String,
    ) : EnableEncryptionResult
    /** Local key is present but failed an encrypt/decrypt self-test. */
    data class KeyUnusable(val reason: String) : EnableEncryptionResult
}

/** Steps of the guided "turn on encryption" flow, walking the user
 *  through whichever is needed before encryption is turned on. */
sealed interface EncryptionSetupState {
    /** Dialog not shown. */
    data object Hidden : EncryptionSetupState
    /** No key yet — offer to generate one. */
    data object PromptGenerate : EncryptionSetupState
    /** Generating the key and saving it to the password manager. */
    data object Generating : EncryptionSetupState
    /** Key ready — show QR/copy so the user can back it up. */
    data class ShowKey(
        val keyBase64: String,
        /** True if a QR code of the key was saved to the photo library. */
        val qrSavedToPhotos: Boolean = false,
    ) : EncryptionSetupState
    /** A key exists elsewhere — trying the password manager. */
    data object Restoring : EncryptionSetupState
    /** Password-manager restore failed — let the user paste the key. */
    data class PasteKey(val error: String? = null) : EncryptionSetupState
    /** Could not obtain a key. */
    data class Failed(val message: String) : EncryptionSetupState
}

/** State of the encryption key for the current account. */
enum class KeyStorageStatus {
    /** No key on this device, none recorded for the account. */
    NoKeyStored,
    /** Not on this device, but a fingerprint is recorded — a key exists
     *  elsewhere and should be restored, not regenerated. */
    KeyGeneratedBefore,
    /** A key for the current account is on this device. */
    KeyLocallyAvailable,
}

/**
 * Owns encryption state and operations: key generation, cloud-keychain
 * backup/restore, and the on/off switch for encrypting future uploads.
 * Enabling is forward-only — existing cloud data is left as-is.
 */
class EncryptionManager(
    private val encryptionKeyManager: EncryptionKeyManager,
    private val usersDao: UsersDao,
    private val preferences: Preferences,
    private val platform: Platform,
    private val scope: RecordingBackgroundScope,
) {
    companion object {
        private val logger = Logger.withTag("EncryptionManager")
    }
    // --- Key management state ---

    /** Bumped after the local key store changes (generate/restore). The
     *  local key is a suspend one-shot, not a flow, so this re-drives
     *  [keyStorageStatus] to re-read it. Exposed via [keyStoreRevision] so
     *  observers can react to a key being *replaced* (e.g. a wrong key
     *  swapped for the right one), which leaves [keyStorageStatus]
     *  unchanged at [KeyStorageStatus.KeyLocallyAvailable]. */
    private val _keyStoreRevision = MutableStateFlow(0)
    val keyStoreRevision: StateFlow<Int> = _keyStoreRevision.asStateFlow()

    /** Derived from account email, recorded fingerprints (prefs +
     *  Firestore) and local key presence, so it can't go stale. */
    val keyStorageStatus: StateFlow<KeyStorageStatus> =
        combine(
            Firebase.auth.authStateChanged
                .map { it?.email }
                .onStart { emit(Firebase.auth.currentUser?.email) }
                .distinctUntilChanged(),
            preferences.encryptionKeyFingerprint,
            usersDao.user
                .map { it?.user?.encryption?.keyFingerprint }
                .onStart { emit(null) }
                .catch { e ->
                    logger.w(e) { "Could not read encryption info from Firestore" }
                    emit(null)
                },
            keyStoreRevision,
        ) { email, prefFingerprint, firestoreFingerprint, _ ->
            val hasLocalKey = withContext(Dispatchers.IO) {
                encryptionKeyManager.getLocalKey(email) != null
            }
            when {
                hasLocalKey -> KeyStorageStatus.KeyLocallyAvailable
                prefFingerprint != null || firestoreFingerprint != null ->
                    KeyStorageStatus.KeyGeneratedBefore
                else -> KeyStorageStatus.NoKeyStored
            }
        }.stateIn(scope, SharingStarted.Eagerly, KeyStorageStatus.NoKeyStored)

    private val _generatedKey = MutableStateFlow<String?>(null)
    val generatedKey = _generatedKey.asStateFlow()

    val useEncryption = preferences.useEncryption

    /**
     * Generate, store and back up a key. The new key is returned but NOT
     * shown in the key-backup dialog — call [revealGeneratedKey] once any
     * follow-up system prompts (e.g. photo library) are out of the way,
     * since on iOS those would render underneath the dialog.
     */
    suspend fun generateAndStoreKey(uiContext: PlatformUiContext): String {
        val keyResult = encryptionKeyManager.generateKey()

        val email = Firebase.auth.currentUser?.email ?: "unknown"
        withContext(Dispatchers.IO) {
            encryptionKeyManager.saveKeyLocally(keyResult.keyBase64, email)
        }

        var backupLocation = "local_only"
        try {
            encryptionKeyManager.saveToCloudKeychain(uiContext, keyResult.keyBase64)
            backupLocation = if (platform.isAndroid) "google_password_manager" else "icloud_keychain"
        } catch (e: Exception) {
            logger.w(e) { "Cloud keychain save failed (key still saved locally)" }
        }

        val deviceName = platform.deviceModelName

        val encryptionInfo = EncryptionInfo(
            keyFingerprint = keyResult.fingerprint,
            createdAt = Clock.System.now().toString(),
            keyBackupLocation = backupLocation,
            keyCreationDevice = deviceName
        )

        withContext(Dispatchers.IO) {
            usersDao.updateEncryptionInfo(encryptionInfo)
            preferences.setEncryptionKeyFingerprint(keyResult.fingerprint)
        }

        _keyStoreRevision.value++
        logger.i { "Key generated, fingerprint=${keyResult.fingerprint}, backup=$backupLocation" }
        return keyResult.keyBase64
    }

    /** Show [key] in the key-backup dialog (driven by [generatedKey]). */
    fun revealGeneratedKey(key: String) { _generatedKey.value = key }

    /** @return true if a key was found in the cloud keychain and stored locally. */
    suspend fun readKeyFromCloudKeychain(uiContext: PlatformUiContext): Boolean {
        val key = encryptionKeyManager.readFromCloudKeychain(uiContext)
        if (key != null) {
            val email = Firebase.auth.currentUser?.email ?: "unknown"
            withContext(Dispatchers.IO) {
                encryptionKeyManager.saveKeyLocally(key, email)
            }
            _keyStoreRevision.value++
            logger.i { "Key restored from cloud keychain" }
            return true
        }
        return false
    }

    /** Restore a key the user pasted in manually; it must round-trip and
     *  match any fingerprint already recorded. @return true if stored locally. */
    suspend fun restoreKeyFromString(keyBase64: String): Boolean {
        val key = keyBase64.trim()
        if (key.isEmpty()) return false
        try {
            val probe = "enc-probe".encodeToByteArray()
            val roundTripped = AesCbcHmacCrypto.decrypt(
                AesCbcHmacCrypto.encrypt(probe, key), key
            )
            if (!roundTripped.contentEquals(probe)) {
                logger.w { "Pasted key failed round-trip self-test" }
                return false
            }
        } catch (e: Exception) {
            logger.w(e) { "Pasted key is not a usable encryption key" }
            return false
        }
        val fingerprint = AesCbcHmacCrypto.keyFingerprint(key)
        val expected = preferences.encryptionKeyFingerprint.value
        if (expected != null && expected != fingerprint) {
            logger.w { "Pasted key fingerprint $fingerprint != expected $expected" }
            return false
        }
        val email = Firebase.auth.currentUser?.email ?: "unknown"
        withContext(Dispatchers.IO) {
            encryptionKeyManager.saveKeyLocally(key, email)
        }
        _keyStoreRevision.value++
        logger.i { "Key restored from pasted string, fingerprint=$fingerprint" }
        return true
    }

    fun clearGeneratedKey() { _generatedKey.value = null }

    /** True only if the cloud keychain holds a key matching the local
     *  key's fingerprint. Any failure returns false (not an error). */
    suspend fun isLocalKeyBackedUpToCloud(uiContext: PlatformUiContext): Boolean {
        val localKey = withContext(Dispatchers.IO) {
            encryptionKeyManager.getLocalKey(Firebase.auth.currentUser?.email)
        }
        if (localKey == null) {
            logger.w { "Cloud backup check: no local key" }
            return false
        }
        val cloudKey = try {
            encryptionKeyManager.readFromCloudKeychain(uiContext)
        } catch (e: Exception) {
            logger.w(e) { "Cloud backup check: could not read cloud keychain" }
            null
        }
        if (cloudKey == null) return false
        val matches = AesCbcHmacCrypto.keyFingerprint(cloudKey) ==
            AesCbcHmacCrypto.keyFingerprint(localKey)
        if (!matches) {
            logger.w { "Cloud backup check: cloud key fingerprint differs from local key" }
        }
        return matches
    }

    /**
     * Turn on encryption for future uploads (existing cloud data is left
     * unencrypted). Refuses unless a usable key is present, so we never
     * upload recordings nothing can decrypt: the account key must exist,
     * match any recorded fingerprint, and pass a round-trip self-test.
     */
    suspend fun enableEncryption(): EnableEncryptionResult {
        val localKey = withContext(Dispatchers.IO) {
            encryptionKeyManager.getLocalKey(Firebase.auth.currentUser?.email)
        }
        if (localKey == null) {
            _keyStoreRevision.value++
            logger.w { "Refusing to enable encryption: no local key in key manager" }
            return EnableEncryptionResult.NoLocalKey
        }

        val localFingerprint = AesCbcHmacCrypto.keyFingerprint(localKey)
        val expectedFingerprint = preferences.encryptionKeyFingerprint.value
        if (expectedFingerprint != null && expectedFingerprint != localFingerprint) {
            logger.w {
                "Refusing to enable encryption: local key fingerprint " +
                    "$localFingerprint != expected $expectedFingerprint"
            }
            return EnableEncryptionResult.KeyFingerprintMismatch(
                localFingerprint = localFingerprint,
                expectedFingerprint = expectedFingerprint,
            )
        }

        try {
            val probe = "enc-probe".encodeToByteArray()
            val roundTripped = AesCbcHmacCrypto.decrypt(
                AesCbcHmacCrypto.encrypt(probe, localKey), localKey
            )
            require(roundTripped.contentEquals(probe)) { "round-trip mismatch" }
        } catch (e: Exception) {
            logger.w(e) { "Refusing to enable encryption: key failed self-test" }
            return EnableEncryptionResult.KeyUnusable(e.message ?: "key self-test failed")
        }

        preferences.setUseEncryption(true)
        logger.i { "Encryption enabled — future uploads will be encrypted" }
        return EnableEncryptionResult.Enabled
    }

    fun disableEncryption() {
        preferences.setUseEncryption(false)
    }
}
