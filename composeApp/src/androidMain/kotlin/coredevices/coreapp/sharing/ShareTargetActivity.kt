package coredevices.coreapp.sharing

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import io.rebble.libpebblecommon.shareintent.ShareIntentDispatcher
import io.rebble.libpebblecommon.shareintent.ShareTargetEntry
import io.rebble.libpebblecommon.shareintent.ShareTargetSync.Companion.EXTRA_WATCHAPP_UUID
import io.rebble.libpebblecommon.shareintent.ShareTargetSync.Companion.SHORTCUT_ID_PREFIX
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/**
 * Lightweight activity that handles the OS share-sheet routing the user
 * picked from a Sharing Shortcut surfaced by
 * [io.rebble.libpebblecommon.shareintent.ShareTargetSync] or from the
 * static "Pebble" intent-filter entry.
 *
 * Routing paths:
 *
 *  - **Direct Share** (user picked "Share to <Watchapp>" from the share
 *    sheet's direct-share row): system constructs an ACTION_SEND intent
 *    with [Intent.EXTRA_SHORTCUT_ID] set to the shortcut's id. We decode
 *    the watchapp uuid from that and dispatch directly.
 *
 *  - **Launcher long-press** (user tapped "Share to <Watchapp>" from a
 *    long-press menu): system uses the shortcut's registered intent
 *    verbatim, which carries [EXTRA_WATCHAPP_UUID].
 *
 *  - **Static fallback** (user picked "Pebble" from the apps row): no
 *    shortcut involvement; neither extra is set. Behavior depends on
 *    how many share-capable watchapps are installed:
 *      - zero: toast prompting the user to install a share-capable watchapp
 *      - one: dispatch directly to that watchapp (no chooser needed)
 *      - two+: show a chooser dialog so the user picks which watchapp
 *
 * The activity finishes immediately after dispatch — the actual delivery
 * survives via the application-scoped coroutine in the dispatcher.
 *
 * Implements [LibPebbleKoinComponent] because [ShareIntentDispatcher] is
 * registered in libpebble3's isolated Koin context, not the host app's
 * global one.
 */
class ShareTargetActivity : Activity(), LibPebbleKoinComponent {
    private val dispatcher: ShareIntentDispatcher by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handleShareIntent(intent)
        } catch (e: Exception) {
            logger.e(e) { "share intent handling failed" }
            Toast.makeText(this, "Couldn't share to your Pebble", Toast.LENGTH_SHORT).show()
            finish()
        }
        // Note: we do NOT call finish() here unconditionally — the chooser
        // dialog path needs the activity alive to host the AlertDialog.
        // Each routing branch in handleShareIntent() finishes itself when
        // appropriate (immediately for direct dispatch; on dialog dismiss
        // for the chooser path).
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }

        // Extract payload first; we need it for every routing path.
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        if (text.isNullOrBlank()) {
            logger.w { "share intent missing EXTRA_TEXT" }
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val url = extractFirstUrl(text)

        // The watchapp uuid can arrive via either of two extras depending
        // on which path the share sheet took to launch us:
        //
        // - Direct Share path: system sets Intent.EXTRA_SHORTCUT_ID to the
        //   shortcut id ("share-watchapp-<uuid>"). Our custom
        //   EXTRA_WATCHAPP_UUID is NOT preserved on this path.
        //
        // - Launcher-shortcut tap path: system uses the shortcut's
        //   registered intent verbatim, which carries EXTRA_WATCHAPP_UUID.
        //
        // - Static "Pebble" entry: neither extra is set — falls through
        //   to the chooser/auto-pick logic below.
        val uuidString = intent.getStringExtra(EXTRA_WATCHAPP_UUID)
            ?: intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
                ?.removePrefix(SHORTCUT_ID_PREFIX)
                ?.takeIf { it.isNotBlank() }

        if (uuidString.isNullOrBlank()) {
            handleAmbiguousFallback(text = text, url = url, subject = subject)
            return
        }

        // We have a specific watchapp targeted — dispatch directly.
        val uuid = try {
            Uuid.parse(uuidString)
        } catch (e: IllegalArgumentException) {
            logger.w(e) { "bad uuid in share intent: $uuidString" }
            finish()
            return
        }
        Toast.makeText(this, "Sharing to your Pebble…", Toast.LENGTH_SHORT).show()
        dispatcher.enqueue(uuid, text = text, url = url, subject = subject)
        finish()
    }

    /**
     * Static "Pebble" entry path: no specific watchapp identified. Decide
     * what to do based on how many share-capable watchapps are installed.
     */
    private fun handleAmbiguousFallback(text: String, url: String?, subject: String?) {
        val targets = dispatcher.availableTargets.value
        when {
            targets.isEmpty() -> {
                Toast.makeText(
                    this,
                    "No watchapp is set up to receive shares. Install a watchapp that supports sharing.",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
            targets.size == 1 -> {
                // Single share-capable watchapp — no chooser needed.
                val target = targets.first()
                logger.i { "ambiguous fallback resolved to sole target ${target.uuid}" }
                Toast.makeText(this, "Sharing to your Pebble…", Toast.LENGTH_SHORT).show()
                dispatcher.enqueue(target.uuid, text = text, url = url, subject = subject)
                finish()
            }
            else -> {
                showChooserDialog(targets, text = text, url = url, subject = subject)
            }
        }
    }

    /**
     * Multiple share-capable watchapps installed — let the user pick one.
     * The dialog hosts itself on this activity; on dismiss/cancel the
     * activity finishes without dispatching.
     */
    private fun showChooserDialog(
        targets: List<ShareTargetEntry>,
        text: String,
        url: String?,
        subject: String?,
    ) {
        // Sort alphabetically by display name for a stable, predictable order.
        val sorted = targets.sortedBy { displayNameFor(it) }
        val labels = sorted.map { displayNameFor(it) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Share to which watchapp?")
            .setItems(labels) { _: DialogInterface, which: Int ->
                val picked = sorted[which]
                logger.i { "chooser picked ${picked.uuid}" }
                Toast.makeText(this, "Sharing to your Pebble…", Toast.LENGTH_SHORT).show()
                dispatcher.enqueue(picked.uuid, text = text, url = url, subject = subject)
                finish()
            }
            .setOnCancelListener { finish() }
            .setOnDismissListener { if (!isFinishing) finish() }
            .show()
    }

    private fun displayNameFor(entry: ShareTargetEntry): String =
        entry.shareTarget.label?.takeIf { it.isNotBlank() }
            ?: entry.longName.takeIf { it.isNotBlank() }
            ?: entry.shortName

    private fun extractFirstUrl(text: String): String? =
        Patterns.WEB_URL.matcher(text).takeIf { it.find() }?.group()

    companion object {
        private val logger = Logger.withTag(ShareTargetActivity::class.simpleName!!)
    }
}
