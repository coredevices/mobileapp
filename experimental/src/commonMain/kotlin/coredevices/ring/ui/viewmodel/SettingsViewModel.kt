package coredevices.ring.ui.viewmodel

import PlatformUiContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.RecordingDocument
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.libindex.device.IndexDeviceManager
import coredevices.libindex.device.InterviewedIndexDevice
import coredevices.libindex.device.KnownIndexDevice
import coredevices.libindex.di.LibIndexCoroutineScope
import coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.encryption.DocumentEncryptor
import coredevices.ring.encryption.EnableEncryptionResult
import coredevices.ring.encryption.EncryptionManager
import coredevices.ring.encryption.EncryptionSetupState
import coredevices.ring.encryption.KeyStorageStatus
import coredevices.ring.encryption.KeyFingerprintMismatchException
import coredevices.ring.encryption.TamperedException
import coredevices.ring.RingDelegate
import coredevices.ring.model.CactusModelProvider
import coredevices.ring.service.RingSync
import coredevices.ring.storage.BackupZipReader
import coredevices.ring.ui.components.QrPhotoPickResult
import coredevices.ring.ui.components.pickQrCodeFromPhotos
import coredevices.ring.ui.components.saveQrCodeToPhotos
import coredevices.ring.storage.BackupZipWriter
import coredevices.ring.storage.RecordingStorage
import coredevices.ui.ModelType
import coredevices.util.CommonBuildKonfig
import coredevices.util.emailOrNull
import coredevices.util.isAndroid
import coredevices.util.isIOS
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
private data class BackupManifest(
    val version: Int,
    val userId: String,
    val email: String,
    val exportedAt: String,
    val recordingCount: Int
)

