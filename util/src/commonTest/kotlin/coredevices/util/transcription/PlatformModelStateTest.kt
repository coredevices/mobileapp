package coredevices.util.transcription

import coredevices.util.models.ModelDownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformModelStateTest {
    private val idle = ModelDownloadStatus.Idle
    private val failed = ModelDownloadStatus.Failed("platform-native", "offline")
    private val downloading = ModelDownloadStatus.Downloading("platform-native", 0.42f)
    private val scheduled = ModelDownloadStatus.Scheduled("platform-native")

    @Test
    fun onlyNeedsADownloadWhenNothingIsFetchingIt() {
        assertTrue(platformModelNeedsDownload(SpeechModelAvailability.NotDownloaded, idle))
        assertFalse(platformModelNeedsDownload(SpeechModelAvailability.NotDownloaded, downloading))
        assertFalse(platformModelNeedsDownload(SpeechModelAvailability.NotDownloaded, scheduled))
        assertFalse(platformModelNeedsDownload(SpeechModelAvailability.Downloading, idle))
        assertFalse(platformModelNeedsDownload(SpeechModelAvailability.Installed, idle))
        assertFalse(platformModelNeedsDownload(SpeechModelAvailability.Unsupported, idle))
        // A failed attempt can be retried.
        assertTrue(platformModelNeedsDownload(SpeechModelAvailability.NotDownloaded, failed))
    }

    @Test
    fun stateLabelPrefersTheAppsOwnDownloadOverTheSystemView() {
        assertEquals("Downloading 42%", platformModelState(SpeechModelAvailability.NotDownloaded, downloading))
        assertEquals("Downloading…", platformModelState(SpeechModelAvailability.Downloading, idle))
        assertEquals("Download scheduled", platformModelState(SpeechModelAvailability.NotDownloaded, scheduled))
        assertEquals("Download failed", platformModelState(SpeechModelAvailability.NotDownloaded, failed))
        assertEquals("Downloaded", platformModelState(SpeechModelAvailability.Installed, idle))
        assertEquals("Not downloaded", platformModelState(SpeechModelAvailability.NotDownloaded, idle))
        assertEquals("Not supported", platformModelState(SpeechModelAvailability.Unsupported, idle))
    }

    @Test
    fun dialogMessageExplainsTheWaitWhileDownloading() {
        val waiting = platformModelDialogMessage("nl", SpeechModelAvailability.Downloading, idle)
        assertTrue("downloading" in waiting && "Dutch" in waiting)
        assertTrue("downloading" in platformModelDialogMessage("nl", SpeechModelAvailability.NotDownloaded, scheduled))
        val prompt = platformModelDialogMessage("nl", SpeechModelAvailability.NotDownloaded, idle)
        assertTrue(prompt.startsWith("To use iOS speech recognition for Dutch"))
        assertTrue("offline" in platformModelDialogMessage(null, SpeechModelAvailability.NotDownloaded, failed))
        assertEquals("The speech model for Dutch is downloaded.", platformModelDialogMessage("nl", SpeechModelAvailability.Installed, idle))
    }
}
