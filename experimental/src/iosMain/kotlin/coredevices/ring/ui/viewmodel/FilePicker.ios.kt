package coredevices.ring.ui.viewmodel

import PlatformUiContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerMode
import platform.darwin.NSObject
import kotlin.coroutines.resume

@Suppress("DEPRECATION")
actual suspend fun pickZipFile(uiContext: PlatformUiContext): Path? {
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            // Use string-based UTI for broader cinterop compatibility
            val picker = UIDocumentPickerViewController(
                documentTypes = listOf("com.pkware.zip-archive", "public.zip-archive"),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
            )
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull()
                    if (url != null) {
                        val nsUrl = url as platform.Foundation.NSURL
                        cont.resume(Path(nsUrl.path ?: ""))
                    } else {
                        cont.resume(null)
                    }
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    cont.resume(null)
                }
            }
            picker.delegate = delegate
            uiContext.viewController.presentViewController(picker, animated = true, completion = null)
        }
    }
}
