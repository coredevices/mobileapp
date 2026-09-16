package coredevices.ring.ui.screens.settings

import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelInfo
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
    fun everyOfferedEngineIsNamed() {
        indexSpeechModes.forEach {
            assertTrue(it.speechEngineName().isNotBlank(), "$it has no name")
        }
    }

    @Test
    fun modelDetailOnlyOffersTheDownloadSizeWhenItIsNotOnDisk() {
        val model = ModelInfo(slug = "parakeet-tdt-0.6b-v2", sizeInMB = 406, intendedTask = "Higher accuracy for English")
        assertEquals("Higher accuracy for English · Downloaded", speechModelDetail(model, downloaded = true))
        assertEquals("Higher accuracy for English · 406 MB download", speechModelDetail(model, downloaded = false))
        assertEquals("406 MB download", speechModelDetail(model.copy(intendedTask = null), downloaded = false))
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

        assertEquals("Not selectable for this speech model", spokenLanguageRowSubtitle("fr", selectable = false))
        assertEquals("Automatic", spokenLanguageRowSubtitle(null, selectable = true))
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
