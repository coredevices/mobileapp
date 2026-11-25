package io.rebble.libpebblecommon.metadata.pbw.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
internal data class PbwBlob internal constructor(
        val crc: Long?,
        val name: String,
        @SerialName("sdk_version")
        val sdkVersion: SdkVersion? = null,
        val size: Int,
        val timestamp: Int?
)