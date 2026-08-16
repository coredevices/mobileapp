package coredevices.ring.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelNeedsReplacementTest {

    private val stt = "parakeet-tdt-0.6b-v3"
    private val lm = "needle-pebble-ft"
    private val compatible = setOf(stt, lm)

    @Test
    fun staleNetworkModel_isReplaced() {
        assertTrue(modelNeedsReplacement(stt, compatible, versionMatches = false, bundledInApp = false))
    }

    @Test
    fun staleBundledModel_isKept() {
        assertFalse(modelNeedsReplacement(lm, compatible, versionMatches = false, bundledInApp = true))
    }

    @Test
    fun currentVersion_isKept() {
        assertFalse(modelNeedsReplacement(stt, compatible, versionMatches = true, bundledInApp = false))
        assertFalse(modelNeedsReplacement(lm, compatible, versionMatches = true, bundledInApp = true))
    }

    @Test
    fun unknownName_isAlwaysReplaced() {
        assertTrue(modelNeedsReplacement("whisper-tiny", compatible, versionMatches = true, bundledInApp = false))
        assertTrue(modelNeedsReplacement("whisper-tiny", compatible, versionMatches = true, bundledInApp = true))
    }
}
