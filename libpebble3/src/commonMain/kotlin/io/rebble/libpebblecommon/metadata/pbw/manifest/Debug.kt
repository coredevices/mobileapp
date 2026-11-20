package io.rebble.libpebblecommon.metadata.pbw.manifest

import kotlinx.serialization.Serializable

@Serializable
internal data class Debug internal constructor(
    val empty: String? = null
)