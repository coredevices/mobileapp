package coredevices.coreapp.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.LibPebble
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Attaches a PebbleOS QEMU emulator as a watch, for automated testing without hardware:
 *
 *     adb shell am broadcast -a coredevices.coreapp.ADD_QEMU_WATCH \
 *       -n coredevices.coreapp/coredevices.coreapp.debug.QemuSetupReceiver \
 *       --es host 127.0.0.1 --ei port 12344
 *
 * Guarded by android.permission.DUMP in the manifest, which only adb/shell and system hold, so no
 * installed app can reach it — safe to keep in release builds for CI.
 */
class QemuSetupReceiver : BroadcastReceiver(), KoinComponent {
    private val libPebble: LibPebble by inject()
    private val logger = Logger.withTag("QemuSetupReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.getStringExtra("host") ?: DEFAULT_HOST
        val port = intent.getIntExtra("port", DEFAULT_PORT)
        val connect = intent.getBooleanExtra("connect", true)
        logger.i { "adding QEMU watch at $host:$port (connect=$connect)" }
        libPebble.addQemuWatch("$host:$port", connect)
    }

    companion object {
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 12344
    }
}
