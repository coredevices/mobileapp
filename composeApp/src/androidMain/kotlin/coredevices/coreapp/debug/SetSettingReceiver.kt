package coredevices.coreapp.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Sets a persisted app setting from adb, so hardware-lab QA can put the app into a known state
 * without walking the UI:
 *
 *     # mark watch-onboarding seen, so connecting a watch does NOT trigger the onboarding route
 *     # (and its default-cohort firmware auto-upgrade) — lets QA choose any "from" firmware
 *     adb shell am broadcast -a coredevices.coreapp.SET_SETTING \
 *       -n coredevices.coreapp/coredevices.coreapp.debug.SetSettingReceiver \
 *       --es key hasSeenWatchOnboarding --ez value true
 *
 *     # turn on debug options (unlocks the SIDELOAD_FIRMWARE receiver)
 *     adb shell am broadcast -a coredevices.coreapp.SET_SETTING \
 *       -n coredevices.coreapp/coredevices.coreapp.debug.SetSettingReceiver \
 *       --es key showDebugOptions --ez value true
 *
 * A boolean `value` extra (`--ez`) stores a boolean; otherwise a string `value` extra (`--es`)
 * stores a string. Replies with the stored value as result data, or a non-zero result code and a
 * message. Guarded by android.permission.DUMP in the manifest — only adb/shell and system hold it,
 * so no installed app can reach it; safe to keep in release builds (mirrors DevConnectionReceiver).
 */
class SetSettingReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: Settings by inject()
    private val logger = Logger.withTag("SetSettingReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key")
        if (key.isNullOrEmpty()) {
            resultCode = RESULT_FAILED
            resultData = "missing \"key\" extra"
            return
        }
        val stored: String = when (val value = intent.extras?.get("value")) {
            is Boolean -> { settings.putBoolean(key, value); value.toString() }
            is String -> { settings.putString(key, value); value }
            else -> {
                resultCode = RESULT_FAILED
                resultData = "missing boolean/string \"value\" extra"
                return
            }
        }
        logger.i { "set $key = $stored" }
        resultCode = RESULT_OK
        resultData = stored
    }

    companion object {
        private const val RESULT_OK = 0
        private const val RESULT_FAILED = 1
    }
}
