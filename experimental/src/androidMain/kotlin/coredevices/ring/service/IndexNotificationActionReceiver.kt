package coredevices.ring.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import coredevices.ring.agent.ShortcutActionHandler
import com.eygraber.uri.toKmpUriOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Runs a notification action's deep link without opening the app. */
class IndexNotificationActionReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private val logger = Logger.withTag("IndexNotificationActionReceiver")
    }

    private val shortcutActionHandler: ShortcutActionHandler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data?.toKmpUriOrNull()
        if (uri == null || !shortcutActionHandler.handleDeepLink(uri)) {
            logger.w { "Unhandled notification action: ${intent.data}" }
        }
    }
}
