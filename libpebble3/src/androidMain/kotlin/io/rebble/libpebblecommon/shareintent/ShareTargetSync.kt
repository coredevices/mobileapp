package io.rebble.libpebblecommon.shareintent

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/**
 * Keeps Android Sharing Shortcuts in sync with the set of installed watchapps
 * that have declared `shareTarget` in their `package.json`. Each declared
 * watchapp surfaces in the system share sheet as its own entry with its own
 * label and icon; tapping it routes the share into [activityClass] with
 * [EXTRA_WATCHAPP_UUID] populated.
 *
 * Per-watchapp icons come from the PBW's menu icon (extracted by
 * [ShareTargetsProducer] into [ShareTargetEntry.iconBytes]), upscaled and
 * composited onto a colored background for share-sheet rendering. Watchapps
 * with no extractable menu icon fall back to [fallbackIconResId] (the host
 * app's launcher icon).
 *
 * Caller (typically the host app's startup code) must:
 *   1. Provide the [ComponentName] of the activity that handles ACTION_SEND
 *      from these shortcuts. The activity must be declared in the host
 *      application's manifest with an `<intent-filter>` matching
 *      `android.intent.action.SEND` for the appropriate MIME types.
 *   2. Call [start] once during application initialization.
 *
 * Uses [androidx.core.content.pm.ShortcutManagerCompat] which is available
 * back to API 25 — well below the host app's minSdk 26.
 */
