package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.Serializable

@Serializable
public data class Resources internal constructor(
    val media: List<Media> = emptyList()
)