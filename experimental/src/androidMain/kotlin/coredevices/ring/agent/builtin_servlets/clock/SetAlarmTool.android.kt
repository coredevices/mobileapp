package coredevices.ring.agent.builtin_servlets.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import org.koin.mp.KoinPlatform

actual suspend fun setAlarm(hours: Int, minutes: Int, label: String?) {
    val context = KoinPlatform.getKoin().get<Context>()
    // Setting an alarm launches the clock app's activity. When we run in the background (the common
    // case for the ring), startActivity is silently dropped unless we hold a CompanionDeviceManager
    // association, which grants the background-activity-launch exemption.
    check(context.hasCompanionDeviceAssociation()) {
        "Background alarms require a device association, check the Devices tab to fix."
    }
    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        if (label != null) {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        putExtra(AlarmClock.EXTRA_HOUR, hours)
        putExtra(AlarmClock.EXTRA_MINUTES, minutes)
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    check(context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
        "Couldn't find a Clock app supporting setting alarms from other apps."
    }
    context.startActivity(intent)
}