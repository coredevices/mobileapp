package io.rebble.cobble.shared.data.js

import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.serialization.Serializable

@Serializable
internal data class ActivePebbleWatchInfo(
        val platform: String,
        val model: String,
        val language: String,
        val firmware: FirmwareVersion
) {
    @Serializable
    data class FirmwareVersion(
            val major: Int,
            val minor: Int,
            val patch: Int,
            val suffix: String
    )
}

internal fun ActivePebbleWatchInfo.Companion.fromWatchInfo(watchInfo: WatchInfo): ActivePebbleWatchInfo {
    val fwVersion = watchInfo.runningFwVersion
    return ActivePebbleWatchInfo(
            platform = watchInfo.platform.watchType.codename,
            model = watchInfo.color.jsName,
            language = watchInfo.language,
            firmware = ActivePebbleWatchInfo.FirmwareVersion(
                    major = fwVersion.major,
                    minor = fwVersion.minor,
                    patch = fwVersion.patch,
                    suffix = fwVersion.suffix ?: ""
            )
    )
}