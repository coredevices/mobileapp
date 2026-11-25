package coredevices.util

import PlatformUiContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.Path
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class PickerDelegate(private val onDone: () -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) = onDone()
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onDone()
}

actual suspend fun writeToDownloads(uiContext: PlatformUiContext, path: Path, mimeType: String) {
    val url = NSURL.fileURLWithPath(path.toString())
    withContext(Dispatchers.Main) {
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        var continuation: CancellableContinuation<Unit>? = null
        val delegate = PickerDelegate { continuation?.takeIf { it.isActive }?.resume(Unit) }
        picker.delegate = delegate
        try {
            suspendCancellableCoroutine { cont ->
                continuation = cont
                var presenter = uiContext.viewController
                while (true) presenter = presenter.presentedViewController ?: break
                presenter.presentViewController(picker, animated = true, completion = null)
                if (picker.presentingViewController == null) {
                    cont.resumeWithException(IOException("Couldn't present document picker"))
                }
            }
        } finally {
            if (continuation?.isCancelled == true) picker.dismissViewControllerAnimated(true, completion = null)
            // picker.delegate is weak: referencing delegate here keeps it alive while suspended
            if (picker.delegate === delegate) picker.delegate = null
        }
    }
}
