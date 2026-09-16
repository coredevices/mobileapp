package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.Serializable

@Serializable
data class AndroidCompanionAppRoot(
    val url: String? = null,
    val apps: List<AndroidCompanionAppInstance> = emptyList(),
    /**
     * Whether the watchapp needs the companion app to function. When false, the companion only
     * enhances the watchapp (e.g. alongside PKJS), so no error is surfaced if it isn't installed.
     */
    val required: Boolean = true,
)
