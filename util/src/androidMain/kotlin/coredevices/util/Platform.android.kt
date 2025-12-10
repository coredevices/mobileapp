package coredevices.util
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableSharedFlow

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override suspend fun openUrl(url: String) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW, url.toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        const val NOTIFICATION_ID_BASE_REMINDER = 10
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