package coredevices.ring.ui.screens.settings

import coredevices.ring.agent.LlmMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmModeControlTest {

    @Test
    fun everyModeHasItsOwnLabelAndDetail() {
        val labels = LlmMode.entries.map { it.displayName() }
        val details = LlmMode.entries.map { it.modeDetail() }
        assertEquals(LlmMode.entries.size, labels.toSet().size)
        assertEquals(LlmMode.entries.size, details.toSet().size)
        assertTrue(labels.none { it.isBlank() })
        assertTrue(details.none { it.isBlank() })
    }

    @Test
    fun everyModeIsOfferedExactlyOnceMostCloudFirst() {
        assertEquals(LlmMode.entries.toSet(), llmModeOptions.toSet())
        assertEquals(llmModeOptions.size, llmModeOptions.toSet().size)
        assertEquals(
            listOf(false, true, true),
            llmModeOptions.map { it.usesLocalCactus() },
        )
    }

    @Test
    fun localModesAreNotSelectableWhenLocalIsUnsupported() {
        assertTrue(llmModeSelectable(LlmMode.RemoteOnly, localSupported = false))
        assertFalse(llmModeSelectable(LlmMode.LocalOnly, localSupported = false))
        assertFalse(llmModeSelectable(LlmMode.RemoteFirst, localSupported = false))
    }

    @Test
    fun everyModeIsSelectableWhenLocalIsSupported() {
        assertTrue(LlmMode.entries.all { llmModeSelectable(it, localSupported = true) })
    }

    @Test
    fun rowSubtitleWarnsOnlyForModesThatRunTheLocalModel() {
        assertEquals(LlmMode.RemoteOnly.displayName(), llmModeRowSubtitle(LlmMode.RemoteOnly))
        LlmMode.entries.filter { it.usesLocalCactus() }.forEach {
            assertEquals("${it.displayName()} · ${it.modeDetail()}", llmModeRowSubtitle(it))
        }
    }
}
