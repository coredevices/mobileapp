package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class AndroidCompanionAppInstance internal constructor(
    @SerialName("package") val pkg: String? = null
)
