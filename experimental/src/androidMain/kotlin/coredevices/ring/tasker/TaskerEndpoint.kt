package coredevices.ring.tasker

import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Shared Android plumbing for routing Index content (notes, reminders) to
 * [Tasker](https://tasker.joaoapps.com/). Every item goes out two ways, and the user wires up
 * whichever suits them (setting up both double-processes the item):
 *
 * - an `ACTION_SEND` activity intent, picked up with a "Received Share" event profile — the content
 *   arrives in `%rs_text` and custom extras are dropped;
 * - an [ACTION_INDEX_ITEM] broadcast, picked up with an "Intent Received" event profile — every
 *   extra (`text`, `message_type`, `timestamp`, and caller extras like `deadline`) arrives as its
 *   own Tasker variable. An explicit broadcast is also exempt from the background-activity-launch
 *   restrictions that make the activity path unreliable behind a locked screen.
 *
 * There is no remote auth — "connecting" simply records that the user opted in (see
 * [IntegrationTokenStorage] usage in the note/reminder clients), and Tasker is only treated as
 * available while its package is installed.
 */
internal object TaskerEndpoint : KoinComponent {
    const val PACKAGE = "net.dinglisch.android.taskerm"
    const val TOKEN_STORAGE_KEY = "tasker"

    /** Action of the broadcast that feeds a Tasker "Intent Received" profile; users type this in. */
    const val ACTION_INDEX_ITEM = "coredevices.coreapp.INDEX_ITEM"

    private val context: Context by inject()
    private val logger = Logger.withTag("TaskerEndpoint")

    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Sends [text] to Tasker. Returns the timestamp used for the payload, which callers surface as
     * the created note/reminder id. Throws [IllegalStateException] if Tasker is not installed.
     */
    fun send(text: String, messageType: String, extras: Map<String, String> = emptyMap()): String {
        check(isInstalled()) { "Tasker is not installed" }
        // Colons are invalid in filenames on Android external storage, so keep the timestamp
        // filename-safe — Tasker recipes commonly use it directly (e.g. an Obsidian note filename).
        val timestamp = Clock.System.now().toString().replace(":", "-")
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage(PACKAGE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra("messageType", messageType)
            putExtra("timestamp", timestamp)
            extras.forEach { (key, value) -> putExtra(key, value) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        // "Received Share" (above) hides custom extras; this broadcast feeds "Intent Received",
        // where every extra becomes a Tasker variable.
        context.sendBroadcast(
            Intent(ACTION_INDEX_ITEM).apply {
                setPackage(PACKAGE)
                putExtra("text", text)
                putExtra("message_type", messageType)
                putExtra("timestamp", timestamp)
                extras.forEach { (key, value) -> putExtra(key, value) }
            }
        )

        logger.i { "Sent $messageType to Tasker (${text.length} chars)" }
        return timestamp
    }
}
