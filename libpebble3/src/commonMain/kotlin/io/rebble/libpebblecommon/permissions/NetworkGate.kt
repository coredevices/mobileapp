package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.plugin.PluginNetworkPolicy
import io.rebble.libpebblecommon.plugin.PluginNetworkVerdict
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlin.uuid.Uuid

fun interface NetworkAccess {
    /** [wait]: never from a blocking callback. */
    suspend fun check(url: String, wait: Boolean): PluginNetworkVerdict
}

/** A legacy app is checked against `GlobalInternet` instead. */
class NetworkGate(
    private val permissions: AppPermissions,
    private val name: String,
    private val appUuid: Uuid,
) : NetworkAccess {
    override suspend fun check(url: String, wait: Boolean): PluginNetworkVerdict {
        if (!permissions.enforcing) {
            return if (PluginNetworkPolicy.isRequestable(url)) {
                PluginNetworkVerdict.Allowed
            } else {
                PluginNetworkVerdict.Denied("'$url' can't be requested")
            }
        }
        val scoped = PluginNetworkPolicy.permissionFor(url)
            ?: return PluginNetworkVerdict.Denied("'$url' can't be requested")
        val subject = permissions.subjectOf(appUuid)
            ?: return PluginNetworkVerdict.Denied("$name isn't installed")
        val permission = if (subject.legacy) PluginPermissions.GLOBAL_INTERNET else scoped
        val decision = permissions.request(subject, permission, wait)
        return if (decision == PermissionDecision.Granted) {
            PluginNetworkVerdict.Allowed
        } else {
            PluginNetworkVerdict.Denied(refusalMessage(name, mapOf(permission to decision)))
        }
    }
}

