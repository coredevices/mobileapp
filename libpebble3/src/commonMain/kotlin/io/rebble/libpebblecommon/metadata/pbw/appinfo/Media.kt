package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import io.rebble.libpebblecommon.metadata.StringOrBoolean

@Serializable
public data class Media internal constructor(
    @SerialName("file")
    val resourceFile: String,
    val menuIcon: StringOrBoolean = StringOrBoolean(false),
    val name: String,
    val type: String,
    val targetPlatforms: List<String>? = null,
    val characterRegex: String? = null
)