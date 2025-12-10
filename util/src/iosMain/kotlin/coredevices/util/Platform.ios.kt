package coredevices.util
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    override suspend fun openUrl(url: String) {
        val completable = CompletableDeferred<Unit>()
        UIApplication.sharedApplication.openURL(NSURL.URLWithString(url)!!, mapOf<Any?, Any>()) {
            completable.complete(Unit)
        }
    }
}