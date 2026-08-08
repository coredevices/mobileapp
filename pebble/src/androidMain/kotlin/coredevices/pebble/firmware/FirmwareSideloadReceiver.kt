package coredevices.pebble.firmware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.ui.showDebugOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File

private const val ACTION_SIDELOAD_FIRMWARE = "coredevices.pebble.SIDELOAD_FIRMWARE"
private const val EXTRA_PATH = "path"

class FirmwareSideloadReceiver : BroadcastReceiver(), KoinComponent {
    private val logger = Logger.withTag("FirmwareSideloadReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SIDELOAD_FIRMWARE) {
            return
        }
        val pathExtra = intent.getStringExtra(EXTRA_PATH)
        if (pathExtra == null) {
            logger.w { "Ignoring sideload broadcast: missing \"$EXTRA_PATH\" extra" }
            return
        }
        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (!get<Settings>().showDebugOptions()) {
                    logger.w { "Ignoring sideload broadcast: debug options are disabled" }
                    get<PebbleDeepLinkHandler>().showMessage(
                        "Sideload ignored: turn on Show debug options in settings first"
                    )
                    return@launch
                }
                val file = if (pathExtra.startsWith("/")) {
                    File(pathExtra)
                } else {
                    File(context.externalCacheDir, pathExtra)
                }
                if (!file.isFile) {
                    logger.w { "Ignoring sideload broadcast: ${file.absolutePath} not found" }
                    return@launch
                }
                logger.i { "Sideload broadcast for ${file.absolutePath}" }
                get<PebbleDeepLinkHandler>().sideloadFirmware(Path(file.absolutePath))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
