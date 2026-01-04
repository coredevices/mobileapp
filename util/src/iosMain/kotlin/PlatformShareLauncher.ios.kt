import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.allocArrayOf
import kotlinx.io.files.Path
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.Foundation.create
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat

actual class PlatformShareLauncher() {
    actual fun share(text: String?, file: Path) {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController!!
        val activityViewController = UIActivityViewController(listOf(file.toNSURL()), null)
        viewController.presentViewController(activityViewController, true, null)
    }

    actual fun shareImage(image: ImageBitmap, filename: String) {
        val skiaBitmap = image.asSkiaBitmap()
        skiaBitmap.setAlphaType(org.jetbrains.skia.ColorAlphaType.OPAQUE)
        val data = org.jetbrains.skia.Image.makeFromBitmap(skiaBitmap).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG) ?: return
        val uiImage = UIImage(data = data.toNSData())
        
        dispatch_async(dispatch_get_main_queue()) {
            val root = getRootViewController()
            val top = getTopViewController(root)

            if (top != null) {
                val activityViewController = UIActivityViewController(listOf(uiImage), null)
                top.presentViewController(activityViewController, true, null)
            }
        }
    }

    private fun getRootViewController(): UIViewController? {
        return UIApplication.sharedApplication.keyWindow?.rootViewController
    }

    private fun getTopViewController(base: UIViewController?): UIViewController? {
        if (base is UINavigationController) {
            return getTopViewController(base.visibleViewController)
        }
        if (base is UITabBarController) {
            return getTopViewController(base.selectedViewController)
        }
        if (base?.presentedViewController != null) {
            return getTopViewController(base.presentedViewController)
        }
        return base
    }

    private fun org.jetbrains.skia.Data.toNSData(): platform.Foundation.NSData = memScoped {
        val byteArray = this@toNSData.bytes
        return@memScoped platform.Foundation.NSData.create(bytes = allocArrayOf(byteArray), length = byteArray.size.toULong())
    }
}