package coredevices.coreapp.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.devconnection.DevConnectionServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Starts the LAN developer connection so pebble-tool can reach it over an adb tunnel:
 *
 *     adb shell am broadcast -a coredevices.coreapp.DEV_CONNECTION \
 *       -n coredevices.coreapp/coredevices.coreapp.debug.DevConnectionReceiver
 *     adb forward tcp:0 tcp:9000
 *
 * Replies with the server port as the broadcast result data, or a non-zero result code and a
 * message on failure. Guarded by android.permission.DUMP in the manifest, which only adb/shell
 * and system hold, so no installed app can reach it — safe to keep in release builds.
 */
class DevConnectionReceiver : BroadcastReceiver(), KoinComponent {
    private val libPebble: LibPebble by inject()
    private val logger = Logger.withTag("DevConnectionReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val watch = libPebble.watches.value
                    .filterIsInstance<ConnectedPebble.DevConnection>()
                    .firstOrNull()
                if (watch == null) {
                    logger.w { "No connected watch to start a dev connection on" }
                    pendingResult.setResult(RESULT_FAILED, "no watch connected", null)
                    return@launch
                }
                // Restart unconditionally: an already-running connection may be the cloudpebble
                // proxy, and the LAN server only releases port 9000 once its job completes.
                withTimeout(RESTART_TIMEOUT) {
                    watch.stopDevConnection()
                    watch.devConnectionActive.first { !it }
                    watch.startDevConnection(forceLan = true)
                }
                logger.i { "Dev connection started on port ${DevConnectionServer.PORT}" }
                pendingResult.setResult(RESULT_OK, DevConnectionServer.PORT.toString(), null)
            } catch (e: Exception) {
                logger.e(e) { "Failed to start dev connection" }
                pendingResult.setResult(RESULT_FAILED, e.message ?: "failed to start", null)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val RESULT_OK = 0
        private const val RESULT_FAILED = 1
        private val RESTART_TIMEOUT = 10.seconds
    }
}
