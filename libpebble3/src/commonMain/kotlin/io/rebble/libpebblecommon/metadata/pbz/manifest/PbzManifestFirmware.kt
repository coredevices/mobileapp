package io.rebble.libpebblecommon.metadata.pbz.manifest

import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatformSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PbzManifestFirmware(
    val name: String,
    val type: String,
    val timestamp: Long,
    val commit: String,
    @Serializable(with = WatchHardwarePlatformSerializer::class)
    @SerialName("hwrev")
    val hwRev: WatchHardwarePlatform,
    val size: Long,
    val crc: Long,
    val versionTag: String? = null,
    val slot: Int? = null,
)
