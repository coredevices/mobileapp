package io.rebble.libpebblecommon.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SystemServiceTest {
    @Test
    fun firmwareVersionComparator() {
        val v3_0_0 = fwVersion("v3.0.0", Instant.DISTANT_PAST, 3, 0, 0)
        val v3_1_0 = fwVersion("v3.1.0", Instant.DISTANT_PAST, 3, 1, 0)
        val v3_1_111 = fwVersion("v3.1.111", Instant.DISTANT_PAST, 3, 1, 1)
        val v3_2_0 = fwVersion("v3.2.0", Instant.DISTANT_PAST, 3, 2, 0)
        val v4_0_0 = fwVersion("v4.0.0", Instant.DISTANT_PAST, 4, 0, 0)
        val v4_0_0_later = fwVersion("v4.0.0", Instant.DISTANT_PAST + 1.days, 4, 0, 0)
        val v4_0_0_suffix = fwVersion("v4.0.0", Instant.DISTANT_PAST, 4, 0, 0, suffix = "new")

        assertTrue(v3_1_0 > v3_0_0)
        assertNotEquals(v3_1_0, v3_0_0)
        assertTrue(v3_1_111 > v3_0_0)
        assertTrue(v3_1_111 > v3_1_0)
        assertTrue(v3_2_0 > v3_1_111)
        assertTrue(v4_0_0 > v3_0_0)
        assertTrue(v4_0_0 > v3_1_111)
        assertTrue(v4_0_0 > v3_2_0)
        assertTrue(v4_0_0_later > v4_0_0)
        assertTrue(v4_0_0 >= v4_0_0_suffix)
        assertTrue(v4_0_0 <= v4_0_0_suffix)
        assertEquals(v4_0_0, v4_0_0_suffix)
        assertNotEquals(v4_0_0, v4_0_0_later)
    }
}

private fun fwVersion(
    stringVersion: String,
    timestamp: Instant,
    major: Int,
    minor: Int,
    patch: Int,
    suffix: String? = null,
) = FirmwareVersion(
    stringVersion = stringVersion,
    timestamp = timestamp,
    major = major,
    minor = minor,
    patch = patch,
    suffix = suffix,
    gitHash = "",
    isRecovery = false,
    isDualSlot = false,
    isSlot0 = false,
)