class ShareTargetSync(
    private val activityClass: ComponentName,
    /**
     * Resource id of the fallback icon for share-target shortcuts. Used
     * when a watchapp has no extractable menu icon in its PBW. The host
     * app should provide a sensible default (e.g. its launcher icon).
     */
    private val fallbackIconResId: Int,
) : LibPebbleKoinComponent {
    private val appContext: AppContext by inject()
    private val scope: LibPebbleCoroutineScope by inject()
    private val producer: ShareTargetsProducer by inject()
    private val context = appContext.context
    private val logger = Logger.withTag(ShareTargetSync::class.simpleName!!)
    private val targets get() = producer.flow

    companion object {
        const val EXTRA_WATCHAPP_UUID = "io.rebble.libpebblecommon.shareintent.WATCHAPP_UUID"
        const val SHORTCUT_CATEGORY = "io.rebble.libpebblecommon.WATCHAPP_SHARE"
        const val SHORTCUT_ID_PREFIX = "share-watchapp-"

        /**
         * Target dimensions for the rasterized shortcut icon. Android share
         * sheets render at ~48dp at typical density, scaled up to ~96px on
         * the display. We choose 192 to look acceptable on high-density
         * screens and to leave room for the launcher's adaptive-icon shape
         * masking.
         */
        private const val SHORTCUT_ICON_PX = 192

        /**
         * Background color for the rendered shortcut icon — Repebble's
         * brand crimson. Pebble menu icons are designed for the watch's
         * display: typically black-on-transparent (1-bit) with the watch
         * firmware drawing them as off-white silhouettes against the
         * menu's colored background. We replicate that aesthetic: tint
         * non-transparent pixels to a warm off-white and draw onto this
         * crimson background. Result is consistent with how the icons
         * look on the watch and gives all share-target shortcuts a unified
         * Pebble-branded appearance in the share sheet.
         */
        private const val SHORTCUT_ICON_BG_COLOR: Int = 0xFFA41D1A.toInt()

        /**
         * Foreground color for the rendered icon — Repebble's warm cream
         * white. Slightly off pure white to feel softer against the red
         * background and to match the brand's typography color.
         */
        private const val SHORTCUT_ICON_FG_R: Float = 245f  // 0xF5
        private const val SHORTCUT_ICON_FG_G: Float = 240f  // 0xF0
        private const val SHORTCUT_ICON_FG_B: Float = 232f  // 0xE8
    }

    /**
     * Lazily-resolved bitmap form of the fallback icon resource. Direct-share
     * shortcuts on Samsung OneUI only surface in the share sheet when their
     * icon is delivered as a bitmap (showing up as
     * `bitmapPath=/data/.../X.png` in `cmd shortcut get-shortcuts`), not as
     * a resource reference.
     */
    private val fallbackIconBitmap: Bitmap? by lazy {
        try {
            val drawable = ResourcesCompat.getDrawable(context.resources, fallbackIconResId, null)
                ?: return@lazy null
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return@lazy drawable.bitmap
            }
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: SHORTCUT_ICON_PX
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: SHORTCUT_ICON_PX
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            logger.w(e) { "couldn't rasterize fallback icon resource $fallbackIconResId" }
            null
        }
    }

    /**
     * Per-watchapp cache of rasterized icon bitmaps keyed by uuid. The cache
     * key includes [ByteArray.contentHashCode] of the source bytes so an
     * upgraded watchapp (different bytes for the same uuid) gets a fresh
     * render. Without this, every locker re-emission would re-decode and
     * re-draw the bitmap on the IO thread — wasteful on the share-sheet
     * critical path.
     */
    private val iconCache = HashMap<Uuid, CachedIcon>()
    private data class CachedIcon(val sourceHash: Int, val bitmap: Bitmap)

    /**
     * Returns an [IconCompat] suitable for both the shortcut and the
     * Person attached to it. Prefers the watchapp's own menu icon scaled
     * up onto a colored background; falls back to the host app's launcher
     * icon when the watchapp has no extractable menu icon or rasterization
     * fails.
     *
     * Callers are expected to invoke this at shortcut-publish time only
     * (not on every share dispatch); the rasterization, while cached, is
     * still expensive enough to want to amortize.
     */
    private fun iconFor(entry: ShareTargetEntry): IconCompat {
        val rendered = renderWatchappIcon(entry)
        if (rendered != null) return IconCompat.createWithBitmap(rendered)
        // Watchapp has no menu icon, decoding failed, or it's an unsupported
        // format — fall back to the host app's launcher icon as a bitmap so
        // we still get direct-share surfacing on Samsung (which requires
        // bitmap-backed icons; resource references aren't surfaced).
        fallbackIconBitmap?.let { return IconCompat.createWithBitmap(it) }
        // Last-resort: resource reference. Some launchers may still surface
        // it; better than no icon at all.
        return IconCompat.createWithResource(context, fallbackIconResId)
    }

    private fun renderWatchappIcon(entry: ShareTargetEntry): Bitmap? {
        val sourceBytes = entry.iconBytes ?: return null
        val sourceHash = sourceBytes.contentHashCode()
        iconCache[entry.uuid]?.let { cached ->
            if (cached.sourceHash == sourceHash) return cached.bitmap
        }
        val source = try {
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
        } catch (e: Exception) {
            logger.w(e) { "couldn't decode menu icon bytes for ${entry.uuid}" }
            null
        } ?: return null
        val rendered = composeShortcutBitmap(source)
        iconCache[entry.uuid] = CachedIcon(sourceHash, rendered)
        return rendered
    }

    /**
     * Composes a watchapp's small menu icon onto a square colored
     * background sized for share-sheet rendering. Pebble menu icons are
     * authored as black-on-transparent PNGs with the assumption the watch
     * firmware will draw them as off-white silhouettes against the menu's
     * colored background; we replicate that here by tinting all opaque
     * pixels to the brand cream via a [ColorMatrix] that:
     *
     *  - Zeros out the RGB channels (so source color information is
     *    discarded — black, dark gray, and any color all map to the
     *    constant)
     *  - Adds the brand cream RGB constants
     *  - Preserves the alpha channel (so anti-aliased edges still feather
     *    cleanly into the red background)
     *
     * Net effect: every visible pixel becomes brand-cream-with-original-
     * alpha, drawn onto Pebble brand red. Matches the watch's own menu
     * rendering and the repebble.com visual identity.
     *
     * The source icon is scaled with nearest-neighbor sampling to preserve
     * its pixel-art character (Pebble menu icons are typically 25×25;
     * smooth interpolation makes them look like blurry blobs).
     *
     * The icon is centered with a small inset so the launcher's circular
     * mask doesn't crop the artwork.
     */
    private fun composeShortcutBitmap(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(SHORTCUT_ICON_PX, SHORTCUT_ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(SHORTCUT_ICON_BG_COLOR)

        // Fit the icon into ~70% of the canvas so the launcher's circular
        // shape mask doesn't clip it. Preserve aspect ratio.
        val targetEdge = (SHORTCUT_ICON_PX * 0.70f).toInt()
        val srcAspect = source.width.toFloat() / source.height.toFloat()
        val destW: Int
        val destH: Int
        if (srcAspect >= 1f) {
            destW = targetEdge
            destH = (targetEdge / srcAspect).toInt().coerceAtLeast(1)
        } else {
            destH = targetEdge
            destW = (targetEdge * srcAspect).toInt().coerceAtLeast(1)
        }
        val left = (SHORTCUT_ICON_PX - destW) / 2
        val top = (SHORTCUT_ICON_PX - destH) / 2
        val srcRect = Rect(0, 0, source.width, source.height)
        val destRect = Rect(left, top, left + destW, top + destH)

        // Color matrix that tints opaque pixels brand cream while preserving
        // alpha. Layout: 4×5 matrix, rows are [R, G, B, A] outputs, columns
        // are [R, G, B, A, constant] inputs. We zero out RGB inputs and set
        // the constant for R/G/B to brand cream channel values; alpha
        // passes through unchanged so anti-aliased edges feather cleanly.
        val tint = ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, SHORTCUT_ICON_FG_R,
            0f, 0f, 0f, 0f, SHORTCUT_ICON_FG_G,
            0f, 0f, 0f, 0f, SHORTCUT_ICON_FG_B,
            0f, 0f, 0f, 1f, 0f,
        ))
        val paint = Paint().apply {
            // No filtering = nearest-neighbor — preserves pixel-art look at scale.
            isFilterBitmap = false
            colorFilter = ColorMatrixColorFilter(tint)
        }
        canvas.drawBitmap(source, srcRect, destRect, paint)
        return out
    }

    fun start() {
        targets
            .onEach { applyShortcuts(it) }
            .launchIn(scope)
    }

    private fun applyShortcuts(entries: List<ShareTargetEntry>) {
        val desiredById: Map<String, ShareTargetEntry> =
            entries.associateBy { "$SHORTCUT_ID_PREFIX${it.uuid}" }

        // Drop cache entries for watchapps that are no longer share targets.
        val keptUuids = entries.map { it.uuid }.toHashSet()
        iconCache.keys.retainAll(keptUuids)

        // Remove stale shortcuts we previously published. Other dynamic
        // shortcuts the host app may publish are untouched.
        val existing = ShortcutManagerCompat.getDynamicShortcuts(context)
        val staleIds = existing
            .map { it.id }
            .filter { it.startsWith(SHORTCUT_ID_PREFIX) && it !in desiredById }
        if (staleIds.isNotEmpty()) {
            logger.d { "removing stale share shortcuts: $staleIds" }
            ShortcutManagerCompat.removeDynamicShortcuts(context, staleIds)
        }

        for ((id, entry) in desiredById) {
            try {
                ShortcutManagerCompat.pushDynamicShortcut(context, buildShortcut(id, entry))
            } catch (e: Exception) {
                logger.w(e) { "failed to push shortcut for ${entry.uuid}" }
            }
        }
        logger.d { "synced ${desiredById.size} share-target shortcut(s)" }
    }

    private fun buildShortcut(id: String, entry: ShareTargetEntry): ShortcutInfoCompat {
        // Per Google's reference SharingShortcutsManager sample, the shortcut
        // intent's action should be ACTION_DEFAULT (the launcher long-press
        // path), NOT ACTION_SEND. The system constructs its own ACTION_SEND
        // for the direct-share path, with EXTRA_SHORTCUT_ID identifying the
        // chosen target.
        val intent = Intent(Intent.ACTION_DEFAULT).apply {
            component = activityClass
            putExtra(EXTRA_WATCHAPP_UUID, entry.uuid.toString())
        }

        val label = entry.shareTarget.label?.takeIf { it.isNotBlank() } ?: entry.shortName
        val icon = iconFor(entry)

        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(if (entry.longName.isNotBlank()) "Share to ${entry.longName}" else "Share to $label")
            .setIcon(icon)
            // setLongLived flags the shortcut as cacheable by system services
            // even after it's unpublished — required for direct-share
            // surfacing on Samsung OneUI and elsewhere.
            .setLongLived(true)
            .setRank(0)
            .setCategories(setOf(SHORTCUT_CATEGORY))
            .setIntent(intent)
            .build()
    }
}
