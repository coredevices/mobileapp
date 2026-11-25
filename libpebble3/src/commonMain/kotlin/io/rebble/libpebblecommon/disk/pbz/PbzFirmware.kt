package io.rebble.libpebblecommon.disk.pbz

import io.rebble.libpebblecommon.disk.pbz.DiskUtil.requirePbzManifests
import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifest
import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifestWrapper
import kotlinx.io.files.Path

internal class PbzFirmware(private val path: Path) {
    val manifests: List<PbzManifestWrapper> by lazy { requirePbzManifests(path) }
}

internal fun PbzFirmware.findManifestFor(slot: Int?): PbzManifestWrapper {
    if (slot == null) {
        if (manifests.size != 1) {
            throw IllegalStateException("No slot, but there were ${manifests.size} manifests")
        }
        return manifests[0]
    } else {
        val firstManifest = manifests.firstOrNull()
        // PRF doesn't have a slot
        if (firstManifest != null && firstManifest.manifest.firmware.type == "recovery") {
            return firstManifest
        }
        return manifests.find { it.manifest.firmware.slot == slot }
            ?: throw IllegalStateException("No manifest for slot $slot")
    }
}