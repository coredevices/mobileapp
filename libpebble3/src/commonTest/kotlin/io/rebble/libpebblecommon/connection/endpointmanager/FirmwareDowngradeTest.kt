package io.rebble.libpebblecommon.connection.endpointmanager

import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class FirmwareDowngradeTest {
    private fun version(
        tag: String,
        isDualSlot: Boolean = true,
        isRecovery: Boolean = false,
        timestamp: Instant = Instant.DISTANT_PAST,
    ) = FirmwareVersion.from(
        tag = tag,
        isRecovery = isRecovery,
        gitHash = "",
        timestamp = timestamp,
        isDualSlot = isDualSlot,
        isSlot0 = true,
    )!!

    private fun update(tag: String, canDowngrade: Boolean = true) =
        FirmwareUpdateCheckResult.FoundUpdate(
            version = version(tag),
            url = "",
            notes = "",
            canDowngrade = canDowngrade,
        )

    @Test
    fun dualSlotDowngradeNeedsPrf() {
        assertTrue(needsPrfToDowngrade(update("v4.30.1"), version("v4.31.1")))
    }

    @Test
    fun downgradeNotAllowedDoesNot() {
        assertFalse(
            needsPrfToDowngrade(update("v4.30.1", canDowngrade = false), version("v4.31.1"))
        )
    }

    @Test
    fun singleSlotDoesNot() {
        assertFalse(
            needsPrfToDowngrade(update("v4.30.1"), version("v4.31.1", isDualSlot = false))
        )
    }

    @Test
    fun alreadyInRecoveryDoesNot() {
        assertFalse(needsPrfToDowngrade(update("v4.30.1"), version("v4.31.1", isRecovery = true)))
    }

    /** A server claiming a downgrade that isn't one must not push a healthy watch into recovery. */
    @Test
    fun upgradeAndSameVersionDoNot() {
        assertFalse(needsPrfToDowngrade(update("v4.31.1"), version("v4.30.1")))
        assertFalse(needsPrfToDowngrade(update("v4.31.1"), version("v4.31.1")))
    }

    /** Update-check versions carry a placeholder timestamp, so only the numbers may be compared. */
    @Test
    fun timestampIsIgnored() {
        assertFalse(
            needsPrfToDowngrade(
                update("v4.31.1"),
                version("v4.31.1", timestamp = Instant.fromEpochSeconds(1_700_000_000)),
            )
        )
    }

    @Test
    fun dirtyBuildComparesOnVersionNumber() {
        assertTrue(needsPrfToDowngrade(update("v4.30.1"), version("v4.31.0-4-gecada632b-dirty")))
    }
}
