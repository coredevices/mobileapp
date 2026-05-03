package io.rebble.libpebblecommon.notification

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import co.touchlab.kermit.Logger
import java.io.ByteArrayOutputStream

/**
 * Extracts notification icons (small icon + large icon) from an Android
 * [Notification] and encodes them as base64 PNG strings suitable for
 * inclusion in the JSON payload PKJS watchapps consume.
 *
 * Why bother:
 *   Notification icons are typically the most semantically rich visual
 *   signal in a notification. Google Maps' navigation notification, for
 *   example, draws a turn-arrow icon in its smallIcon slot — left turn,
 *   right turn, straight, U-turn, exit ramp, roundabout, etc. — that's
 *   the only structured signal of the maneuver type. The text fields
 *   contain free-form prose that's locale-dependent and varies by trip
 *   ("toward Saticoy St", "Make a U-turn", etc).
 *
 *   Watchapps doing nav, music control, fitness mirroring, etc. routinely
 *   want to render the source notification's iconography on the watch.
 *   Rather than every watchapp inventing its own way of getting at icon
 *   bitmaps (which it can't from JS anyway), the platform extracts +
 *   encodes them once and includes them in the JSON event payload.
 *
 * Cost:
 *   A 32×32 PNG of a typical monochrome icon is ~200-600 bytes raw, ~280-820
 *   bytes base64-encoded. Trivial overhead vs the rest of the notification
 *   payload. CPU cost is one rasterize + one PNG-encode per dispatched
 *   notification — negligible at the ~1Hz rates notifications fire at.
 *
 * Failure mode:
 *   Best-effort. Any failure in icon extraction (Drawable load, bitmap
 *   creation, PNG encode) is logged and skipped — the rest of the payload
 *   is unaffected. Watchapps treat the icon fields as optional.
 */
internal object NotificationIconExtractor {

    private val logger = Logger.withTag("NotificationIconExtractor")

    /**
     * Target size for rasterized notification icons. Pebble watch faces are
     * 144x168 (Aplite/Basalt) up to 200x228 (Emery), and the typical use
     * site is a top-bar slot of 24-32px square. 32px gives watchapps headroom
     * to downscale themselves; smaller would require server-side knowledge of
     * the target watch's display.
     *
     * Watchapps that need the icon at a different size do so themselves —
     * they receive the base64 PNG and decode/scale on the JS side or ship
     * it through to the C side as-is.
     */
    private const val ICON_SIZE_PX = 32

    /**
     * Holds extracted icon data. All fields nullable because any extraction
     * step can legitimately fail or produce nothing.
     */
    data class Icons(
        /** Base64-encoded PNG of the notification's smallIcon, or null. */
        val smallIconBase64: String?,
        /** Base64-encoded PNG of the notification's largeIcon, or null. */
        val largeIconBase64: String?,
    ) {
        companion object {
            val EMPTY = Icons(null, null)
        }
    }

    /**
     * Extract the requested icons. Either or both may be null in the
     * result — extraction is best-effort and returns null on any failure.
     *
     * @param context Used to resolve Icon drawables (icons reference
     *   resources in the source app's package; loadDrawable needs a Context
     *   to dereference). The notification listener service IS a Context;
     *   pass `this` from the listener.
     * @param notification The notification to extract icons from.
     * @param wantSmall Whether to attempt smallIcon extraction. When
     *   false, the corresponding field in the returned [Icons] is null
     *   without any extraction work performed. Defaults to true for
     *   backward compatibility with callers that don't gate.
     * @param wantLarge Same as [wantSmall], for largeIcon.
     */
    fun extract(
        context: Context,
        notification: Notification,
        wantSmall: Boolean = true,
        wantLarge: Boolean = true,
    ): Icons {
        val small = if (wantSmall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Notification.smallIcon is an Icon (added API 23). loadDrawable
            // crosses the package boundary into the source app's resources,
            // which is what we want — Maps' arrow-icon resource is in Maps'
            // own package.
            try {
                notification.smallIcon?.loadDrawable(context)?.let(::drawableToPngBase64)
            } catch (e: Exception) {
                logger.v(e) { "smallIcon extract failed" }
                null
            }
        } else null

        val large = if (wantLarge && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                notification.getLargeIcon()?.loadDrawable(context)?.let(::drawableToPngBase64)
            } catch (e: Exception) {
                logger.v(e) { "largeIcon extract failed" }
                null
            }
        } else null

        return Icons(small, large)
    }

    /**
     * Rasterize a [Drawable] to a [ICON_SIZE_PX]×[ICON_SIZE_PX] bitmap and
     * return base64-encoded PNG. Tints are NOT applied — watchapps decide
     * how to render the alpha channel against their own theme.
     *
     * Returns null if any step fails or yields an empty bitmap. We don't
     * preserve the original drawable's intrinsic size because watchapps
     * don't have a way to negotiate dimensions; a fixed target size is
     * predictable.
     */
    private fun drawableToPngBase64(drawable: Drawable): String? {
        return try {
            // Fast path: drawable is already a bitmap of the right size.
            if (drawable is BitmapDrawable && drawable.bitmap != null &&
                drawable.bitmap.width == ICON_SIZE_PX &&
                drawable.bitmap.height == ICON_SIZE_PX) {
                return bitmapToPngBase64(drawable.bitmap)
            }
            // Else rasterize.
            val bmp = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
            drawable.draw(canvas)
            val encoded = bitmapToPngBase64(bmp)
            bmp.recycle()
            encoded
        } catch (e: Exception) {
            logger.v(e) { "drawable rasterize failed" }
            null
        }
    }

    private fun bitmapToPngBase64(bmp: Bitmap): String? {
        return try {
            val baos = ByteArrayOutputStream()
            // 100 = lossless for PNG (the value is just ignored for PNG, but
            // the docs say to pass 100 for "best quality").
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                logger.v { "PNG compress returned false" }
                return null
            }
            val bytes = baos.toByteArray()
            // NO_WRAP avoids the line breaks the default Base64 encoder
            // inserts at column 76, which JS doesn't need.
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            logger.v(e) { "PNG encode failed" }
            null
        }
    }
}
