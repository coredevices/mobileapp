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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.eygraber.uri.Uri
import com.mmk.kmpnotifier.notification.NotifierManager
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
import coredevices.util.Permission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.compose.koinInject
import rememberOpenDocumentLauncher
import size
import kotlin.time.Clock

class ExperimentalDevices(
    private val ringSync: RingSync,
    private val recordingStorage: RecordingStorage,
    private val ringDelegate: RingDelegate,
    private val sandboxRepository: McpSandboxRepository,
    private val preferences: Preferences,
    private val shortcutActionHandler: ShortcutActionHandler,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
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

    fun handleDeepLink(uri: Uri): Boolean {
        return shortcutActionHandler.handleDeepLink(uri)
    }

    fun requiredRuntimePermissions(): Set<Permission> {
        return ringDelegate.requiredRuntimePermissions()
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
        LaunchedEffect(Unit) {
            topBarParams.title("Index")
            topBarParams.searchAvailable(null)
            topBarParams.actions {
                BugReportButton(
                    coreNav,
                    pebble = false,
                    screenContext = mapOf("screen" to "FeedTab")
                )
                if (isDebugEnabled) {
                    IconButton(
                        onClick = {
                            launchWavImportDialog(listOf("audio/*"))
                        }
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = "Debug")
                    }
                }
                IconButton(
                    onClick = {
                        coreNav.navigateTo(RingRoutes.Settings)
                    }
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
        FeedTabContents(
            topBarParams,
            PaddingValues.Zero,
            coreNav,
            onAddChat = { message ->
                scope.launch {
                    recordingQueue.queueTextProcessing(message)
                }
            }
        )
    }

    suspend fun exportOutput(id: String): DocumentAttachment? {
        val path = recordingStorage.exportRecording(id)
        val source = SystemFileSystem.source(path).buffered()
        return DocumentAttachment(
            fileName = "recording.wav",
            mimeType = "audio/wav",
            source = source.buffered(),
            size = path.size(),
        )
    }

    fun debugSummary(): String? {
        return ringSync.lastRingSummary()
    }
}