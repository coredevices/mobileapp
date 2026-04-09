package coredevices.ring.ui.viewmodel

import PlatformUiContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual suspend fun writeToDownloads(uiContext: PlatformUiContext, path: Path) {
    val url = NSURL.fileURLWithPath(path.toString())
    val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)

    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                    cont.resume(Unit)
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    cont.resume(Unit)
                }
            }
            picker.delegate = delegate
            uiContext.viewController.presentViewController(picker, animated = true, completion = null)
        }
    }
    delay(250)
}