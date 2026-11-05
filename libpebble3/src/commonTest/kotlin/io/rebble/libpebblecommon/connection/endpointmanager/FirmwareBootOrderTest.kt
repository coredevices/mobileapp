package io.rebble.libpebblecommon.connection.endpointmanager

import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifest
import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifestFirmware
import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifestWrapper
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FirmwareBootOrderTest {
    private fun version(tag: String, isDualSlot: Boolean = true, isRecovery: Boolean = false) =
        FirmwareVersion.from(
            tag = tag,
            isRecovery = isRecovery,
            gitHash = "",
            timestamp = Instant.fromEpochSeconds(1_792_000_000),
            isDualSlot = isDualSlot,
            isSlot0 = true,
        )!!

    private fun manifest(tag: String, slot: Int?, type: String = "normal") = PbzManifestWrapper(
        manifest = PbzManifest(
            manifestVersion = 2,
            generatedAt = 0,
            firmware = PbzManifestFirmware(
                name = "pebbleos.bin",
                type = type,
                timestamp = 1_792_000_000_000,
                commit = "",
                hwRev = WatchHardwarePlatform.UNKNOWN,
                size = 1,
                crc = 1,
                versionTag = tag,
                slot = slot,
            ),
            type = "firmware",
        ),
        zipPath = Path("unused.pbz"),
        manifestPath = Path(""),
    )

    private val release = version("v4.45.0")
    private val nightly = version("v4.45.0-11-g7bf58aca1")

    /** A dual-slot bundle of [tag], for a watch running [running] that updates slot 0. */
    private fun routeDualSlot(running: FirmwareVersion, tag: String) = installRoute(
        listOf(manifest(tag, slot = 0), manifest(tag, slot = 1)), running, updateToSlot = 0,
    ) { bootsAfterDirectInstall(running, it) }

    @Test
    fun releasesBootInVersionOrder() {
        assertTrue(bootsAfterDirectInstall(release, version("v4.46.0")))
        assertTrue(bootsAfterDirectInstall(version("v4.45.0-beta2"), release))
        assertFalse(bootsAfterDirectInstall(release, version("v4.44.9")))
    }

    @Test
    fun devBuildsOutrankReleases() {
        assertTrue(bootsAfterDirectInstall(release, nightly))
        assertFalse(bootsAfterDirectInstall(nightly, version("v4.46.0")))
        assertFalse(bootsAfterDirectInstall(version("v4.45.0-dirty"), version("v4.46.0")))
    }

    @Test
    fun devBuildsAreAssumedNewer() {
        assertTrue(bootsAfterDirectInstall(nightly, version("v4.45.0-12-gabc1234")))
        assertTrue(bootsAfterDirectInstall(version("v4.45.0-3-gdef5678-dirty"), nightly))
    }

    @Test
    fun aBuildThatBootsInstallsIntoTheOtherSlot() {
        val route = routeDualSlot(release, "v4.46.0")
        assertIs<InstallRoute.Direct>(route)
        assertEquals(0, route.manifest.manifest.firmware.slot)
    }

    @Test
    fun aBuildThatWouldNotBootGoesThroughRecoveryWithSlot0() {
        for ((running, tag) in listOf(release to "v4.44.9", nightly to "v4.46.0")) {
            val route = routeDualSlot(running, tag)
            assertIs<InstallRoute.ViaPrf>(route)
            assertEquals(0, route.manifest.manifest.firmware.slot)
        }
    }

    @Test
    fun aBuildOnlyForTheRunningSlotGoesThroughRecovery() {
        val route = installRoute(listOf(manifest("v4.46.0", slot = 0)), release, updateToSlot = 1) { true }
        assertIs<InstallRoute.ViaPrf>(route)
    }

    @Test
    fun aBuildOnlyForSlot1ThatCantGoDirectHasNoRoute() {
        assertNull(installRoute(listOf(manifest("v4.44.9", slot = 1)), release, updateToSlot = 1) { false })
    }

    @Test
    fun recoveryInstallsTheSlot0BuildDirectly() {
        val prf = version("v4.9.142", isRecovery = true)
        val route = installRoute(
            listOf(manifest("v4.46.0", slot = 0), manifest("v4.46.0", slot = 1)), prf, updateToSlot = null,
        ) { false }
        assertIs<InstallRoute.Direct>(route)
        assertEquals(0, route.manifest.manifest.firmware.slot)
        assertNull(installRoute(listOf(manifest("v4.46.0", slot = 1)), prf, updateToSlot = null) { false })
    }

    @Test
    fun singleSlotWatchesAndRecoveryImagesInstallDirectly() {
        val singleSlot = version("v4.45.0", isDualSlot = false)
        assertIs<InstallRoute.Direct>(installRoute(listOf(manifest("v4.44.0", slot = null)), singleSlot, null) { false })
        assertIs<InstallRoute.Direct>(
            installRoute(listOf(manifest("v4.9.150", slot = null, type = "recovery")), release, 1) { false }
        )
    }

    @Test
    fun aDirectInstallTheWatchDidNotBootIsNotRetriedDirectly() {
        val newer = version("v4.45.0-12-gabc1234")
        val install = FirmwareInstall(
            FirmwareUpdateCheckResult.FoundUpdate(newer, url = "", notes = "", installsDirectly = true),
            Path("update.pbz"),
            FirmwareUpdateTrigger.AutoUpdate,
        )
        val awaiting = PendingFirmwareInstalls.Pending(install, PendingFirmwareInstalls.Reason.AwaitingBoot)
        val didNotBoot = PendingFirmwareInstalls.Pending(install, PendingFirmwareInstalls.Reason.DidNotBoot)
        assertTrue(installsDirectly(nightly, newer, awaiting))
        assertFalse(installsDirectly(nightly, newer, didNotBoot))
        assertFalse(installsDirectly(nightly, version("4.45.0-12-gabc1234"), didNotBoot))
        assertTrue(installsDirectly(nightly, version("v4.45.0-13-g1234567"), didNotBoot))
    }
}
