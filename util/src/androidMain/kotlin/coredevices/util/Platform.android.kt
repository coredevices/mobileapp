package coredevices.util
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import androidx.core.net.toUri
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import kotlinx.coroutines.flow.MutableSharedFlow

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val deviceModelName: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}"
    override suspend fun openUrl(url: String) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW, url.toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override suspend fun runWithBgTask(name: String, task: suspend () -> Unit) {
        task()
    }

    override val isSecondaryProfile: Boolean by lazy {
        val userManager = context.getSystemService(UserManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) userManager.isProfile else !userManager.isSystemUser
    }

    override val previousRunCrashed: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return Firebase.crashlytics.didCrashOnPreviousExecution()
            }
            val am = context.getSystemService(ActivityManager::class.java)
            // The newest record is often an isolated child (WebView), so only look at the main process.
            val lastExit = am.getHistoricalProcessExitReasons(context.packageName, 0, 0)
                .filter { it.processName == context.packageName }
                .maxByOrNull { it.timestamp }
            return when (lastExit?.reason) {
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_ANR,
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> true
                else -> false
            }
        }

    companion object {
        val audioPermResults = MutableSharedFlow<Boolean>()
        val alarmPermResults = MutableSharedFlow<Boolean>()
        val notificationPermResults = MutableSharedFlow<Boolean>()
        val notificationPermTrigger = MutableSharedFlow<Unit>()
        val alarmPermTrigger = MutableSharedFlow<Unit>()

        suspend fun triggerNotificationPermRequest() {
            notificationPermTrigger.emit(Unit)
        }

        suspend fun triggerAlarmPermRequest() {
            alarmPermTrigger.emit(Unit)
        }
    }
}