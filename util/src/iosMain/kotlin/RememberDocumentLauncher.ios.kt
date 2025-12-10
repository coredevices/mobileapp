
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.interop.LocalUIViewController
import coredevices.util.toBuffer
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCompatible
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeJPEG
import platform.UniformTypeIdentifiers.UTTypeMPEG4Movie
import platform.UniformTypeIdentifiers.UTTypePNG
import platform.UniformTypeIdentifiers.loadDataRepresentationForContentType
import platform.UniformTypeIdentifiers.registeredContentTypes
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private fun NSURL.toDocumentAttachment(): DocumentAttachment {
    val shouldStopAccessing = this.startAccessingSecurityScopedResource()
    try {
        memScoped {
            val data = NSData.dataWithContentsOfURL(this@toDocumentAttachment)
            val pathExtension = this@toDocumentAttachment.pathExtension
            val mimeType = pathExtension?.let { UTType.typeWithFilenameExtension(it) }?.preferredMIMEType

            if (data != null) {
                val buf = data.toBuffer()
                return DocumentAttachment(
                    fileName = this@toDocumentAttachment.lastPathComponent ?: "",
                    mimeType = mimeType,
                    source = buf,
                    size = data.length.toLong(),
                )
            } else {
                error("Failed to read data from URL: ${this@toDocumentAttachment}")
            }
        }
    } finally {
        if (shouldStopAccessing) {
            this.stopAccessingSecurityScopedResource()
        }
    }
}

@Composable
actual fun rememberOpenDocumentLauncher(onResult: (List<DocumentAttachment>?) -> Unit): (mimeTypeFilter: List<String>) -> Unit {
    val presentationController = LocalUIViewController.current
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
                val results = urls.map { it.toDocumentAttachment() }
                if (results.isNotEmpty()) {
                    onResult(results)
                } else {
                    onResult(null)
                }
            }
        }
    }
    return { mimeTypeFilter ->
        val pickerController = UIDocumentPickerViewController(documentTypes = listOf("public.item"), UIDocumentPickerMode.UIDocumentPickerModeOpen)
        pickerController.delegate = delegate
        presentationController.presentViewController(pickerController, animated = true, completion = null)
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
actual fun rememberOpenPhotoLauncher(onResult: (List<DocumentAttachment>?) -> Unit): () -> Unit {
    val presentationController = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val delegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, completion = null)
                @Suppress("UNCHECKED_CAST")
                didFinishPicking as List<PHPickerResult>
                val results = didFinishPicking.map {
                    val provider = it.itemProvider
                    scope.async {
                        suspendCancellableCoroutine { cont ->
                            val type = when {
                                provider.registeredContentTypes().contains(UTTypePNG) -> UTTypePNG
                                provider.registeredContentTypes().contains(UTTypeJPEG) -> UTTypeJPEG
                                provider.registeredContentTypes().contains(UTTypeMPEG4Movie) -> UTTypeMPEG4Movie
                                else -> null
                            }
                            if (type == null) {
                                cont.resume(null)
                                return@suspendCancellableCoroutine
                            }
                            provider.loadDataRepresentationForContentType(type) { data, error ->
                                if (error != null) {
                                    cont.resumeWithException(Exception("Failed to load data: ${error.localizedDescription}"))
                                } else if (data != null) {
                                    val buf = data.toBuffer()
                                    val attachment = DocumentAttachment(
                                        fileName = it.itemProvider.suggestedName
                                            ?.let { it + ".${type.preferredFilenameExtension}" } ?: Uuid.random().toString(),
                                        mimeType = type.preferredMIMEType,
                                        source = buf,
                                        size = data.length.toLong(),
                                    )
                                    cont.resume(attachment)
                                } else {
                                    cont.resume(null)
                                }
                            }
                        }
                    }
                }
                scope.launch {
                    val resolvedResults = results.awaitAll().filterNotNull()
                    if (resolvedResults.isNotEmpty()) {
                        onResult(resolvedResults)
                    } else {
                        onResult(null)
                    }
                }
            }
        }
    }
    val imagePickerController = remember {
        val configuration = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary()).apply {
            setFilter(PHPickerFilter.anyFilterMatchingSubfilters(
                listOf(
                    PHPickerFilter.imagesFilter,
                    PHPickerFilter.screenshotsFilter,
                    PHPickerFilter.screenRecordingsFilter,
                    PHPickerFilter.videosFilter
                )
            ))
            setPreferredAssetRepresentationMode(PHPickerConfigurationAssetRepresentationModeCompatible)
            setSelection(PHPickerConfigurationSelectionOrdered)
            setSelectionLimit(8)
        }
        PHPickerViewController(configuration = configuration).apply {
            setDelegate(delegate)
        }
    }

    return {
        presentationController.presentViewController(imagePickerController, animated = true, completion = null)
    }
}