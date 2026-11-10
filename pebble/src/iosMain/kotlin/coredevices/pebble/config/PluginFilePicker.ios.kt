package coredevices.pebble.config

import PlatformUiContext
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.plugin.files.FileEntryKind
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationMinimalBookmark
import platform.Foundation.base64EncodedStringWithOptions
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import kotlin.coroutines.resume

private val logger = Logger.withTag("PluginFilePicker")
private val mutex = Mutex()

// The picker only keeps a weak reference to its delegate; this keeps it alive until it answers.
private var pickerDelegate: UIDocumentPickerDelegateProtocol? = null

/**
 * iOS has no read-only pick: the mode is recorded on the grant and enforced by the host, as a
 * browser does.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun pickPluginFile(
    uiContext: PlatformUiContext,
    request: FilePickRequest,
): PickedPluginEntry? = mutex.withLock {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypesFor(request))
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                    pickerDelegate = null
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    val path = url?.path
                    val handle = url?.let(::bookmark)
                    cont.resume(
                        if (url != null && path != null && handle != null) {
                            PickedPluginEntry(scope = path, name = url.lastPathComponent ?: "", handle = handle)
                        } else {
                            null
                        }
                    )
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    pickerDelegate = null
                    cont.resume(null)
                }
            }
            pickerDelegate = delegate
            picker.delegate = delegate
            cont.invokeOnCancellation { pickerDelegate = null }
            topmostViewController(uiContext.viewController).presentViewController(picker, animated = true, completion = null)
        }
    }
}

private fun contentTypesFor(request: FilePickRequest): List<UTType> = when (request.kind) {
    FileEntryKind.Directory -> listOf(UTTypeFolder)
    FileEntryKind.File -> request.accept.mapNotNull { UTType.typeWithMIMEType(it) }.ifEmpty { listOf(UTTypeItem) }
}

@OptIn(ExperimentalForeignApi::class)
private fun bookmark(url: NSURL): String? {
    val started = url.startAccessingSecurityScopedResource()
    try {
        val data = url.bookmarkDataWithOptions(
            NSURLBookmarkCreationMinimalBookmark,
            includingResourceValuesForKeys = null,
            relativeToURL = null,
            error = null,
        )
        if (data == null) logger.w { "couldn't bookmark the picked entry" }
        return data?.base64EncodedStringWithOptions(0u)
    } finally {
        if (started) url.stopAccessingSecurityScopedResource()
    }
}

// Compose renders dialogs in their own, higher UIWindow; presenting from the root VC would land
// the picker behind one.
private fun topmostViewController(fallback: UIViewController): UIViewController {
    val window = UIApplication.sharedApplication.windows
        .filterIsInstance<UIWindow>()
        .filter { !it.hidden }
        .maxByOrNull { it.windowLevel }
    var controller = window?.rootViewController ?: fallback
    while (true) controller = controller.presentedViewController ?: break
    return controller
}
