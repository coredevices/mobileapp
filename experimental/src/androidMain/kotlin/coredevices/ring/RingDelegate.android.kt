package coredevices.ring

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import coredevices.HackyPermissionRequesterProvider
import coredevices.ring.glance.VoiceWidgetReceiver
import coredevices.ring.service.RingBackgroundManager
import coredevices.util.Permission

actual class RingDelegate(
    private val context: Context,
    private val ringBackgroundManager: RingBackgroundManager,
    private val permissionRequester: HackyPermissionRequesterProvider,
) {
    private val logger = Logger.withTag("RingDelegate")

    actual fun requiredRuntimePermissions(): Set<Permission> = buildSet {
        addAll(setOf(
            Permission.RecordAudio,
            Permission.PostNotifications,
            Permission.Bluetooth,
            Permission.ExternalStorage,
            Permission.SetAlarms,
        ))
        if (isBeeperAvailable()) {
            add(Permission.Beeper)
        }
    }

    /**
     * Called by activity onCreate / didFinishLaunching to initialize the Ring module.
     */
    actual suspend fun init() {
        ringBackgroundManager.startBackgroundIfEnabled()
        //enableWidget(context)
    }
}

fun enableWidget(context: Context) {
    val componentName = ComponentName(
        context,
        VoiceWidgetReceiver::class.java
    ) // Replace YourWidgetReceiver::class.java with your actual receiver class
    context.packageManager.setComponentEnabledSetting(
        componentName,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
}
