package coredevices.pebble.config

import PlatformUiContext
import io.rebble.libpebblecommon.permissions.PermissionDecision
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginNativeException
import io.rebble.libpebblecommon.plugin.files.FileAccessMode
import io.rebble.libpebblecommon.plugin.files.FileEntryKind
import io.rebble.libpebblecommon.plugin.files.FileHandleInfo
import io.rebble.libpebblecommon.plugin.files.PluginFileAccessApi
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** What a config page asks for with `Pebble.showOpenFilePicker` / `Pebble.showDirectoryPicker`. */
@Serializable
data class FilePickRequest(
    val kind: FileEntryKind,
    val mode: FileAccessMode = FileAccessMode.Read,
    /** MIME types to offer, for a file picker. Empty means anything. */
    val accept: List<String> = emptyList(),
)

/**
 * A picked entry. [scope] is what the permission is keyed on: the uri on Android, the path on
 * iOS. [handle] is what the platform needs to reach it again (the iOS bookmark; the uri itself on
 * Android).
 */
data class PickedPluginEntry(val scope: String, val name: String, val handle: String)

/**
 * Shows the platform picker and persists access to whatever the user chooses, or returns null if
 * they back out. Read-write is only honoured where the platform actually granted it.
 */
internal expect suspend fun pickPluginFile(
    uiContext: PlatformUiContext,
    request: FilePickRequest,
): PickedPluginEntry?

/** File access on behalf of the plugin an open config page belongs to. */
interface ConfigPageFileAccess {
    /** Null when the user cancelled. Throws `PERMISSION_DENIED` if the plugin may not ask. */
    suspend fun pick(request: FilePickRequest): FileHandleInfo?
    suspend fun handles(): List<FileHandleInfo>
    suspend fun revoke(scope: String)
}

internal class PluginConfigFileAccess(
    private val access: PluginFileAccessApi,
    private val pluginUuid: Uuid,
    private val uiContext: PlatformUiContext,
) : ConfigPageFileAccess {
    override suspend fun pick(request: FilePickRequest): FileHandleInfo? {
        // The bare ReadFiles / WriteFiles is granted at install or from the app's page; a pick never asks.
        val decision = access.checkFilePermission(pluginUuid, request.mode)
        if (decision != PermissionDecision.Granted) {
            throw PluginNativeException(PluginErrors.PERMISSION_DENIED, "this plugin may not ask for files ($decision)")
        }
        val picked = pickPluginFile(uiContext, request) ?: return null
        return access.grantPluginFile(pluginUuid, picked.scope, request.mode, picked.handle)
    }

    override suspend fun handles() = access.pluginFileHandles(pluginUuid)

    override suspend fun revoke(scope: String) = access.revokePluginFile(pluginUuid, scope)
}
