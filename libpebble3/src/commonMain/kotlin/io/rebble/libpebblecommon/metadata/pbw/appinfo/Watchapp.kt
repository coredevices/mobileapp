package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.Serializable

@Serializable
public data class Watchapp internal constructor(
    val watchface: Boolean = false,
    val hiddenApp: Boolean = false,
    val onlyShownOnCommunication: Boolean = false
)