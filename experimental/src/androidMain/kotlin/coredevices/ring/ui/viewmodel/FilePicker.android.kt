package coredevices.ring.ui.viewmodel

import PlatformUiContext
import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import java.io.File
import kotlin.coroutines.resume

actual suspend fun pickZipFile(uiContext: PlatformUiContext): Path? {
    val activity = uiContext.activity
    val registry = activity as? ActivityResultRegistryOwner
        ?: error("Activity is not an ActivityResultRegistryOwner")

    return suspendCancellableCoroutine { cont ->
        val launcher = registry.activityResultRegistry.register(
            "pickBackupZip",
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    // Copy to a temp file so we can read it with kotlinx.io
                    val tempFile = File(activity.cacheDir, "import-backup.zip")
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    cont.resume(Path(tempFile.absolutePath))
                } else {
                    cont.resume(null)
                }
            } else {
                cont.resume(null)
            }
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        launcher.launch(intent)
    }
}
