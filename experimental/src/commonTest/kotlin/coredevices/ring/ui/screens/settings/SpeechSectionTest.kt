package coredevices.ring.ui.screens.settings

import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.ModelInfo
import coredevices.util.transcription.SpeechModelAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechSectionTest {

    @Test
    fun onlyTheEnginesThatReachTheCloudNeedAnAccount() {
        assertFalse(CactusSTTMode.LocalOnly.needsSignIn())
        assertFalse(CactusSTTMode.PlatformOnly.needsSignIn())
        assertTrue(CactusSTTMode.RemoteOnly.needsSignIn())
        assertTrue(CactusSTTMode.RemoteFirst.needsSignIn())
        assertTrue(CactusSTTMode.LocalFirst.needsSignIn())
    }

    @Test
    fun aSingleLanguageLocalModelLocksTheSpokenLanguage() {
        val multi = ModelInfo(slug = "parakeet-tdt-0.6b-v3", supportsMultiLanguage = true)
        val single = ModelInfo(slug = "parakeet-tdt-0.6b-v2", supportsMultiLanguage = false)

        assertFalse(spokenLanguageSelectable(CactusSTTMode.LocalOnly, single))
        assertTrue(spokenLanguageSelectable(CactusSTTMode.LocalOnly, multi))
        // The cloud handles any language, so the local model doesn't constrain it.
        assertTrue(spokenLanguageSelectable(CactusSTTMode.RemoteOnly, single))
        // Not yet loaded — don't lock the row on a guess.
        assertTrue(spokenLanguageSelectable(CactusSTTMode.LocalOnly, null))
    }

    @Test
    fun thePlatformEngineNeedsItsOwnModelDownloadNotACactusOne() {
        assertTrue(speechEngineNeedsDownload(CactusSTTMode.PlatformOnly, onDeviceSupported = false, hasOfflineModels = false, platformModelNeedsDownload = true))
        assertFalse(speechEngineNeedsDownload(CactusSTTMode.PlatformOnly, onDeviceSupported = true, hasOfflineModels = false, platformModelNeedsDownload = false))
        assertTrue(speechEngineNeedsDownload(CactusSTTMode.LocalOnly, onDeviceSupported = true, hasOfflineModels = false, platformModelNeedsDownload = false))
        assertFalse(speechEngineNeedsDownload(CactusSTTMode.RemoteOnly, onDeviceSupported = true, hasOfflineModels = false, platformModelNeedsDownload = true))
    }

    @Test
    fun platformModelRowDescribesTheLanguageAndItsState() {
        val idle = ModelDownloadStatus.Idle
        assertEquals("Dutch · Not downloaded", platformModelSubtitle("nl", SpeechModelAvailability.NotDownloaded, idle))
        assertEquals("Dutch · Downloaded", platformModelSubtitle("nl", SpeechModelAvailability.Installed, idle))
        assertEquals("Automatic · Downloading 42%", platformModelSubtitle(null, SpeechModelAvailability.NotDownloaded, ModelDownloadStatus.Downloading("platform-native", 0.42f)))
    }

    @Test
    fun onlyTheLocalEnginesNeedAModelChoice() {
        assertTrue(CactusSTTMode.LocalOnly.needsLocalModel())
        assertTrue(CactusSTTMode.LocalFirst.needsLocalModel())
        assertTrue(CactusSTTMode.RemoteFirst.needsLocalModel())
        assertFalse(CactusSTTMode.RemoteOnly.needsLocalModel())
        assertFalse(CactusSTTMode.PlatformOnly.needsLocalModel())
    }
}
