package coredevices.util
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    override suspend fun runWithBgTask(name: String, task: suspend () -> Unit) {
        val scope = CoroutineScope(Dispatchers.Default)
        UIApplication.sharedApplication.beginBackgroundTaskWithName(name) {
            scope.cancel("Background task expired")
        }.also { bgTaskId ->
            try {
                scope.launch {
                    task()
                }.join()
            } catch (e: CancellationException) {
                // swallow cancellation exceptions since they just mean the background task was killed by the system
            } finally {
                UIApplication.sharedApplication.endBackgroundTask(bgTaskId)
            }
        }
    }
}