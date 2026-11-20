package io.rebble.libpebblecommon.metadata.pbz.manifest

import io.rebble.libpebblecommon.disk.pbz.DiskUtil
import io.rebble.libpebblecommon.metadata.pbw.manifest.Debug
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PbzManifest(
    val manifestVersion: Int,
    val generatedAt: Long,
    val generatedBy: String? = null,
    val debug: Debug? = null,
    val firmware: PbzManifestFirmware,
    val resources: SystemResources? = null,
    @SerialName("js_tooling")
    val jsTooling: JsTooling? = null,
    val type: String
)

internal data class PbzManifestWrapper(
    val manifest: PbzManifest,
    val zipPath: Path,
    val manifestPath: Path,
) {
    fun getFile(fileName: String): RawSource {
        return DiskUtil.getFile(zipPath, Path(manifestPath, fileName).toString())
    }
    fun getFirmware(): RawSource = getFile(manifest.firmware.name)
    fun getResources(): RawSource? = manifest.resources?.let { getFile(it.name) }
}