package coredevices.ring.ui.viewmodel

import PlatformUiContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.firestore.EncryptionInfo
import coredevices.firestore.UsersDao
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.RecordingDocument
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.libindex.device.IndexDeviceManager
import coredevices.libindex.device.InterviewedIndexDevice
import coredevices.libindex.device.KnownIndexDevice
import coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.encryption.DocumentEncryptor
import coredevices.ring.encryption.EncryptionKeyManager
import coredevices.ring.encryption.KeyFingerprintMismatchException
import coredevices.ring.encryption.TamperedException
import coredevices.ring.service.RingSync
import coredevices.ring.storage.BackupZipReader
import coredevices.ring.storage.BackupZipWriter
import coredevices.ring.storage.RecordingStorage
import coredevices.ui.ModelType
import coredevices.util.CommonBuildKonfig
import coredevices.util.Platform
import coredevices.util.emailOrNull
import coredevices.util.isAndroid
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
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
import kotlinx.coroutines.sync.Mutex
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
    private val usersDao: UsersDao,
    private val platform: Platform,
    private val ringSync: RingSync,
    private val preferences: Preferences,
    private val firestoreRecordingsDao: FirestoreRecordingsDao,
    private val recordingRepository: RecordingRepository,
    private val recordingEntryDao: RecordingEntryDao,
    private val conversationMessageDao: ConversationMessageDao,
    private val encryptionKeyManager: EncryptionKeyManager,
    private val recordingStorage: RecordingStorage,
    private val documentEncryptor: DocumentEncryptor,
    private val noteIntegrationFactory: NoteIntegrationFactory,
    private val gTasksIntegration: GTasksIntegration,
    private val indexDeviceManager: IndexDeviceManager,
    private val itemRepository: coredevices.ring.database.room.repository.ItemRepository,
    private val listRepository: coredevices.ring.database.room.repository.ListRepository,
    private val indexFeedSyncService: coredevices.ring.service.indexfeed.IndexFeedSyncService,
    private val recordingProcessingQueue: coredevices.ring.service.recordings.RecordingProcessingQueue,
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
            add(ReminderProvider.Native)
            if (gTasksIntegration.isAuthorized()) {
                add(ReminderProvider.GoogleTasks)
            }
        }
    }

    fun onModelDownloadDialogDismissed(success: Boolean) {
        val wasDownloading = _showModelDownloadDialog.value ?: return
        _showModelDownloadDialog.value = null
        viewModelScope.launch {
            when (wasDownloading) {
                is ModelType.Agent -> preferences.setUseCactusAgent(success)
                is ModelType.STT -> preferences.setUseCactusTranscription(success)
            }
        }
    }
    
    fun toggleCactusAgent() {
        viewModelScope.launch {
            if (!_useCactusAgent.value) {
                _showModelDownloadDialog.value = ModelType.Agent(CommonBuildKonfig.CACTUS_LM_MODEL_NAME)
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

    fun setSecondaryMode(mode: SecondaryMode) {
        preferences.setSecondaryMode(mode)
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

    fun downloadFeedHistory() {
        if (_syncingFeedHistory.value) return
        // Bail before the sub-steps run — otherwise the unconditional
        // "Sync complete" at the end of `try` overwrites the
        // "Not signed in" status that performFeedHistoryDownload sets.
        if (Firebase.auth.currentUser == null) {
            _syncStatus.value = "Not signed in"
            return
        }
        viewModelScope.launch {
            _syncingFeedHistory.value = true
            _syncStatus.value = "Starting sync..."
            try {
                withContext(Dispatchers.IO) {
                    uploadPendingRecordings()
                    performFeedHistoryDownload()
                    // Items + lists ride the bidirectional syncher,
                    // which is also continuously running in the
                    // background. This is the manual "force a full
                    // reconciliation" entry point.
                    indexFeedSyncService.syncNow()
                }
                // Single final "done" status. Each sub-step sets its own
                // in-flight label ("Uploading 22 items...", "Repairing 15
                // item links...") and there's no easy way to know which
                // step ran last (the early-return path of repair, the
                // empty-set path of upload, etc.) — so a freeze on a
                // mid-step label is too easy. One unconditional final
                // string after the whole `try` block kills it.
                _syncStatus.value = "Sync complete"
            } catch (e: Exception) {
                Logger.withTag("FeedHistorySync").e(e) { "Feed history sync failed" }
                _syncStatus.value = "Sync failed: ${e.message}"
            } finally {
                _syncingFeedHistory.value = false
            }
        }
    }

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

    private suspend fun performFeedHistoryDownload() {
        val log = Logger.withTag("FeedHistorySync")

        val user = Firebase.auth.currentUser
        if (user == null) {
            log.w { "Not signed in — cannot sync feed history" }
            _syncStatus.value = "Not signed in"
            return
        }
        log.i { "Feed history sync started for user ${user.uid} (${user.email})" }
        log.i { "Firestore path: recordings/${user.uid}/recordings" }
        _syncStatus.value = "Fetching from cloud..."

        var cursor: dev.gitlive.firebase.firestore.DocumentSnapshot? = null
        var totalApplied = 0
        var totalRemote = 0
        // Per-page docs are processed concurrently — ingestRemoteRecording
        // is independent per doc (decrypt + Room insert + child rows).
        // Room serialises its own writes at the SQLite layer; the
        // ingestor is also idempotent (compares remote.updated vs
        // local.updated and no-ops when local is at-or-newer), so a
        // concurrent re-fire from the auto-pull snapshot listener can't
        // produce duplicates.
        val mutex = Mutex()

        while (true) {
            val snapshot = firestoreRecordingsDao.getPaginated(50, cursor)
            val docs = snapshot.documents
            if (docs.isEmpty()) break
            totalRemote += docs.size

            coroutineScope {
                docs.map { doc ->
                    async {
                        try {
                            val data = doc.data<RecordingDocument>()
                            val before = recordingRepository.getByFirestoreId(doc.id)
                            try {
                                recordingProcessingQueue.ingestRemoteRecording(doc.id, data)
                            } catch (e: KeyFingerprintMismatchException) {
                                log.e { "Recording ${doc.id} encrypted with key ${e.expected} but local key is ${e.actual} — restore the original key" }
                                _syncStatus.value = "Key mismatch — restore the original encryption key"
                                return@async
                            } catch (e: TamperedException) {
                                log.e(e) { "Recording ${doc.id} failed integrity check" }
                                _syncStatus.value = "Recording ${doc.id} failed integrity check"
                                return@async
                            }
                            val after = recordingRepository.getByFirestoreId(doc.id)
                            // Count an "apply" when the row was created
                            // OR when its updated stamp moved forward.
                            if (before == null || (after != null && after.updated != before.updated)) {
                                val n = mutex.withLock { ++totalApplied }
                                _syncStatus.value = "Applied $n recordings..."
                            }
                        } catch (e: Exception) {
                            log.w(e) { "Skipping recording ${doc.id}: ${e.message}" }
                        }
                    }
                }.awaitAll()
            }

            log.i { "Progress: fetched $totalRemote remote, applied $totalApplied" }
            cursor = docs.lastOrNull()
        }

        log.i { "Feed history sync complete: applied $totalApplied of $totalRemote remote" }
        // Cache the cloud-side count so the Settings → Backup dialog can
        // display it instantly without re-paginating the entire collection
        // (the prior implementation downloaded every full document body
        // on every dialog open, which on a 1300+ recording user took a
        // minute over mobile).
        preferences.setLastBackupCount(totalRemote)
        _syncStatus.value = "Done — applied $totalApplied of $totalRemote recordings"
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            _backupLoading.value = true
            _backupStatus.value = "Deleting backup..."
            val log = Logger.withTag("Backup")
            try {
                // Collect all audio file names before deleting documents
                _backupStatus.value = "Collecting audio files..."
                val audioFileIds = mutableListOf<String>()
                withContext(Dispatchers.IO) {
                    var cursor: dev.gitlive.firebase.firestore.DocumentSnapshot? = null
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
                                    audioFileIds.add("$fileName-clean")
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
        if (enabled) {
            downloadFeedHistory()
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    fun deleteLocalFeed() {
        viewModelScope.launch {
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

    // --- Encryption key management ---

    private val _encryptionKeyStatus = MutableStateFlow<String?>(null)
    val encryptionKeyStatus = _encryptionKeyStatus.asStateFlow()
    private val _hasLocalKey = MutableStateFlow(false)
    val hasLocalKey = _hasLocalKey.asStateFlow()
    private val _encryptionKeyLoading = MutableStateFlow(false)
    val encryptionKeyLoading = _encryptionKeyLoading.asStateFlow()
    private val _generatedKey = MutableStateFlow<String?>(null)
    val generatedKey = _generatedKey.asStateFlow()

    fun checkLocalKey() {
        viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { encryptionKeyManager.getLocalKey() }
            _hasLocalKey.value = key != null
        }
    }

    fun generateAndStoreKey(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            _encryptionKeyLoading.value = true
            _encryptionKeyStatus.value = "Generating AES-256 encryption key..."
            try {
                val keyResult = encryptionKeyManager.generateKey()

                val email = Firebase.auth.currentUser?.email ?: "unknown"
                _encryptionKeyStatus.value = "Saving key locally..."
                withContext(Dispatchers.IO) {
                    encryptionKeyManager.saveKeyLocally(keyResult.keyBase64, email)
                }

                var backupLocation = "local_only"
                _encryptionKeyStatus.value = "Saving to Password Manager..."
                try {
                    encryptionKeyManager.saveToCloudKeychain(uiContext, keyResult.keyBase64)
                    backupLocation = if (platform.isAndroid) "google_password_manager" else "icloud_keychain"
                } catch (e: Exception) {
                    Logger.withTag("EncryptionKey").w(e) { "Cloud keychain save failed (key still saved locally)" }
                    _encryptionKeyStatus.value = "Key generated. Password Manager: ${e.message}"
                }

                val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
                val now = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMs)
                val encryptionInfo = EncryptionInfo(
                    keyFingerprint = keyResult.fingerprint,
                    createdAt = now.toString(),
                    keyBackupLocation = backupLocation
                )

                _encryptionKeyStatus.value = "Storing encryption info in cloud..."
                withContext(Dispatchers.IO) {
                    usersDao.updateEncryptionInfo(encryptionInfo)
                    preferences.setEncryptionKeyFingerprint(keyResult.fingerprint)
                }

                _hasLocalKey.value = true
                _generatedKey.value = keyResult.keyBase64
                if (_encryptionKeyStatus.value?.startsWith("Key generated.") != true) {
                    _encryptionKeyStatus.value = "Encryption key generated and saved"
                }
                Logger.withTag("EncryptionKey").i { "Key generated, fingerprint=${keyResult.fingerprint}, backup=$backupLocation" }
            } catch (e: Exception) {
                Logger.withTag("EncryptionKey").e(e) { "Key generation failed" }
                _encryptionKeyStatus.value = "Failed: ${e.message}"
            } finally {
                _encryptionKeyLoading.value = false
            }
        }
    }

    fun readKeyFromCloudKeychain(uiContext: PlatformUiContext) {
        viewModelScope.launch {
            _encryptionKeyLoading.value = true
            _encryptionKeyStatus.value = "Reading from Password Manager..."
            try {
                val key = encryptionKeyManager.readFromCloudKeychain(uiContext)
                if (key != null) {
                    val email = Firebase.auth.currentUser?.email ?: "unknown"
                    withContext(Dispatchers.IO) {
                        encryptionKeyManager.saveKeyLocally(key, email)
                    }
                    _hasLocalKey.value = true
                    _encryptionKeyStatus.value = "Key restored from Password Manager"
                    Logger.withTag("EncryptionKey").i { "Key restored from cloud keychain" }
                }
            } catch (e: Exception) {
                Logger.withTag("EncryptionKey").e(e) { "Failed to read from cloud keychain" }
                _encryptionKeyStatus.value = e.message
            } finally {
                _encryptionKeyLoading.value = false
            }
        }
    }

    fun clearEncryptionKeyStatus() {
        _encryptionKeyStatus.value = null
    }

    fun clearGeneratedKey() {
        _generatedKey.value = null
    }

    // --- Encryption toggle ---

    val useEncryption = preferences.useEncryption

    private val _migrationStatus = MutableStateFlow<String?>(null)
    val migrationStatus = _migrationStatus.asStateFlow()
    private val _migrating = MutableStateFlow(false)
    val migrating = _migrating.asStateFlow()

    fun enableEncryption() {
        viewModelScope.launch {
            _migrating.value = true
            val log = Logger.withTag("EncryptionMigration")
            try {
                val key = withContext(Dispatchers.IO) { encryptionKeyManager.getLocalKey() }
                if (key == null) {
                    _migrationStatus.value = "No encryption key — generate one first"
                    return@launch
                }

                // Step 1: Download all remote recordings to local DB
                _migrationStatus.value = "Syncing from cloud..."
                withContext(Dispatchers.IO) { performFeedHistoryDownload() }

                // Step 3: Cache all audio files locally (so we have plaintext to encrypt)
                _migrationStatus.value = "Caching audio files..."
                val allRecordings = withContext(Dispatchers.IO) {
                    recordingRepository.getAllRecordings().first()
                }
                val allAudioIds = mutableListOf<String>()
                for (recording in allRecordings) {
                    val entries = withContext(Dispatchers.IO) {
                        recordingEntryDao.getEntriesForRecording(recording.id).first()
                    }
                    for (entry in entries) {
                        val fileName = entry.fileName ?: continue
                        for (variant in listOf(fileName, "$fileName-clean")) {
                            try {
                                val (source, _) = withContext(Dispatchers.IO) {
                                    recordingStorage.openRecordingSource(variant)
                                }
                                source.close()
                                allAudioIds.add(variant)
                            } catch (e: Exception) {
                                log.w { "Could not cache audio $variant: ${e.message}" }
                            }
                        }
                    }
                }
                log.i { "Cached ${allAudioIds.size} audio files locally" }

                // Step 4: Enable encryption preference
                preferences.setUseEncryption(true)

                // Step 5: Encrypt and re-upload audio files (overwrites in-place)
                _migrationStatus.value = "Encrypting audio files..."
                var audioEncrypted = 0
                for (audioId in allAudioIds) {
                    try {
                        val success = withContext(Dispatchers.IO) {
                            recordingStorage.encryptAndReuploadAudio(audioId, key)
                        }
                        if (success) audioEncrypted++
                        _migrationStatus.value = "Encrypting audio $audioEncrypted/${allAudioIds.size}..."
                    } catch (e: Exception) {
                        log.w(e) { "Failed to encrypt audio $audioId" }
                    }
                }
                log.i { "Encrypted $audioEncrypted/${allAudioIds.size} audio files" }

                // Step 6: Re-upload all documents encrypted IN PLACE.
                // We overwrite the existing Firestore doc via set() rather
                // than addRecording() so the firestoreId stays stable.
                // Reassigning firestoreIds would orphan every item that
                // references this recording via its `sourceRecordingId`.
                _migrationStatus.value = "Encrypting documents..."
                var uploaded = 0
                for (recording in allRecordings) {
                    val firestoreId = recording.firestoreId
                    if (firestoreId.isNullOrBlank()) {
                        // Legacy row that was never uploaded. Skip — the
                        // upload observer will pick it up later and
                        // upload it pre-encrypted.
                        log.w { "Skipping recording ${recording.id} during encryption migration: no firestoreId" }
                        continue
                    }
                    try {
                        val entries = withContext(Dispatchers.IO) {
                            recordingEntryDao.getEntriesForRecording(recording.id).first()
                        }
                        val messages = withContext(Dispatchers.IO) {
                            conversationMessageDao.getMessagesForRecording(recording.id).first()
                        }
                        // Preserve any metadata already on the remote doc.
                        val preservedMetadata = try {
                            withContext(Dispatchers.IO) {
                                firestoreRecordingsDao.getRecording(firestoreId).get()
                                    .data<RecordingDocument>().metadata
                            }
                        } catch (e: Exception) { null }
                        var doc = recording.toDocument(
                            entries = entries.map { entry ->
                                coredevices.indexai.data.entity.RecordingEntry(
                                    timestamp = entry.timestamp,
                                    fileName = entry.fileName,
                                    status = entry.status,
                                    transcription = entry.transcription,
                                    transcribedUsingModel = entry.transcribedUsingModel,
                                    error = entry.error,
                                    ringTransferInfo = entry.ringTransferInfo,
                                    userMessageId = entry.userMessageId
                                )
                            },
                            messages = messages.map { it.document },
                            metadata = preservedMetadata,
                        )
                        doc = documentEncryptor.encryptDocument(doc, key)
                        withContext(Dispatchers.IO) {
                            firestoreRecordingsDao.setRecording(firestoreId, doc)
                        }
                        uploaded++
                        _migrationStatus.value = "Encrypted docs $uploaded/${allRecordings.size}..."
                    } catch (e: Exception) {
                        log.w(e) { "Failed to re-upload recording ${recording.id}" }
                    }
                }

                log.i { "Encryption migration complete: $uploaded docs, $audioEncrypted audio files" }
                _migrationStatus.value = "Encryption enabled — $uploaded docs, $audioEncrypted audio files encrypted"
            } catch (e: Exception) {
                log.e(e) { "Encryption migration failed" }
                _migrationStatus.value = "Migration failed: ${e.message}"
            } finally {
                _migrating.value = false
            }
        }
    }

    fun disableEncryption() {
        preferences.setUseEncryption(false)
        _migrationStatus.value = "Encryption disabled — future uploads will be unencrypted"
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

                    // 0. Sync local recordings to cloud first
                    _backupDownloadStatus.value = "Syncing local recordings to cloud..."
                    uploadPendingRecordings()

                    // 1. Fetch all recording documents from Firestore
                    _backupDownloadStatus.value = "Fetching recording list..."
                    val allDocs = mutableListOf<Pair<String, RecordingDocument>>()
                    var cursor: dev.gitlive.firebase.firestore.DocumentSnapshot? = null
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
                    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    val kdtInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMs)
                    val today = kdtInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date
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
                            exportedAt = kdtInstant.toString(),
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
                            for (variant in listOf(fileName, "$fileName-clean")) {
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
                            listOf(fileName, "$fileName-clean").mapNotNull { variant ->
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
