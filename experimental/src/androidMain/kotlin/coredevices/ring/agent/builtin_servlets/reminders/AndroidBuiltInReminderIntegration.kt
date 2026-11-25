package coredevices.ring.agent.builtin_servlets.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.koin.core.component.inject

class AndroidBuiltInReminderIntegration : BuiltInReminderIntegration() {
    private val context: Context by inject()

    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override suspend fun schedulingBlocker(isGeofence: Boolean): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
            "Notification permission not granted, enable notifications for the app in Settings."
        isGeofence ->
            if (hasBackgroundLocation()) null
            else "Background location permission not granted, set location access to 'Allow all the time' in Settings."
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() ->
            "No permissions to schedule reminders, check under 'Special app access > Alarms and reminders' in Settings app."
        else -> null
    }

    private fun hasBackgroundLocation() =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)

    // Not needed on android
    override suspend fun purgeLegacySchedules() {}
}
