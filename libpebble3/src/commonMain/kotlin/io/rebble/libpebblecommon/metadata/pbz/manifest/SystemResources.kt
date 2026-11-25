package io.rebble.libpebblecommon.metadata.pbz.manifest

import kotlinx.serialization.Serializable

@Serializable
internal data class SystemResources(
    val name: String,
    val timestamp: Long,
    val size: Long,
    val crc: Long
)
