package io.rebble.libpebblecommon.connection.endpointmanager

import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifestWrapper
import io.rebble.libpebblecommon.services.FirmwareVersion

// The build tooling (pebbleos tools/pblboot.py) treats exactly these tags as releases.
private val RELEASE_TAG = Regex("^v?\\d+(?:\\.\\d+)?(?:\\.\\d+)?(?:-(?:beta|rc)\\d+)?$")

internal val FirmwareVersion.isRelease: Boolean get() = RELEASE_TAG.matches(stringVersion)

/** Some firmware reports its tag without the leading v. */
internal fun FirmwareVersion.isSameBuildAs(other: FirmwareVersion): Boolean =
    stringVersion.removePrefix("v") == other.stringVersion.removePrefix("v")

/**
 * Whether a dual-slot watch boots [target] once it's installed into the other slot. Of two valid
 * slots, pblboot boots the higher priority: any dev build outranks any release, releases rank by
 * version, and dev builds by when they were built - assumed here to be in the order they arrive.
 */
internal fun bootsAfterDirectInstall(running: FirmwareVersion, target: FirmwareVersion): Boolean = when {
    !target.isRelease -> true
    !running.isRelease -> false
    else -> !target.lowerVersionNumberThan(running)
}

/**
 * Whether the watch boots [target] after a normal install, rather than going through recovery: not
 * if it already kept booting its previous build when [target] was installed directly ([pending]).
 */
internal fun installsDirectly(
    running: FirmwareVersion,
    target: FirmwareVersion,
    pending: PendingFirmwareInstalls.Pending?,
): Boolean {
    val didNotBoot = pending?.takeIf { it.reason == PendingFirmwareInstalls.Reason.DidNotBoot }?.install?.update?.version
    return (!running.isDualSlot || running.isRecovery || bootsAfterDirectInstall(running, target)) &&
        didNotBoot?.isSameBuildAs(target) != true
}

/** Ignores the timestamp, which is a placeholder on versions that came from an update check. */
private fun FirmwareVersion.lowerVersionNumberThan(other: FirmwareVersion): Boolean =
    compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }) < 0

internal sealed class InstallRoute(val manifest: PbzManifestWrapper) {
    class Direct(manifest: PbzManifestWrapper) : InstallRoute(manifest)

    /** Recovery installs [manifest] (always the slot 0 build) after the watch reboots into it. */
    class ViaPrf(manifest: PbzManifestWrapper) : InstallRoute(manifest)
}

/** How a bundle with [manifests] can be installed; null if it can't. */
internal fun installRoute(
    manifests: List<PbzManifestWrapper>,
    running: FirmwareVersion,
    updateToSlot: Int?,
    bootsDirectly: (FirmwareVersion) -> Boolean,
): InstallRoute? {
    val slot0 = manifests.find { it.manifest.firmware.slot == 0 }
    return when {
        manifests.firstOrNull()?.manifest?.firmware?.type == "recovery" -> InstallRoute.Direct(manifests.first())
        running.isRecovery ->
            (slot0 ?: manifests.singleOrNull()?.takeIf { it.manifest.firmware.slot == null })
                ?.let { InstallRoute.Direct(it) }
        updateToSlot == null -> manifests.singleOrNull()?.let { InstallRoute.Direct(it) }
        else -> {
            val other = manifests.find { it.manifest.firmware.slot == updateToSlot }
            when {
                other != null && other.manifest.asFirmwareVersion()?.let(bootsDirectly) == true ->
                    InstallRoute.Direct(other)
                slot0 != null -> InstallRoute.ViaPrf(slot0)
                else -> null
            }
        }
    }
}
