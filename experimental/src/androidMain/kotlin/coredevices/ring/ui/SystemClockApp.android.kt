package coredevices.ring.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import org.koin.mp.KoinPlatform

actual fun openSystemClockApp(fireKind: ItemMetadata.Scheduled.FireKind): Boolean {
    val context = KoinPlatform.getKoin().get<Context>()
    val action = when (fireKind) {
        ItemMetadata.Scheduled.FireKind.Timer -> AlarmClock.ACTION_SHOW_TIMERS
        ItemMetadata.Scheduled.FireKind.Alarm -> AlarmClock.ACTION_SHOW_ALARMS
    }
    val intent = Intent(action).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
