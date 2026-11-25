package io.rebble.libpebblecommon.metadata.pbw.manifest

import kotlinx.serialization.Serializable

@Serializable
internal data class SdkVersion internal constructor(
        val major: Int?,
        val minor: Int?
)