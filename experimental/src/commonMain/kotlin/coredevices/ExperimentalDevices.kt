package coredevices

import DocumentAttachment
import com.eygraber.uri.Uri
import com.mmk.kmpnotifier.notification.NotifierManager
import coredevices.indexai.database.dao.AgentIterationDao
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.libindex.LibIndex
import coredevices.ring.RingDelegate
import coredevices.ring.agent.ShortcutActionHandler
import coredevices.ring.agent.builtin_servlets.calendar.TargetCalendarSeeder
import coredevices.ring.bugreport.IndexRebootLogEntry
import coredevices.ring.bugreport.IndexRebootLogStore
import coredevices.ring.bugreport.IndexSettingsSummary
import coredevices.ring.bugreport.RecentRecordingExport
import coredevices.ring.bugreport.RingRssiStatistics
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.IndexNotificationManager
import coredevices.ring.service.RingSync
import coredevices.ring.storage.RecordingStorage
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.Platform
import coredevices.util.isAndroid
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import size

class ExperimentalDevices(
    private val ringSync: RingSync,
    private val recordingStorage: RecordingStorage,
    private val ringDelegate: RingDelegate,
    private val recordingRepository: RecordingRepository,
    private val conversationMessageDao: ConversationMessageDao,
    private val agentIterationDao: AgentIterationDao,
    private val preferences: Preferences,
    private val shortcutActionHandler: ShortcutActionHandler,
    private val libIndex: LibIndex,
    private val permissionRequester: PermissionRequester,
    /** Touched here so Koin instantiates the singleton at app start;
     *  the syncer's init block attaches its observers immediately and
     *  runs for the rest of the process lifetime (mirrors how
     *  RecordingProcessingQueue's recording observer kicks off). */
    private val indexFeedSyncService: coredevices.ring.service.indexfeed.IndexFeedSyncService,
    private val defaultListsBootstrap: coredevices.ring.service.indexfeed.DefaultListsBootstrap,
    private val indexSettingsSummary: IndexSettingsSummary,
    private val rebootLogStore: IndexRebootLogStore,
    private val rssiStatistics: RingRssiStatistics,
    private val platform: Platform,
    private val targetCalendarSeeder: TargetCalendarSeeder,
    private val indexNotificationManager: IndexNotificationManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    fun appInit() {
        ringDelegate.appInit()
        libIndex.init(
            permissionRequester.missingPermissions.distinctUntilChanged { old, new ->
                (Permission.Bluetooth in old && Permission.Bluetooth !in new) || (Permission.Bluetooth !in old && Permission.Bluetooth in new)
            }.map {
                Permission.Bluetooth !in it
            }
        )
        indexFeedSyncService.hashCode()
        // Self-healing: creates the three system seed lists in Firestore
        // (Notes-to-self / Todos / Shopping) if any are missing. Idempotent.
        // Runs after each auth event because [DefaultListsBootstrap] reads
        // its own auth state internally; we kick once at app start and
        // again whenever auth changes via the snapshot listener flow.
        scope.launch {
            flow {
                emit(Firebase.auth.currentUser)
                Firebase.auth.authStateChanged.collect { emit(it) }
            }.distinctUntilChanged { old: FirebaseUser?, new: FirebaseUser? ->
                old?.uid == new?.uid
            }.collect { user ->
                if (user != null) {
                    try { defaultListsBootstrap.ensure() } catch (e: Exception) {
                        co.touchlab.kermit.Logger.withTag("ExperimentalDevices")
                            .w(e) { "DefaultListsBootstrap.ensure() failed" }
                    }
                }
            }
        }
    }

    suspend fun init() {
        ringDelegate.init()
        scope.launch { targetCalendarSeeder.seedIfNeeded() }
        scope.launch { indexNotificationManager.notifyLegacyDestinationUse() }
        if (preferences.ringPairedOld.value && preferences.ringPaired.value == null) {
            // Prompt user to re-pair to migrate
            NotifierManager.getLocalNotifier().notify {
                title = "Re-pairing required"
                body = "Please re-pair your Index 01 device to continue using it."
            }
        }
    }

    fun onBackgroundSync() {
        ringDelegate.onBackgroundSync()
    }

    fun handleDeepLink(uri: Uri): Boolean {
        return shortcutActionHandler.handleDeepLink(uri)
    }
    fun badCollectionsDir(): Path? = RingSync.badCollectionsDir

    suspend fun exportOutput(id: String): List<DocumentAttachment> {
        val logger = co.touchlab.kermit.Logger.withTag("ExperimentalDevices")
        val attachments = mutableListOf<DocumentAttachment>()
        // Export both the processed audio and the original raw capture for the
        // reported recording, so the bug report carries both for comparison.
        for (useOriginal in listOf(false, true)) {
            try {
                val path = recordingStorage.exportRecording(id, useOriginalAudio = useOriginal)
                val suffix = if (useOriginal) "-original" else ""
                attachments.add(
                    DocumentAttachment(
                        fileName = "recording$suffix.wav",
                        mimeType = "audio/wav",
                        source = SystemFileSystem.source(path).buffered(),
                        size = path.size(),
                    )
                )
            } catch (e: Exception) {
                val variant = if (useOriginal) "original" else "processed"
                logger.w(e) { "Failed to export $variant audio for recording $id" }
            }
        }
        return attachments
    }

    /**
     * Export the most recent [limit] recordings for a bug report: one
     * `recent_recordings.json` capturing each recording's [LocalRecording],
     * entries, conversation messages (the data shown in `RecordingDetails`) and agent iterations,
     * plus one WAV per recording entry that has audio. Audio export per entry is
     * best-effort — an un-uploaded or missing file is skipped, not fatal.
     */
    suspend fun exportRecentRecordings(limit: Int = 10): List<DocumentAttachment> = withContext(Dispatchers.IO) {
        val logger = co.touchlab.kermit.Logger.withTag("ExperimentalDevices")
        val recordings = recordingRepository.getRecentRecordings(limit)
        if (recordings.isEmpty()) return@withContext emptyList()

        val attachments = mutableListOf<DocumentAttachment>()
        val exports = recordings.map { recording ->
            val entries = recordingRepository.getRecordingEntriesFlow(recording.id).first()
            val messages = conversationMessageDao.getMessagesForRecording(recording.id).first()
            val iterations = agentIterationDao.getForRecording(recording.id).first()

            entries.mapNotNull { it.fileName }.distinct().forEach { fileName ->
                // Export both the processed audio (what was transcribed) and the
                // original raw capture, so bug reports carry both for comparison.
                for (useOriginal in listOf(false, true)) {
                    try {
                        val path = recordingStorage.exportRecording(fileName, useOriginalAudio = useOriginal)
                        val suffix = if (useOriginal) "-original" else ""
                        attachments.add(
                            DocumentAttachment(
                                fileName = "recording-${recording.id}-$fileName$suffix.wav",
                                mimeType = "audio/wav",
                                source = SystemFileSystem.source(path).buffered(),
                                size = path.size(),
                            )
                        )
                    } catch (e: Exception) {
                        val variant = if (useOriginal) "original" else "processed"
                        logger.w(e) { "Failed to export $variant audio for recording ${recording.id} ($fileName)" }
                    }
                }
            }

            RecentRecordingExport(recording, entries, messages, iterations)
        }

        val json = Json.encodeToString(exports)
        val buffer = Buffer().apply { writeString(json) }
        attachments.add(
            DocumentAttachment(
                fileName = "recent_recordings.json",
                mimeType = "application/json",
                source = buffer,
                size = buffer.size,
            )
        )
        attachments
    }

    fun rebootLog(): List<IndexRebootLogEntry> = rebootLogStore.entries()

    suspend fun debugSummary(): String {
        return buildString {
            ringSync.lastRingSummary()?.let {
                append(it)
                append("\n")
            }
            append("Index Debug enabled: ${preferences.debugDetailsEnabled.value}\n")
            append("Secondary profile: ${platform.isSecondaryProfile}\n")
            if (platform.isAndroid) {
                append("PendingIntent scan enabled: ${preferences.usePendingIntentScan.value}\n")
                append("Scan by paired address: ${preferences.scanByPairedAddress.value}\n")
            }
            append(runCatching { indexSettingsSummary.summary() }
                .getOrElse { "\nIndex Settings unavailable: ${it.message}" })
            append("\n")
            append(runCatching { rssiStatistics.summary() }
                .getOrElse { "Ring Rx RSSI statistics unavailable: ${it.message}\n" })
        }
    }
}