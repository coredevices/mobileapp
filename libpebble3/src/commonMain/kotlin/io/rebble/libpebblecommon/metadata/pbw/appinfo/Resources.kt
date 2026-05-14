package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.Serializable

@Serializable
data class Resources(
    val media: List<Media> = emptyList()
)