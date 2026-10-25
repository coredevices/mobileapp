package coredevices.pebble.config

import PlatformUiContext
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.plugin.files.FileAccessMode
import io.rebble.libpebblecommon.plugin.files.FileEntryKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val logger = Logger.withTag("PluginFilePicker")
private val mutex = Mutex()

internal actual suspend fun pickPluginFile(
    uiContext: PlatformUiContext,
    request: FilePickRequest,
): PickedPluginEntry? = mutex.withLock {
    val activity = uiContext.activity
    val registry = activity as? ActivityResultRegistryOwner
    if (registry == null) {
        logger.w { "activity is not an ActivityResultRegistryOwner; can't show a picker" }
        return@withLock null
    }
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            lateinit var launcher: ActivityResultLauncher<Intent>
            launcher = registry.activityResultRegistry.register(
                "pluginFilePicker",
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                launcher.unregister()
                cont.resume(persist(activity, request, result.resultCode, result.data))
            }
            cont.invokeOnCancellation { launcher.unregister() }
            try {
                launcher.launch(intentFor(request))
            } catch (e: ActivityNotFoundException) {
                logger.w(e) { "no document picker available" }
                launcher.unregister()
                cont.resume(null)
            }
        }
    }
}

private fun intentFor(request: FilePickRequest): Intent {
    val intent = when (request.kind) {
        FileEntryKind.Directory -> Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        FileEntryKind.File -> Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            if (request.accept.isNotEmpty()) putExtra(Intent.EXTRA_MIME_TYPES, request.accept.toTypedArray())
        }
    }
    return intent.addFlags(request.mode.uriFlags() or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
}

/** Takes the persistable permission, so the plugin can reach the entry from a later session. */
private fun persist(activity: Activity, request: FilePickRequest, resultCode: Int, data: Intent?): PickedPluginEntry? {
    if (resultCode != Activity.RESULT_OK || data == null) return null
    val uri = data.data ?: return null
    val flags = request.mode.uriFlags()
    if ((data.flags and flags) != flags) {
        logger.w { "picker did not grant ${request.mode}" }
        return null
    }
    val persisted = runCatching { activity.contentResolver.takePersistableUriPermission(uri, flags) }
        .onFailure { logger.w(it) { "couldn't persist access" } }
        .isSuccess
    if (!persisted) return null
    val document = when (request.kind) {
        FileEntryKind.Directory -> DocumentFile.fromTreeUri(activity, uri)
        FileEntryKind.File -> DocumentFile.fromSingleUri(activity, uri)
    }
    val name = runCatching { document?.name }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
    return PickedPluginEntry(scope = uri.toString(), name = name, handle = uri.toString())
}

private fun FileAccessMode.uriFlags() = when (this) {
    FileAccessMode.Read -> Intent.FLAG_GRANT_READ_URI_PERMISSION
    FileAccessMode.ReadWrite -> Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
}
