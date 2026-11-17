package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.disk.pbw.PbwApp
import io.rebble.libpebblecommon.locker.AppCapability
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.metadata.pbw.appinfo.pebbleKit2Packages
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlin.uuid.Uuid
import io.rebble.libpebblecommon.web.LockerEntry as WebLockerEntry

data class RequestedPermissions(
    val declared: List<PluginPermission>?,
    val hasPkjs: Boolean,
    val hasCompanionApp: Boolean = false,
    val hasAndroidCompanion: Boolean = false,
    val pebbleKit2Packages: List<String> = emptyList(),
    /** The appinfo `health` capability, which old SDKs never checked. */
    val hasHealth: Boolean = false,
    val legacyForHealth: Boolean = true,
) {
    /** A legacy app asks for what it used to get implicitly. */
    val all: List<String>
        get() = ((declared?.flatMap { it.keys() } ?: legacyRequested(hasPkjs, hasAndroidCompanion)) +
            pebbleKit2Packages.map(::pebbleKit2Key) +
            listOfNotNull(PluginPermissions.HEALTH.takeIf { hasHealth && legacyForHealth })).distinct()

    /** Only for an app not installed yet; otherwise use `AppPermissions.subjectOf`. */
    fun subject(appUuid: Uuid) = PermissionSubject(
        appUuid = appUuid,
        legacy = declared == null,
        hasAndroidCompanion = hasAndroidCompanion,
        hasCompanionApp = hasCompanionApp,
        legacyForHealth = legacyForHealth,
    )
}

fun declaredPermissions(usesPermissions: List<PluginPermission>, manifestVersion: Int): List<PluginPermission>? =
    usesPermissions.takeUnless { isLegacy(manifestVersion) }

fun PbwApp.requestedPermissions(companionsOf: LockerEntry? = null) = RequestedPermissions(
    declared = info.declaredPermissions,
    hasPkjs = hasPKJS,
    hasCompanionApp = companionsOf?.androidCompanion != null || companionsOf?.iosCompanion != null,
    hasAndroidCompanion = companionsOf?.androidCompanion != null,
    pebbleKit2Packages = info.pebbleKit2Packages(),
    hasHealth = AppCapability.Health.code in info.capabilities,
    legacyForHealth = isLegacyForHealth(
        WatchType.entries.mapNotNull { getBinaryHeaderFor(it)?.let { header -> "${header.sdkVersionMajor.get()}.${header.sdkVersionMinor.get()}" } },
    ),
)

/** Null when the store sent no appinfo fields (as the Rebble store doesn't): the pbw says then. */
fun WebLockerEntry.requestedPermissions(): RequestedPermissions? {
    val manifestVersion = manifestVersion ?: return null
    return RequestedPermissions(
        declared = declaredPermissions(usesPermissions.orEmpty(), manifestVersion),
        // Not in the locker: a legacy app's PebbleKit JS permissions wait for its pbw.
        hasPkjs = false,
        hasCompanionApp = companions.android != null || companions.ios != null,
        hasAndroidCompanion = companions.android != null,
        pebbleKit2Packages = pebbleKit2Packages.orEmpty(),
        hasHealth = AppCapability.Health.code in capabilities.orEmpty(),
        legacyForHealth = isLegacyForHealth(hardwarePlatforms.map { it.sdkVersion }),
    )
}

data class InstallReview(
    val appUuid: Uuid,
    val appName: String,
    val toReview: List<String>,
    /** Declared, undecided, and granted by the policy without asking: shown so they can be denied. */
    val automatic: List<String> = emptyList(),
    /** Null for a legacy app. */
    val declared: List<PluginPermission>? = null,
) {
    /** The developer said which permissions are required. */
    val describesRequired: Boolean get() = declared?.describesRequired() == true

    fun isRequired(key: String) = declared?.isRequired(key) == true

    /** Installing waits for these: all of them, unless the developer said which are required. */
    fun needsAnswer(key: String) = !describesRequired || isRequired(key)

    fun reasonFor(key: String) = declared?.declarationOf(key)?.reason
}
