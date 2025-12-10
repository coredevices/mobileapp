import kotlinx.io.files.Path
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual class PlatformShareLauncher() {
    actual fun share(text: String?, file: Path) {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController!!
        val activityViewController = UIActivityViewController(listOf(file.toNSURL()), null)
        viewController.presentViewController(activityViewController, true, null)
    }
}