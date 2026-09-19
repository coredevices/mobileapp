package coredevices.util.transcription

import coredevices.util.models.CactusSTTMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechModelPromptTest {

    @Test
    fun aMissingModelAlwaysAsksTheUserToDownloadIt() {
        assertEquals(
            SpeechModelPrompt.DownloadRequired(CactusSTTMode.PlatformOnly),
            speechModelPrompt(CactusSTTMode.PlatformOnly, SpeechModelAvailability.NotDownloaded, cloudFallback = true),
        )
        assertEquals(
            SpeechModelPrompt.DownloadRequired(CactusSTTMode.LocalOnly),
            speechModelPrompt(CactusSTTMode.LocalOnly, SpeechModelAvailability.NotDownloaded, cloudFallback = false),
        )
    }

    @Test
    fun anInFlightDownloadOnlyWarnsWhenNothingElseCanTranscribe() {
        assertNull(speechModelPrompt(CactusSTTMode.PlatformOnly, SpeechModelAvailability.Downloading, cloudFallback = true))
        assertEquals(
            SpeechModelPrompt.DownloadPending(CactusSTTMode.LocalOnly),
            speechModelPrompt(CactusSTTMode.LocalOnly, SpeechModelAvailability.Downloading, cloudFallback = false),
        )
    }

    @Test
    fun nothingToPromptWhenTheModelIsInstalledOrCannotExist() {
        assertNull(speechModelPrompt(CactusSTTMode.LocalOnly, SpeechModelAvailability.Installed, cloudFallback = false))
        assertNull(speechModelPrompt(CactusSTTMode.PlatformOnly, SpeechModelAvailability.Unsupported, cloudFallback = false))
    }
}
