package coredevices.ring.agent.builtin_servlets.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import org.koin.mp.KoinPlatform
import kotlin.time.Duration

actual suspend fun setTimer(duration: Duration, title: String?, skipUI: Boolean) {
    val context = KoinPlatform.getKoin().get<Context>()
    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, duration.inWholeSeconds.toInt())
        if (skipUI) {
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (title != null) {
            putExtra(AlarmClock.EXTRA_MESSAGE, title)
        }
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    check(context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
        "Couldn't find a Clock app supporting setting timers from other apps."
    }
    context.startActivity(intent)
}