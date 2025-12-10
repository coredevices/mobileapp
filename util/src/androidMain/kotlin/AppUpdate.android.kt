package coredevices.coreapp.util

import PlatformUiContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.requestUpdateFlow
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.util.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

actual data class AppUpdatePlatformContent(
    val androidUpdate: AppUpdateInfo
)

class AndroidAppUpdate(
    private val appUpdateManager: AppUpdateManager,
    private val settings: Settings,
    private val context: Context,
) : AppUpdate {
    private val logger = Logger.withTag("AndroidAppUpdate")

    override val updateAvailable: StateFlow<AppUpdateState> = appUpdateManager.requestUpdateFlow()
        .onStart { emit(AppUpdateResult.NotAvailable) } // Emit a loading state initially
        .catch { exception ->
            Logger.w(exception) { "Failed to check for CoreApp updates" }
            emit(AppUpdateResult.NotAvailable)
        }
        .map { result ->
            when (result) {
                is AppUpdateResult.Available -> AppUpdateState.UpdateAvailable(
                    AppUpdatePlatformContent(result.updateInfo)
                )

                else -> AppUpdateState.NoUpdateAvailable
            }
        }
        .onEach { result ->
            when (result) {
                AppUpdateState.NoUpdateAvailable -> Unit
                is AppUpdateState.UpdateAvailable -> {
                    val lastPromptedMs = settings.getLong(LAST_PROMPTED_KEY, 0L)
                    val nowMs = System.currentTimeMillis()
                    val diff = (nowMs - lastPromptedMs).milliseconds
                    if (diff > NOTIFICATION_ALLOWED_PERIOD) {
                        settings.set(LAST_PROMPTED_KEY, nowMs)
                        createNotification(context)
                    } else {
                        Logger.d { "Not notifying for update - only $diff since last prompt" }
                    }
                }
            }
        }
        .stateIn(
            scope = GlobalScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 30.minutes.inWholeMilliseconds,
                replayExpirationMillis = 12.hours.inWholeMilliseconds,
            ),
            initialValue = AppUpdateState.NoUpdateAvailable,
        )

    override fun startUpdateFlow(uiContext: PlatformUiContext, update: AppUpdatePlatformContent) {
        if (update.androidUpdate.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            logger.d { "Starting update flow" }
            appUpdateManager.startUpdateFlowForResult(
                update.androidUpdate,
                AppUpdateType.IMMEDIATE,
                uiContext.activity,
                REQUEST_CODE_APP_UPDATE
            )
        } else {
            logger.d { "Update type not allowed" }
        }
    }

    private fun createNotification(context: Context) {
        val playStoreIntent = getPlayStoreMarketIntent(context, context.packageName)
        if (playStoreIntent == null) {
            logger.w { "Failed to create play store intent for notification" }
            return
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0, // Request code - can be any unique integer
            playStoreIntent,
            pendingIntentFlags
        )

        context.createChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pebble App Update Available")
            .setContentText("Please update the Pebble app!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        val manager = context.getSystemService(NotificationManager::class.java)
        logger.d { "Posting app udate available notification" }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun getPlayStoreMarketIntent(context: Context, packageName: String): Intent? {
        val marketIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                setPackage("com.android.vending")
            }
        return if (marketIntent.resolveActivity(context.packageManager) != null) {
            marketIntent
        } else {
            null
        }
    }

    private fun Context.createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Pebble App Updates"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val REQUEST_CODE_APP_UPDATE = 12346
        private const val LAST_PROMPTED_KEY = "last_prompted_app_update"
        private val NOTIFICATION_ALLOWED_PERIOD = 1.days
        private const val CHANNEL_ID = "app_update_channel"
        private const val NOTIFICATION_ID = 3006089
    }
}