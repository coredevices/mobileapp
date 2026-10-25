package io.rebble.libpebblecommon.plugin.files

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.dao.LockerAppPermissionDao
import io.rebble.libpebblecommon.permissions.AppPermissions
import io.rebble.libpebblecommon.permissions.PermissionDecision
import io.rebble.libpebblecommon.permissions.deniesBareFilePermissionOf
import io.rebble.libpebblecommon.permissions.fileScopeOf
import io.rebble.libpebblecommon.permissions.readFilesKey
import io.rebble.libpebblecommon.permissions.writeFilesKey
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/** The host side of plugin file access: what a settings page asks for, grants, lists and revokes. */
interface PluginFileAccessApi {
    /** Entries the user has handed [appUuid] and not taken back. */
    suspend fun pluginFileHandles(appUuid: Uuid): List<FileHandleInfo>

    /** Whether the bare `ReadFiles` / `WriteFiles` a pick needs is already granted; never asks. */
    suspend fun checkFilePermission(appUuid: Uuid, mode: FileAccessMode): PermissionDecision

    /**
     * Records a pick as granted `ReadFiles/<scope>`, or `WriteFiles/<scope>` for read-write. Null
     * when the entry turns out to be unreachable, in which case nothing is kept.
     */
    suspend fun grantPluginFile(appUuid: Uuid, scope: String, mode: FileAccessMode, handle: String): FileHandleInfo?

    suspend fun revokePluginFile(appUuid: Uuid, scope: String)
}

/**
 * File access is ordinary permissions, scoped like `Internet/<domain>` is: a pick writes a granted
 * `ReadFiles/<scope>` or `WriteFiles/<scope>` row for the app (write covers read), every operation
 * is checked against it, and the user can take it back from the app's permission list.
 */
class PluginFileAccess(
    private val permissions: AppPermissions,
    private val dao: LockerAppPermissionDao,
    private val fileSystem: PluginFileSystem,
) : PluginFileAccessApi {
    private val logger = Logger.withTag("PluginFileAccess")

    override suspend fun pluginFileHandles(appUuid: Uuid): List<FileHandleInfo> {
        val rows = dao.getByAppUuid(appUuid)
        val picked = rows.filter { it.granted == true }.map { it.permission }
        return picked.mapNotNull(::fileScopeOf).distinct()
            .filterNot { rows.deniesBareFilePermissionOf(readFilesKey(it)) }
            .map { scope ->
                val writable = writeFilesKey(scope) in picked && !rows.deniesBareFilePermissionOf(writeFilesKey(scope))
                handleInfo(scope, if (writable) FileAccessMode.ReadWrite else FileAccessMode.Read)
            }
    }

    override suspend fun checkFilePermission(appUuid: Uuid, mode: FileAccessMode): PermissionDecision =
        permissions.check(appUuid, keyFor(mode))

    override suspend fun grantPluginFile(appUuid: Uuid, scope: String, mode: FileAccessMode, handle: String): FileHandleInfo? {
        fileSystem.retain(scope, handle)
        if (fileSystem.stat(scope, emptyList()) == null) {
            logger.w { "picked entry is unreachable; not granting" }
            releaseIfUnreferenced(scope)
            return null
        }
        permissions.setDecisions(appUuid, mapOf(keyFor(mode, scope) to true))
        return handleInfo(scope, mode)
    }

    override suspend fun revokePluginFile(appUuid: Uuid, scope: String) {
        dao.deleteByAppUuidAndPermissions(appUuid, listOf(readFilesKey(scope), writeFilesKey(scope)))
        releaseIfUnreferenced(scope)
    }

    /** Whether [appUuid] may use [scope] in [mode] right now; records the use. */
    suspend fun check(appUuid: Uuid, scope: String, mode: FileAccessMode): Boolean =
        permissions.check(appUuid, keyFor(mode, scope)) == PermissionDecision.Granted

    suspend fun handleInfo(appUuid: Uuid, scope: String): FileHandleInfo? {
        if (!check(appUuid, scope, FileAccessMode.Read)) return null
        val mode = if (check(appUuid, scope, FileAccessMode.ReadWrite)) FileAccessMode.ReadWrite else FileAccessMode.Read
        return handleInfo(scope, mode)
    }

    /** Every file the app was handed; for when it leaves the locker. */
    suspend fun destroy(appUuid: Uuid) {
        val keys = dao.getByAppUuid(appUuid).map { it.permission }.filter { fileScopeOf(it) != null }
        if (keys.isEmpty()) return
        dao.deleteByAppUuidAndPermissions(appUuid, keys)
        keys.mapNotNull(::fileScopeOf).distinct().forEach { releaseIfUnreferenced(it) }
    }

    private suspend fun handleInfo(scope: String, mode: FileAccessMode): FileHandleInfo {
        val entry = runCatching { fileSystem.stat(scope, emptyList()) }.getOrNull()
        return FileHandleInfo(
            id = scope,
            kind = entry?.kind ?: FileEntryKind.File,
            name = entry?.name ?: scope.trimEnd('/').substringAfterLast('/'),
            mode = mode,
        )
    }

    // A platform permission is app-wide, so it stays while any app still holds the scope.
    private suspend fun releaseIfUnreferenced(scope: String) {
        val held = dao.allFlow().first().any { it.granted == true && fileScopeOf(it.permission) == scope }
        if (held) return
        try {
            fileSystem.release(scope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "couldn't release file access" }
        }
    }

    private fun keyFor(mode: FileAccessMode): String = when (mode) {
        FileAccessMode.Read -> PluginPermissions.READ_FILES
        FileAccessMode.ReadWrite -> PluginPermissions.WRITE_FILES
    }

    private fun keyFor(mode: FileAccessMode, scope: String): String = when (mode) {
        FileAccessMode.Read -> readFilesKey(scope)
        FileAccessMode.ReadWrite -> writeFilesKey(scope)
    }
}
