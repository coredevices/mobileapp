import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered

private val logger = Logger.withTag("DocumentLauncher")

@Composable
actual fun rememberOpenDocumentLauncher(onResult: (List<DocumentAttachment>?) -> Unit): (mimeTypeFilter: List<String>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            scope.launch {
                // Cloud providers (e.g. Nextcloud) download the file while it's opened.
                val sources = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        try {
                            context.contentResolver.openAttachment(uri)
                        } catch (e: Exception) {
                            logger.w(e) { "Couldn't open $uri" }
                            null
                        }
                    }
                }
                onResult(sources.ifEmpty { null })
            }
        }
    return { mimeTypeFilter ->
        launcher.launch(mimeTypeFilter.toTypedArray())
    }
}

private fun ContentResolver.openAttachment(uri: Uri): DocumentAttachment {
    val stream = openInputStream(uri) ?: error("Provider crashed")
    val mimeType = if (uri.scheme == "content") {
        getType(uri)
    } else {
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
    return DocumentAttachment(
        fileName = getNameFromUri(uri, this) ?: "unknown",
        mimeType = mimeType,
        source = stream.asSource().buffered(),
        size = stream.available().toLong(),
    )
}

private fun getNameFromUri(uri: Uri, contentResolver: ContentResolver): String? {
    if (uri.scheme == "content") {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                check(idx > -1)
                return it.getString(idx)
            }
        }
    } else {
        return uri.lastPathSegment
    }
    return null
}

@Composable
actual fun rememberOpenPhotoLauncher(onResult: (List<DocumentAttachment>?) -> Unit): () -> Unit {
    val launcher = rememberOpenDocumentLauncher(onResult)
    return {
        launcher(listOf("image/*"))
    }
}