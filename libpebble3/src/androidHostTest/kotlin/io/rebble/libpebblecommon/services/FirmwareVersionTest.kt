package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.packets.WatchFirmwareVersion
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FirmwareVersionTest {
    companion object {
        private const val GITHASH = "ABCDEFG"
        private val HARDWARE_PLATFORM = WatchHardwarePlatform.CORE_ASTERIX
        private const val RECOVERY = false
        private val TIMESTAMP = Instant.fromEpochSeconds(123456789)
    }

    @Test
    fun parseFWVersion() {
        val tag = "v4.0.1-tag"
        assertEquals(FirmwareVersion(
            stringVersion = tag,
            timestamp = TIMESTAMP,
            major = 4,
            minor = 0,
            patch = 1,
            suffix = "tag",
            gitHash = GITHASH,
            isRecovery = RECOVERY,
            isDualSlot = false,
            isSlot0 = false,
        ), WatchFirmwareVersion().apply {
            timestamp.set(TIMESTAMP.epochSeconds.toUInt())
            versionTag.set(tag)
            gitHash.set(GITHASH)
            flags.set(1.toUByte())
            hardwarePlatform.set(HARDWARE_PLATFORM.protocolNumber)
        }.firmwareVersion())
    }

    @Test
    fun parseFWVersionNoTag() {
        val tag = "v4.0.2"
        assertEquals(
            expected = FirmwareVersion(
                stringVersion = tag,
                timestamp = TIMESTAMP,
                major = 4,
                minor = 0,
                patch = 2,
                suffix = "",
                gitHash = GITHASH,
                isRecovery = RECOVERY,
                isDualSlot = false,
                isSlot0 = false,
            ), actual = WatchFirmwareVersion().apply {
                timestamp.set(TIMESTAMP.epochSeconds.toUInt())
                versionTag.set(tag)
                gitHash.set(GITHASH)
                flags.set(1.toUByte())
                hardwarePlatform.set(HARDWARE_PLATFORM.protocolNumber)
            }.firmwareVersion()
        )
    }

    @Test
    fun parseFWVersionNoPoint() {
        val tag = "v4.0-prf4"
        assertEquals(FirmwareVersion(
            stringVersion = tag,
            timestamp = TIMESTAMP,
            major = 4,
            minor = 0,
            patch = 0,
            suffix = "prf4",
            gitHash = GITHASH,
            isRecovery = RECOVERY,
            isDualSlot = false,
            isSlot0 = false,
        ), WatchFirmwareVersion().apply {
            timestamp.set(TIMESTAMP.epochSeconds.toUInt())
            versionTag.set(tag)
            gitHash.set(GITHASH)
            flags.set(1.toUByte())
            hardwarePlatform.set(HARDWARE_PLATFORM.protocolNumber)
        }.firmwareVersion())
    }
}