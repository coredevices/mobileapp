package coredevices.ring.ui.screens

import BugReportButton
import CoreNav
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.pebble.ui.TopBarParams
import coredevices.ring.database.Preferences
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.screens.home.IndexFeedScreen
import coredevices.ring.ui.theme.IndexThemeHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import org.koin.compose.koinInject
import rememberOpenDocumentLauncher
import kotlin.time.Clock

@Composable
fun IndexScreen(coreNav: CoreNav, topBarParams: TopBarParams) {
    val recordingQueue = koinInject<RecordingProcessingQueue>()
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
    DisposableEffect(Unit) {
        topBarParams.title("")
        topBarParams.searchAvailable(null)
        topBarParams.actions { /* moved into IndexFeedScreen.IndexHeader */ }
        onDispose { /* nothing to clean up */ }
    }
    IndexThemeHost {
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
    }
}