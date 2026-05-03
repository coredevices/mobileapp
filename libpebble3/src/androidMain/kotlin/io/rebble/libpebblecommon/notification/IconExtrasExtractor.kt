package io.rebble.libpebblecommon.notification

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Base64
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream

/**
 * Extracts every [Icon][android.graphics.drawable.Icon]-typed entry from
 * a [Notification]'s extras [Bundle], rasterizes each to a 32×32 ARGB PNG,
 * base64-encodes it, and emits a JSON object map keyed by the extras key.
 *
 * Why bother:
 *   Android 14+ introduced the Ongoing Activity API, which lets apps
 *   render rich live notifications with custom glyphs. Google Maps' nav
 *   notification is the canonical example — the turn-direction arrow,
 *   lane guidance, arrival flag, and ETA chip icon are all stored as
 *   Icon parcelables in the notification's extras bundle under keys
 *   like `android.ongoingActivityNoti.chipIcon`,
 *   `android.ongoingActivityNoti.nowbarIcon`,
 *   `android.ongoingActivityNoti.secondIcon`.
 *
 *   These don't appear in the standard `Notification.smallIcon` /
 *   `largeIcon` slots (which carry the app's brand glyph), and they
 *   don't appear in `Notification.contentView` / `bigContentView`
 *   RemoteViews (which Ongoing Activity notifications often don't
 *   populate at all). The extras bundle is where they live, full stop.
 *
 *   Watchapps that mirror nav, fitness, media, or any other live-
 *   activity-style notifications need access to these. This extractor
 *   provides it as a stable Android API surface (`Bundle.get(key)`
 *   followed by `Icon.loadDrawable(context)`) — no reflection, no
 *   layout-tree walking, no version-fragile RemoteViews introspection.
 *
 * Output JSON shape (object keyed by extras key):
 *   ```
 *   {
 *     "android.ongoingActivityNoti.chipIcon": {
 *       "base64":     "<base64 PNG>",       // 32×32 ARGB, NO_WRAP
 *       "hash":       "<hex>",              // djb2 over the rasterized pixels
 *       "intrinsicW": <int>,                // Drawable.intrinsicWidth (-1 if unknown)
 *       "intrinsicH": <int>                 // Drawable.intrinsicHeight
 *     },
 *     "android.ongoingActivityNoti.nowbarIcon": { ... },
 *     ...
 *   }
 *   ```
 *
 *   Hash is djb2 over the rasterized 32×32 pixel ints — useful for
 *   cheap state-change detection across notifications (e.g. distinguishing
 *   one Maps turn-arrow glyph from another) without diffing the full
 *   base64 payload.
 *
 * Cost:
 *   One Drawable load + 32×32 raster + PNG encode per Icon entry.
 *   Maps' nav notification typically has 2-3 icon extras, totalling
 *   ~1.5 KB of base64 payload per notification. Trivial vs the rest
 *   of the dispatch pipeline.
 *
 * Failure mode:
 *   Best-effort. Per-icon failures (load fail, rasterize fail, encode
 *   fail) are logged at trace and that key is skipped in the output;
 *   other Icon entries continue to be processed. Never throws.
 *
 * API gating:
 *   The [Icon] class is API 23+. Pre-API-23 returns an empty object
 *   (no Icon-typed extras can exist in the bundle below that level).
 */
internal object IconExtrasExtractor {

    private val logger = Logger.withTag("IconExtrasExtractor")

    /** Match the icon size used by [NotificationIconExtractor] for comparability. */
    private const val ICON_SIZE_PX = 32

    /**
     * Walk [notification]'s extras, rasterize every Icon-typed entry,
     * return a JsonObject mapping extras-key → per-icon details.
     *
     * @param context Used to call `Icon.loadDrawable(context)`. The
     *   notification listener service IS a Context with the right
     *   permissions to resolve drawables from the source app.
     */
    fun extract(context: Context, notification: Notification): JsonObject {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return buildJsonObject { /* Icon class doesn't exist pre-API-23 */ }
        }
        val extras: Bundle = notification.extras ?: return buildJsonObject { }
        return buildJsonObject {
            for (key in extras.keySet()) {
                val value: Any? = try {
                    @Suppress("DEPRECATION")
                    extras.get(key)
                } catch (e: Exception) {
                    logger.v(e) { "extras.get('$key') threw, skipping" }
                    null
                }
                if (value !is Icon) continue
                emitIconEntry(context, key, value)
            }
        }
    }

    private fun JsonObjectBuilder.emitIconEntry(context: Context, key: String, icon: Icon) {
        val drawable: Drawable = try {
            icon.loadDrawable(context)
        } catch (e: Throwable) {
            logger.v(e) { "Icon.loadDrawable failed for '$key'" }
            return
        } ?: return

        val intrinsicW = drawable.intrinsicWidth
        val intrinsicH = drawable.intrinsicHeight

        val raster: Bitmap = try {
            // Fast path: drawable is already a bitmap of the target size.
            if (drawable is BitmapDrawable && drawable.bitmap != null &&
                drawable.bitmap.width == ICON_SIZE_PX &&
                drawable.bitmap.height == ICON_SIZE_PX
            ) {
                drawable.bitmap
            } else {
                val bmp = Bitmap.createBitmap(
                    ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
                drawable.draw(canvas)
                bmp
            }
        } catch (e: Throwable) {
            logger.v(e) { "rasterize failed for '$key'" }
            return
        }

        val hash = djb2OfPixels(raster) ?: return
        val base64 = pngBase64(raster)

        // Recycle the bitmap unless it was the BitmapDrawable's own
        // (recycling that would corrupt the source drawable).
        if (raster !== (drawable as? BitmapDrawable)?.bitmap) raster.recycle()

        if (base64 == null) return

        putJsonObject(key) {
            put("base64", base64)
            put("hash", hash)
            put("intrinsicW", intrinsicW)
            put("intrinsicH", intrinsicH)
        }
    }

    private fun djb2OfPixels(bmp: Bitmap): String? {
        return try {
            val pixels = IntArray(ICON_SIZE_PX * ICON_SIZE_PX)
            bmp.getPixels(pixels, 0, ICON_SIZE_PX, 0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
            var h = 5381L
            for (px in pixels) {
                h = ((h shl 5) + h + px.toLong()) and 0xFFFFFFFFL
            }
            h.toString(16)
        } catch (e: Throwable) {
            logger.v(e) { "pixel hash failed" }
            null
        }
    }

    private fun pngBase64(bmp: Bitmap): String? {
        return try {
            val baos = ByteArrayOutputStream()
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)) return null
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Throwable) {
            logger.v(e) { "png encode failed" }
            null
        }
    }
}
