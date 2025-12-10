package coredevices.pebble.services

import coredevices.pebble.services.Memfault.Companion.serialForMemfault
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MemfaultTest {
    private fun createWatchInfo(serial: String, mac: String) = WatchInfo(
        runningFwVersion = FirmwareVersion(
            "v3.8",
            Instant.DISTANT_PAST,
            8,
            0,
            0,
            "",
            "ABCDEF",
            false,
            isDualSlot = false,
            isSlot0 = false,
        ),
        recoveryFwVersion = FirmwareVersion(
            "v3.8",
            Instant.DISTANT_PAST,
            8,
            0,
            0,
            "",
            "ABCDEF",
            true,
            isDualSlot = false,
            isSlot0 = false,
        ),
        platform = WatchHardwarePlatform.CORE_ASTERIX,
        bootloaderTimestamp = Instant.DISTANT_PAST,
        board = "basalt_ev2",
        serial = serial,
        btAddress = mac,
        resourceCrc = 0,
        resourceTimestamp = Instant.DISTANT_PAST,
        language = "en_US",
        languageVersion = 2,
        capabilities = emptySet(),
        isUnfaithful = false,
        healthInsightsVersion = 1,
        javascriptVersion = 1,
        color = WatchColor.ClassicFlyBlue,
    )

    @Test
    fun xxxxxxxxxxxxSerial() {
        assertEquals("XXXXB7B8CD5E", createWatchInfo(
            serial = "XXXXXXXXXXXX",
            mac = "F5:F9:5E:CD:B8:B7"
        ).serialForMemfault())
    }

    @Test
    fun normalSerial() {
        assertEquals("123456789012", createWatchInfo(
            serial = "123456789012",
            mac = "B7:B8:CD:5E:F9:F5"
        ).serialForMemfault())
    }
}