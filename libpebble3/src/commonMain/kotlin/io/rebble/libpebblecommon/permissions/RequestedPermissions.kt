package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.disk.pbw.PbwApp
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.metadata.pbw.appinfo.pebbleKit2Packages
import io.rebble.libpebblecommon.plugin.PluginPermission
import kotlin.uuid.Uuid

data class RequestedPermissions(
    val declared: List<PluginPermission>?,
    val hasPkjs: Boolean,
    val hasCompanionApp: Boolean = false,
    val hasAndroidCompanion: Boolean = false,
    val pebbleKit2Packages: List<String> = emptyList(),
) {
    /** A legacy app asks for what it used to get implicitly. */
    val all: List<String>
        get() = ((declared?.flatMap { it.keys() } ?: legacyRequested(hasPkjs, hasAndroidCompanion)) +
            pebbleKit2Packages.map(::pebbleKit2Key)).distinct()

    /** Only for an app not installed yet; otherwise use `AppPermissions.subjectOf`. */
    fun subject(appUuid: Uuid) = PermissionSubject(
        appUuid = appUuid,
        legacy = declared == null,
        hasAndroidCompanion = hasAndroidCompanion,
        hasCompanionApp = hasCompanionApp,
    )
}

fun declaredPermissions(usesPermissions: List<PluginPermission>, manifestVersion: Int): List<PluginPermission>? =
    usesPermissions.takeUnless { isLegacy(manifestVersion) }

fun PbwAppInfo.requestedPermissions(hasPkjs: Boolean, companionsOf: LockerEntry? = null) = RequestedPermissions(
    declared = declaredPermissions,
    hasPkjs = hasPkjs,
    hasCompanionApp = companionsOf?.androidCompanion != null || companionsOf?.iosCompanion != null,
    hasAndroidCompanion = companionsOf?.androidCompanion != null,
    pebbleKit2Packages = pebbleKit2Packages(),
)

fun PbwApp.requestedPermissions() = info.requestedPermissions(hasPkjs = hasPKJS)

data class InstallReview(
    val appUuid: Uuid,
    val appName: String,
    val toReview: List<String>,
)
