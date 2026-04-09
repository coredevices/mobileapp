package coredevices.ring.ui.viewmodel

import PlatformUiContext
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.io.files.Path
import androidx.core.net.toUri
import coredevices.util.PermissionResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.mp.KoinPlatform
import java.io.File
import kotlin.coroutines.resume

actual suspend fun writeToDownloads(uiContext: PlatformUiContext, path: Path) {
    val registry = uiContext.activity as? ActivityResultRegistryOwner ?: error("Activity is not an ActivityResultRegistryOwner")
    suspendCancellableCoroutine { continuation ->
        val launcher = registry.activityResultRegistry.register(
            "writeRecordingToDownloads",
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    val outputStream = uiContext.activity.contentResolver.openOutputStream(it)
                    val inputStream = File(path.toString()).inputStream()
                    inputStream.use { input ->
                        outputStream?.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            continuation.resume(Unit)
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, path.name)
            val downloads = "content://com.android.providers.downloads.documents/document/downloads".toUri()
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloads)
        }
        launcher.launch(intent)
    }
}