class SettingsViewModel(
    private val ringSync: RingSync,
    private val preferences: Preferences,
    private val firestoreRecordingsDao: FirestoreRecordingsDao,
    private val recordingRepository: RecordingRepository,
    private val recordingEntryDao: RecordingEntryDao,
    private val conversationMessageDao: ConversationMessageDao,
    private val recordingStorage: RecordingStorage,
    private val documentEncryptor: DocumentEncryptor,
    private val encryptionManager: EncryptionManager,
    private val noteIntegrationFactory: NoteIntegrationFactory,
    private val gTasksIntegration: GTasksIntegration,
    private val indexDeviceManager: IndexDeviceManager,
    private val itemRepository: coredevices.ring.database.room.repository.ItemRepository,
    private val listRepository: coredevices.ring.database.room.repository.ListRepository,
    private val indexFeedSyncService: coredevices.ring.service.indexfeed.IndexFeedSyncService,
    private val platform: coredevices.util.Platform,
    private val mcpSandboxRepository: McpSandboxRepository,
    private val ringDelegate: RingDelegate,
    private val cactusModelProvider: CactusModelProvider,
    private val appScope: LibIndexCoroutineScope,
): ViewModel() {
    val version = CommonBuildKonfig.GIT_HASH
    val username = Firebase.auth.authStateChanged
        .map { it?.emailOrNull }
        .stateIn(viewModelScope, SharingStarted.Lazily, Firebase.auth.currentUser?.email)
    val userId = Firebase.auth.authStateChanged
        .map { it?.uid }
        .stateIn(viewModelScope, SharingStarted.Lazily, Firebase.auth.currentUser?.uid)
    private val _useCactusAgent = MutableStateFlow(false)
    val useCactusAgent = _useCactusAgent.asStateFlow()
    private val _showModelDownloadDialog = MutableStateFlow<ModelType?>(null)
    val showModelDownloadDialog = _showModelDownloadDialog.asStateFlow()
    private val _showMusicControlDialog = MutableStateFlow(false)
    val showMusicControlDialog = _showMusicControlDialog.asStateFlow()
    val musicControlMode = preferences.musicControlMode
    val debugDetailsEnabled = preferences.debugDetailsEnabled
    private val _showContactsDialog = MutableStateFlow(false)
    val showContactsDialog = _showContactsDialog.asStateFlow()
    private val _showSecondaryModeDialog = MutableStateFlow(false)
    val showSecondaryModeDialog = _showSecondaryModeDialog.asStateFlow()
    val secondaryMode = preferences.secondaryMode
    val secondaryModeMcpGroupId = preferences.secondaryModeMcpGroupId
    val sandboxGroups = mcpSandboxRepository.getAllGroupsFlow().stateIn(
        viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )
    private val _showNoteShortcutDialog = MutableStateFlow(false)
    val showNoteShortcutDialog = _showNoteShortcutDialog.asStateFlow()
    val noteShortcut = preferences.noteShortcut
    private val currentRing = indexDeviceManager.rings.map {
        it.firstOrNull { ring -> ring is KnownIndexDevice }
    }
    val ringPaired = preferences.ringPaired
    private val _panicPending = MutableStateFlow(false)
    val panicPending = _panicPending.asStateFlow()
    val currentRingFirmware = currentRing
        .mapNotNull { (it as? InterviewedIndexDevice)?.firmwareVersion }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    val currentRingName = currentRing
        .map { it?.name }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    private val _availableNoteProviders = MutableStateFlow<List<NoteProvider>>(emptyList())
    val availableNoteProviders = _availableNoteProviders.asStateFlow()
    private val _availableReminderProviders = MutableStateFlow<List<ReminderProvider>>(emptyList())
    val availableReminderProviders = _availableReminderProviders.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.useCactusAgent.collectLatest { useCactus ->
                _useCactusAgent.value = useCactus
            }
        }
        viewModelScope.launch {
            updateAvailableNoteProviders()
            updateAvailableReminderProviders()
        }
    }

    private suspend fun updateAvailableNoteProviders() {
        _availableNoteProviders.value = NoteProvider.entries.filter {
            noteIntegrationFactory.createNoteClient(it).isAuthorized()
        }
    }

    private suspend fun updateAvailableReminderProviders() {
        _availableReminderProviders.value = buildList {
            add(ReminderProvider.BuiltIn)
            if (platform.isIOS) {
                add(ReminderProvider.IOSReminders)
            }
            if (gTasksIntegration.isAuthorized()) {
                add(ReminderProvider.GoogleTasks)
            }
            // Tasker shares one opt-in across notes & reminders; reuse the note client's auth check.
            if (platform.isAndroid &&
                noteIntegrationFactory.createNoteClient(NoteProvider.Tasker).isAuthorized()
            ) {
                add(ReminderProvider.Tasker)
            }
        }
    }

    fun onModelDownloadDialogDismissed(success: Boolean) {
        val wasDownloading = _showModelDownloadDialog.value ?: return
        _showModelDownloadDialog.value = null
        // appScope: pref write must land even if the user leaves Settings.
        appScope.launch {
            when (wasDownloading) {
                is ModelType.Agent -> preferences.setUseCactusAgent(success)
                is ModelType.STT -> preferences.setUseCactusTranscription(success)
            }
        }
    }

    fun toggleCactusAgent() {
        appScope.launch {
            if (!_useCactusAgent.value) {
                // Trigger LM model extraction, should be already integrated into assets so no DL
                cactusModelProvider.getLMModelPath()
                preferences.setUseCactusAgent(true)
            } else {
                preferences.setUseCactusAgent(false)
            }
        }
    }

    fun showMusicControlDialog() {
        _showMusicControlDialog.value = true
    }

    fun closeMusicControlDialog() {
        _showMusicControlDialog.value = false
    }

    fun setMusicControlMode(mode: MusicControlMode) {
        preferences.setMusicControlMode(mode)
    }

    fun showSecondaryModeDialog() {
        _showSecondaryModeDialog.value = true
    }

    fun closeSecondaryModeDialog() {
        _showSecondaryModeDialog.value = false
    }

    fun setSecondaryMode(mode: SecondaryMode, mcpSandboxGroupId: Long? = null) {
        preferences.setSecondaryMode(mode)
        if (mode == SecondaryMode.McpSandbox) {
            preferences.setSecondaryModeMcpGroupId(mcpSandboxGroupId)
        }
    }

    fun toggleDebugDetailsEnabled() {
        viewModelScope.launch {
            val newValue = !debugDetailsEnabled.value
            preferences.setDebugDetailsEnabled(newValue)
        }
    }

    fun showNoteShortcutDialog() {
        viewModelScope.launch {
            updateAvailableNoteProviders()
            updateAvailableReminderProviders()
        }
        _showNoteShortcutDialog.value = true
    }

    fun closeNoteShortcutDialog() {
        _showNoteShortcutDialog.value = false
    }

    fun setNoteShortcut(shortcut: NoteShortcutType) {
        preferences.setNoteShortcut(shortcut)
    }

    fun showContactsDialog() {
        _showContactsDialog.value = true
    }

    fun closeContactsDialog() {
        _showContactsDialog.value = false
    }

    private val _syncingFeedHistory = MutableStateFlow(false)
    val syncingFeedHistory = _syncingFeedHistory.asStateFlow()
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus = _syncStatus.asStateFlow()


    fun panicRing() {
        _panicPending.value = true
        viewModelScope.launch {
            try {
                ringSync.lastRing.value?.panic()
            } catch (e: Exception) {
                Logger.withTag("Settings").e(e) { "Failed to panic ring: ${e.message}" }
            } finally {
                _panicPending.value = false
            }
        }
    }

    fun restartPreemptiveTransfer() {
        ringDelegate.restartPreemptiveTransfer()
    }

    /** Trigger upload of any locally-queued recordings that don't have a
     *  firestoreId yet. The actual upload happens in
     *  [coredevices.ring.service.recordings.RecordingProcessingQueue]'s
     *  push observer — which is the SINGLE uploader and uses
     *  `uploadingIds` to dedup. We just bump `updated` so the Room flow
     *  re-emits and the observer wakes up.
     *
     *  Don't upload directly from here: the observer already filters by
     *  `firestoreId == null`, so a parallel upload from this function
     *  would race the observer and create two Firestore docs for one
     *  local recording (the observer's `addRecording` runs concurrently
     *  with this one's). Auto-pull on a fresh device then mirrored both
     *  docs as separate Room rows — the source of the duplicate
     *  recordings users have been seeing in the feed. */
    private suspend fun uploadPendingRecordings() {
        val log = Logger.withTag("FeedHistorySync")
        val recordings = recordingRepository.getAllRecordings().first()
        val pending = recordings.filter { it.firestoreId == null }
        if (pending.isEmpty()) {
            log.i { "No pending uploads" }
            return
        }
        log.i { "Kicking ${pending.size} pending recordings for upload" }
        _syncStatus.value = "Uploading ${pending.size} local recordings..."
        val now = kotlin.time.Clock.System.now()
        val pendingIds = pending.map { it.id }.toSet()
        for (recording in pending) {
            recordingRepository.setRecordingUpdated(recording.id, now)
        }
        // Wait for the push observer to finish — every kicked id must
        // gain a firestoreId before we move on. Without this the rest of
        // downloadFeedHistory and the final "Sync complete" status race
        // with in-flight uploads. Cap at 60s to avoid hanging forever
        // if the observer is wedged (offline, auth dropped, etc).
        try {
            kotlinx.coroutines.withTimeout(60_000) {
                recordingRepository.getAllRecordings()
                    .first { recs ->
                        val stillPending = recs.count {
                            it.id in pendingIds && it.firestoreId == null
                        }
                        if (stillPending > 0) {
                            _syncStatus.value = "Uploading $stillPending local recordings..."
                        }
                        stillPending == 0
                    }
            }
            log.i { "All ${pending.size} pending recordings uploaded" }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            log.w { "Timed out waiting for ${pending.size} pending uploads — moving on" }
        }
    }

    // --- Backup ---

    /** Cached count from `Preferences.lastBackupCount`. Updated at the
     *  end of every successful sync (`performFeedHistoryDownload`). The
     *  Backup dialog reads this directly — no Firestore round-trip on
     *  open. The Gitlive multiplatform Firestore SDK 2.4.0 doesn't
     *  expose the server-side `count()` aggregation, so the previous
     *  implementation paginated through every full document body just
     *  to count them — slow on a 1300+ recording user. */
    val backupCount = preferences.lastBackupCount
    private val _backupLoading = MutableStateFlow(false)
    val backupLoading = _backupLoading.asStateFlow()
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus = _backupStatus.asStateFlow()
    val backupEnabled = preferences.backupEnabled

    /** Refresh the cached backup count using Firestore's server-side
     *  aggregation `count()` (one cheap read, no document bodies
     *  transferred). The Settings → Backup dialog calls this on open
     *  via `LaunchedEffect`. The result is written to
     *  `Preferences.lastBackupCount` so subsequent opens render
     *  instantly from the cached value while this refresh runs in
     *  the background.
     *
     *  iOS: the native aggregate API isn't bound through Gitlive, so
     *  the actual throws — we fall through to the legacy paginated
     *  `getCount()` (slow on big collections, but correct). */
    fun loadBackupCount() {
        appScope.launch {
            _backupLoading.value = true
            try {
                val count = withContext(Dispatchers.IO) {
                    firestoreRecordingsDao.getCount()
                }
                preferences.setLastBackupCount(count)
            } catch (e: Exception) {
                Logger.withTag("Backup").w(e) { "Failed to refresh backup count" }
                // Don't surface as an error in the UI — the cached value
                // (or "—" if never synced) stays as-is.
            } finally {
                _backupLoading.value = false
            }
        }
    }

    fun deleteBackup() {
        // appScope: a multi-step destructive operation — leaving Settings
        // mid-way must not abandon it half-done.
        appScope.launch {
            _backupLoading.value = true
            _backupStatus.value = "Deleting backup..."
            val log = Logger.withTag("Backup")
            try {
                // Collect all audio file names before deleting documents
                _backupStatus.value = "Collecting audio files..."
                val audioFileIds = mutableListOf<String>()
                withContext(Dispatchers.IO) {
                    var cursor: DocumentSnapshot? = null
                    while (true) {
                        val snapshot = firestoreRecordingsDao.getPaginated(100, cursor)
                        val docs = snapshot.documents
                        if (docs.isEmpty()) break
                        for (doc in docs) {
                            try {
                                val recording = doc.data<coredevices.indexai.data.entity.RecordingDocument>()
                                for (entry in recording.entries) {
                                    val fileName = entry.fileName ?: continue
                                    audioFileIds.add(fileName)
                                    audioFileIds.add("$fileName-original")
                                }
                            } catch (_: Exception) {}
                        }
                        cursor = docs.lastOrNull()
                    }
                }

                // Delete Firestore documents
                _backupStatus.value = "Deleting documents..."
                withContext(Dispatchers.IO) {
                    firestoreRecordingsDao.deleteAllRecordings()
                }

                // Delete audio files from Firebase Storage
                if (audioFileIds.isNotEmpty()) {
                    _backupStatus.value = "Deleting ${audioFileIds.size} audio files..."
                    withContext(Dispatchers.IO) {
                        for (id in audioFileIds) {
                            recordingStorage.deleteFromFirebaseStorage(id)
                        }
                    }
                    log.i { "Deleted ${audioFileIds.size} audio files from Storage" }
                }

                preferences.setLastBackupCount(0)
                _backupStatus.value = "Backup deleted"
                log.i { "All backup recordings and audio files deleted" }
            } catch (e: Exception) {
                log.e(e) { "Failed to delete backup" }
                _backupStatus.value = "Delete failed: ${e.message}"
            } finally {
                _backupLoading.value = false
            }
        }
    }

    fun setBackupEnabled(enabled: Boolean) {
        preferences.setBackupEnabled(enabled)
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    fun deleteLocalFeed() {
        // appScope: same as deleteBackup — must run to completion.
        appScope.launch {
            _backupLoading.value = true
            _backupStatus.value = "Deleting local feed..."
            val log = Logger.withTag("Backup")
            try {
                withContext(Dispatchers.IO) {
                    // Delete DB rows (cascades to entries + messages via foreign keys)
                    recordingRepository.deleteAllLocalRecordings()
                    // Delete cached recording metadata
                    recordingStorage.deleteAllCachedMetadata()
                    // Delete cached audio files from disk
                    recordingStorage.clearCacheDirectory()
                    // Wipe items + lists too — Firestore is unaffected
                    // because IndexFeedSyncService's push observer reads
                    // through the Room flow (a hard DELETE removes rows
                    // from the emission, no `deleted=true` tombstone is
                    // synthesized). On the next snapshot the pull
                    // listener re-ingests from Firestore.
                    itemRepository.deleteAllLocal()
                    listRepository.deleteAllLocal()
                }
                _backupStatus.value = "Local feed deleted"
                log.i { "All local feed data deleted (recordings, entries, items, lists, cache, metadata)" }
            } catch (e: Exception) {
                log.e(e) { "Failed to delete local feed" }
                _backupStatus.value = "Delete failed: ${e.message}"
            } finally {
                _backupLoading.value = false
            }
        }
    }

    // --- Encryption (keying ops in [EncryptionManager], UI status owned here) ---

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

    // --- Guided encryption setup (single switch, walks the user through
    //     generating or restoring a key before turning encryption on) ---

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

    // --- Full backup download ---

    private val _backupDownloadStatus = MutableStateFlow<String?>(null)
    val backupDownloadStatus = _backupDownloadStatus.asStateFlow()
    private val _backupDownloading = MutableStateFlow(false)
    val backupDownloading = _backupDownloading.asStateFlow()
    private val _backupZipPath = MutableStateFlow<Path?>(null)
    val backupZipPath = _backupZipPath.asStateFlow()

    private val backupJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun downloadFullBackup(uiContext: PlatformUiContext) {
        if (_backupDownloading.value) return
        viewModelScope.launch {
            _backupDownloading.value = true
            _backupDownloadStatus.value = "Starting backup..."
            val log = Logger.withTag("FullBackup")
            try {
                withContext(Dispatchers.IO) {
                    val user = Firebase.auth.currentUser
                        ?: throw Exception("Not signed in")
                    log.i { "Starting full backup for user ${user.uid}" }

                    // 1. Fetch all recording documents from Firestore
                    _backupDownloadStatus.value = "Fetching recording list..."
                    val allDocs = mutableListOf<Pair<String, RecordingDocument>>()
                    var cursor: DocumentSnapshot? = null
                    while (true) {
                        val snapshot = firestoreRecordingsDao.getPaginated(50, cursor)
                        val docs = snapshot.documents
                        if (docs.isEmpty()) break
                        for (doc in docs) {
                            try {
                                allDocs.add(doc.id to doc.data<RecordingDocument>())
                            } catch (e: Exception) {
                                log.w(e) { "Skipping malformed document ${doc.id}" }
                            }
                        }
                        cursor = docs.lastOrNull()
                        _backupDownloadStatus.value = "Found ${allDocs.size} recordings..."
                    }
                    log.i { "Found ${allDocs.size} recordings to backup" }

                    if (allDocs.isEmpty()) {
                        _backupDownloadStatus.value = "No recordings to backup"
                        return@withContext
                    }

                    // 2. Create zip file
                    val now = Clock.System.now()
                    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val zipName = "$today Pebble Index backup.zip"
                    val zipPath = Path(recordingStorage.getCacheDirectory(), zipName)
                    if (SystemFileSystem.exists(zipPath)) {
                        SystemFileSystem.delete(zipPath)
                    }
                    val zip = BackupZipWriter(zipPath)

                    // 3. Add manifest
                    val manifest = backupJson.encodeToString(
                        BackupManifest(
                            version = 1,
                            userId = user.uid,
                            email = user.email ?: "unknown",
                            exportedAt = now.toString(),
                            recordingCount = allDocs.size
                        )
                    )
                    zip.addEntry("manifest.json", manifest.encodeToByteArray())

                    // 4. For each recording, add document JSON + audio files
                    var downloaded = 0
                    var audioFiles = 0
                    var decryptSkipped = 0
                    for ((firestoreId, rawDoc) in allDocs) {
                        _backupDownloadStatus.value = "Backing up ${++downloaded}/${allDocs.size}..."

                        // Decrypt if encrypted — backup is always cleartext
                        val doc = if (rawDoc.encrypted != null) {
                            val key = documentEncryptor.getKey()
                            if (key == null) {
                                log.w { "Encrypted recording $firestoreId but no local key — skipping" }
                                decryptSkipped++
                                continue
                            }
                            try {
                                documentEncryptor.decryptDocument(rawDoc, key)
                            } catch (e: KeyFingerprintMismatchException) {
                                log.e { "Recording $firestoreId encrypted with key ${e.expected} but local key is ${e.actual} — skipping" }
                                decryptSkipped++
                                continue
                            } catch (e: TamperedException) {
                                log.e(e) { "Recording $firestoreId failed integrity check — skipping" }
                                decryptSkipped++
                                continue
                            }
                        } else rawDoc

                        // Add document JSON
                        val docJson = backupJson.encodeToString(RecordingDocument.serializer(), doc)
                        zip.addEntry("recordings/$firestoreId/document.json", docJson.encodeToByteArray())

                        // Download audio files for each entry
                        for (entry in doc.entries) {
                            val fileName = entry.fileName ?: continue
                            for (variant in listOf(fileName, "$fileName-original")) {
                                try {
                                    val (source, meta) = recordingStorage.openRecordingSource(variant)
                                    source.use { src ->
                                        val bytes = src.readByteArray()
                                        // Add metadata as a sidecar JSON
                                        val metaJson = "{\"sampleRate\":${meta.cachedMetadata.sampleRate},\"mimeType\":\"${meta.cachedMetadata.mimeType}\"}"
                                        zip.addEntry("recordings/$firestoreId/$variant.meta.json", metaJson.encodeToByteArray())
                                        zip.addEntry("recordings/$firestoreId/$variant.raw", bytes)
                                        audioFiles++
                                    }
                                } catch (e: Exception) {
                                    log.w { "Could not download audio $variant: ${e.message}" }
                                }
                            }
                        }
                    }

                    zip.close()
                    log.i { "Backup complete: $downloaded recordings, $audioFiles audio files, $decryptSkipped skipped due to decrypt failure" }
                    if (decryptSkipped > 0) {
                        _backupDownloadStatus.value =
                            "Backup complete — $decryptSkipped recordings skipped (key mismatch). Restore the original key and retry."
                    }
                    _backupZipPath.value = zipPath
                }
                // Save to Downloads via file picker
                val zipPath = _backupZipPath.value
                if (zipPath != null) {
                    _backupDownloadStatus.value = "Choose save location..."
                    try {
                        writeToDownloads(uiContext, zipPath, "application/zip")
                        _backupDownloadStatus.value = "Backup saved"
                    } catch (e: Exception) {
                        _backupDownloadStatus.value = "Backup created but save failed: ${e.message}"
                    } finally {
                        // Clean up temp zip
                        try { SystemFileSystem.delete(zipPath) } catch (_: Exception) {}
                        _backupZipPath.value = null
                    }
                }
            } catch (e: Exception) {
                log.e(e) { "Backup failed" }
                _backupDownloadStatus.value = "Backup failed: ${e.message}"
            } finally {
                _backupDownloading.value = false
            }
        }
    }

    fun clearBackupZipPath() {
        _backupZipPath.value = null
    }

    // --- Backup import ---

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus = _importStatus.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    fun importBackup(zipPath: Path) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            _importStatus.value = "Reading backup..."
            val log = Logger.withTag("BackupImport")
            try {
                withContext(Dispatchers.IO) {
                    val user = Firebase.auth.currentUser
                        ?: throw Exception("Not signed in")
                    log.i { "Starting backup import for user ${user.uid}" }

                    val reader = BackupZipReader(zipPath)
                    val allEntries = reader.readAllEntries()
                    reader.close()
                    log.i { "Read ${allEntries.size} zip entries" }

                    val entryMap = allEntries.associateBy { it.name }

                    // Parse recordings from zip
                    data class AudioFile(val variant: String, val data: ByteArray, val sampleRate: Int, val mimeType: String)
                    data class RecordingImport(val firestoreId: String, val doc: RecordingDocument, val audioFiles: List<AudioFile>)

                    val recordings = allEntries.mapNotNull { e ->
                        val parts = e.name.split("/")
                        if (parts.size >= 2 && parts[0] == "recordings") parts[1] else null
                    }.distinct().mapNotNull { dirId ->
                        val docEntry = entryMap["recordings/$dirId/document.json"] ?: return@mapNotNull null
                        val doc = try {
                            backupJson.decodeFromString(RecordingDocument.serializer(), docEntry.data.decodeToString())
                        } catch (e: Exception) {
                            log.w(e) { "Failed to parse document for $dirId" }
                            return@mapNotNull null
                        }
                        val audioFiles = doc.entries.flatMap { entry ->
                            val fileName = entry.fileName ?: return@flatMap emptyList()
                            listOf(fileName, "$fileName-original").mapNotNull { variant ->
                                val rawEntry = entryMap["recordings/$dirId/$variant.raw"] ?: return@mapNotNull null
                                val metaEntry = entryMap["recordings/$dirId/$variant.meta.json"]
                                var sampleRate = 16000; var mimeType = "audio/raw"
                                if (metaEntry != null) {
                                    val s = metaEntry.data.decodeToString()
                                    sampleRate = Regex("\"sampleRate\":(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 16000
                                    mimeType = Regex("\"mimeType\":\"([^\"]+)\"").find(s)?.groupValues?.get(1) ?: "audio/raw"
                                }
                                AudioFile(variant, rawEntry.data, sampleRate, mimeType)
                            }
                        }
                        RecordingImport(dirId, doc, audioFiles)
                    }

                    // Dedup by firestoreId only. Timestamp-based dedup
                    // was prone to cascading corruption when multiple
                    // recordings share an epoch-millisecond timestamp
                    // (notably ~520 of this user's docs that round-trip
                    // to epoch-0 on the Kotlin client) — see the same
                    // class of bug previously removed from
                    // performFeedHistoryDownload.
                    val existingFirestoreIds = recordingRepository.getAllFirestoreIds()

                    _importStatus.value = "Importing ${recordings.size} recordings..."
                    log.i { "Parsed ${recordings.size} recordings. ${existingFirestoreIds.size} already in local DB." }

                    var imported = 0
                    var skipped = 0
                    var audioUploaded = 0
                    var failed = 0
                    val counterMutex = kotlinx.coroutines.sync.Mutex()
                    val semaphore = Semaphore(6)
                    val encryptionKey = if (preferences.useEncryption.value) {
                        documentEncryptor.getKey().also { key ->
                            if (key == null) {
                                log.w { "Encryption is enabled, but no key is available during backup import; uploading audio unencrypted" }
                            }
                        }
                    } else {
                        null
                    }

                    coroutineScope {
                        recordings.map { rec ->
                            async {
                                semaphore.withPermit {
                                    try {
                                        // If this recording already exists locally (by firestoreId
                                        // — the only stable identifier across devices), skip the
                                        // cloud upload but still backfill entries/messages from the
                                        // document when the local row has none.
                                        val existingLocalRow = recordingRepository.getByFirestoreId(rec.firestoreId)
                                        val alreadyExists = existingLocalRow != null
                                        val localId = if (alreadyExists) {
                                            counterMutex.withLock { skipped++ }
                                            existingLocalRow?.id
                                        } else {
                                            // 1. Upload audio files to Firebase Storage (overwrite to fix partials)
                                            for (audio in rec.audioFiles) {
                                                recordingStorage.uploadRecordingPcm(
                                                    id = audio.variant,
                                                    sampleRate = audio.sampleRate,
                                                    pcmBytes = audio.data,
                                                    encryptionKey = encryptionKey,
                                                )
                                                counterMutex.withLock { audioUploaded++ }
                                            }

                                            // 2. Upload document to Firestore (preserve original ID).
                                            // Re-encrypt if this account uses encryption — exports are cleartext
                                            // but the cloud invariant is that encrypted users store encrypted docs.
                                            val docToUpload = if (encryptionKey != null) {
                                                documentEncryptor.encryptDocument(rec.doc, encryptionKey)
                                            } else {
                                                rec.doc
                                            }
                                            firestoreRecordingsDao.setRecording(rec.firestoreId, docToUpload)

                                            // 3. Create local feed entry (same as performFeedHistoryDownload)
                                            recordingRepository.createRecording(
                                                firestoreId = rec.firestoreId,
                                                localTimestamp = rec.doc.timestamp,
                                                assistantTitle = rec.doc.assistantSession?.title,
                                                updated = rec.doc.updated
                                            )
                                        }

                                        if (localId != null) {
                                            val existingEntries = recordingEntryDao.getEntriesForRecording(localId).first()
                                            if (existingEntries.isEmpty() && rec.doc.entries.isNotEmpty()) {
                                                recordingEntryDao.insertRecordingEntries(
                                                    rec.doc.entries.map { entry ->
                                                        RecordingEntryEntity(
                                                            recordingId = localId,
                                                            timestamp = entry.timestamp,
                                                            fileName = entry.fileName,
                                                            status = entry.status,
                                                            transcription = entry.transcription,
                                                            transcribedUsingModel = entry.transcribedUsingModel,
                                                            error = entry.error,
                                                            ringTransferInfo = entry.ringTransferInfo,
                                                            userMessageId = entry.userMessageId
                                                        )
                                                    }
                                                )
                                            }
                                            val messages = rec.doc.assistantSession?.messages
                                            if (!messages.isNullOrEmpty()) {
                                                val existingMessages = conversationMessageDao.getMessagesForRecording(localId).first()
                                                if (existingMessages.isEmpty()) {
                                                    conversationMessageDao.insertMessages(
                                                        messages.map { msg ->
                                                            ConversationMessageEntity(
                                                                recordingId = localId,
                                                                document = msg
                                                            )
                                                        }
                                                    )
                                                }
                                            }

                                            // Pin `updated` to the document's value — entry/message
                                            // inserts above auto-bump it to `now()`, which would
                                            // otherwise make the upload observer re-upload a
                                            // freshly-imported recording.
                                            recordingRepository.setRecordingUpdated(
                                                localId,
                                                Instant.fromEpochMilliseconds(rec.doc.updated)
                                            )
                                        }

                                        if (alreadyExists) {
                                            return@withPermit
                                        }

                                        val count = counterMutex.withLock { ++imported }
                                        if (count % 5 == 0 || count == recordings.size) {
                                            _importStatus.value = "Imported $count/${recordings.size}..."
                                        }
                                    } catch (e: Exception) {
                                        counterMutex.withLock { failed++ }
                                        log.e(e) { "Failed to import ${rec.firestoreId}: ${e.message}" }
                                    }
                                }
                            }
                        }.awaitAll()
                    }

                    val summary = buildString {
                        append("Done — $imported imported, $skipped already existed")
                        append(", $audioUploaded audio files")
                        if (failed > 0) append(", $failed failed")
                    }
                    log.i { summary }
                    _importStatus.value = summary
                }
            } catch (e: Exception) {
                log.e(e) { "Import failed" }
                _importStatus.value = "Import failed: ${e.message}"
            } finally {
                _importing.value = false
            }
        }
    }
}
