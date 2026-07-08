package coredevices

import BugReportButton
import CoreNav
import DocumentAttachment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.eygraber.uri.Uri
import com.mmk.kmpnotifier.notification.NotifierManager
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.libindex.LibIndex
import coredevices.libindex.device.IndexPlatformBluetoothAssociations
import coredevices.ring.bugreport.RecentRecordingExport
import coredevices.pebble.ui.TopBarParams
import coredevices.ring.RingDelegate
import coredevices.ring.agent.ShortcutActionHandler
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.RingSync
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.navigation.addRingRoutes
import coredevices.ring.ui.screens.home.FeedTabContents
import coredevices.ring.ui.screens.home.IndexFeedScreen
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.collect
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
import org.koin.compose.koinInject
import rememberOpenDocumentLauncher
import size
import kotlin.time.Clock

class ExperimentalDevices(
    private val ringSync: RingSync,
    private val recordingStorage: RecordingStorage,
    private val ringDelegate: RingDelegate,
    private val sandboxRepository: McpSandboxRepository,
    private val recordingRepository: RecordingRepository,
    private val conversationMessageDao: ConversationMessageDao,
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
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    fun appInit() {
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
        withContext(Dispatchers.IO) {
            sandboxRepository.seedDatabase()
        }
        ringDelegate.init()
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

    fun addExperimentalRoutes(builder: NavGraphBuilder, coreNav: CoreNav) {
        builder.addRingRoutes(coreNav)
    }

    fun badCollectionsDir(): Path? = RingSync.badCollectionsDir

    @Composable
    fun IndexScreen(coreNav: CoreNav, topBarParams: TopBarParams) {
        val recordingQueue = koinInject<RecordingProcessingQueue>()
        val recordingRepo = koinInject<RecordingRepository>()
        val recordingStorage = koinInject<RecordingStorage>()
        val prefs = koinInject<Preferences>()
        val isDebugEnabled by prefs.debugDetailsEnabled.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val launchWavImportDialog = rememberOpenDocumentLauncher {
            it?.firstOrNull()?.let { file ->
                val id = "imported-${Clock.System.now()}"
                scope.launch(Dispatchers.IO) {
                    recordingStorage.openRecordingSink(
                        id = id,
                        sampleRate = 16000,
                        mimeType = "audio/wav",
                    ).buffered().use { sink ->
                        file.source.buffered().use {
                            it.skip(44) // Skip WAV header
                            it.transferTo(sink)
                        }
                    }
                    recordingQueue.queueLocalAudioProcessing(id)
                    topBarParams.showSnackbar("Imported WAV file")
                }
            }
        }
        // The chrome's TopAppBar is hidden tab-wide by WatchHomeScreen
        // whenever currentTab == Index, so we don't manage `setHidden`
        // here — doing it per-screen would race with detail screens
        // (their own DetailTopBar + the chrome would show double until
        // the next compositional pass).
        androidx.compose.runtime.DisposableEffect(Unit) {
            topBarParams.title("")
            topBarParams.searchAvailable(null)
            topBarParams.actions { /* moved into IndexFeedScreen.IndexHeader */ }
            onDispose { /* nothing to clean up */ }
        }
        IndexFeedScreen(
            coreNav = coreNav,
            scrollToTop = topBarParams.scrollToTop,
            headerActions = {
                BugReportButton(
                    coreNav,
                    pebble = false,
                    screenContext = mapOf("screen" to "IndexFeed"),
                )
                if (isDebugEnabled) {
                    IconButton(
                        onClick = { launchWavImportDialog(listOf("audio/*")) },
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = "Debug")
                    }
                }
                IconButton(
                    onClick = { coreNav.navigateTo(RingRoutes.Settings) },
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
        )
        // (Legacy `FeedTabContents` is no longer referenced from here.
        // It used to be kept-alive via a `::FeedTabContents` callable
        // reference, but Kotlin/Native 2.3 crashes during IR lowering on
        // `@Composable` function references whose arity exceeds Function6
        // — KT-bug, "Unexpected number of type arguments". The function
        // is public top-level so no static analysis will drop it; the
        // callable-ref keep-alive was always cosmetic.)
    }

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
     * entries and conversation messages (the data shown in `RecordingDetails`),
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

            RecentRecordingExport(recording, entries, messages)
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

    fun debugSummary(): String {
        return buildString {
            ringSync.lastRingSummary()?.let {
                append(it)
                append("\n")
            }
            append("Cactus agent enabled: ${preferences.useCactusAgent.value}")
        }
    }
}