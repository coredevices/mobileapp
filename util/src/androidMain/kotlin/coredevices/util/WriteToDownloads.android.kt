package coredevices.util

import PlatformUiContext
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.Path
import java.io.File
import kotlin.coroutines.resume
import kotlin.uuid.Uuid

actual suspend fun writeToDownloads(uiContext: PlatformUiContext, path: Path, mimeType: String) {
    val activity = uiContext.activity
    val registry = (activity as? ActivityResultRegistryOwner)?.activityResultRegistry
        ?: throw IOException("Activity is not an ActivityResultRegistryOwner")
    val uri = suspendCancellableCoroutine<Uri?> { continuation ->
        lateinit var launcher: ActivityResultLauncher<Intent>
        launcher = registry.register(
            "writeToDownloads-${Uuid.random()}",
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            launcher.unregister()
            continuation.resume(result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK })
        }
        continuation.invokeOnCancellation { launcher.unregister() }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, path.name)
            val downloads = "content://com.android.providers.downloads.documents/document/downloads".toUri()
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloads)
        }
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            launcher.unregister()
            throw IOException("Couldn't open document picker", e)
        }
    }
    uri ?: return
    withContext(Dispatchers.IO) {
        try {
            val output = activity.contentResolver.openOutputStream(uri) ?: throw IOException("Couldn't open $uri")
            output.use { File(path.toString()).inputStream().use { input -> input.copyTo(it) } }
        } catch (e: Exception) {
            throw e as? IOException ?: IOException("Couldn't write $uri", e)
        }
    }
}
