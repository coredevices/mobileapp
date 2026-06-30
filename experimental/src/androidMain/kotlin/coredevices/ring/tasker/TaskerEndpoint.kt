package coredevices.ring.tasker

import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Shared Android plumbing for routing Index content (notes, reminders) to
 * [Tasker](https://tasker.joaoapps.com/) via an `ACTION_SEND` intent. Users pick the payload up with
 * a "Received Share" event profile filtered to this app and fan it out wherever they like — for
 * example, appending to an Obsidian vault.
 *
 * The content is sent as [Intent.EXTRA_TEXT]; `messageType`, `timestamp`, and any caller-supplied
 * extras ride alongside so the Tasker side can branch on them.
 *
 * There is no remote auth — "connecting" simply records that the user opted in (see
 * [IntegrationTokenStorage] usage in the note/reminder clients), and Tasker is only treated as
 * available while its package is installed.
 *
 * Note: `startActivity` from a background context is subject to Android's background-activity-launch
 * restrictions, so delivery while the screen is locked is not guaranteed.
 */
internal object TaskerEndpoint : KoinComponent {
    const val PACKAGE = "net.dinglisch.android.taskerm"
    const val TOKEN_STORAGE_KEY = "tasker"

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
        logger.i { "Sent $messageType to Tasker (${text.length} chars)" }
        return timestamp
    }
}
