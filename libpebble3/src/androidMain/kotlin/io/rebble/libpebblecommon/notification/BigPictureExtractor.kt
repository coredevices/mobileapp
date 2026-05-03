package io.rebble.libpebblecommon.notification

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Base64
import co.touchlab.kermit.Logger
import java.io.ByteArrayOutputStream

/**
 * Extracts the [Notification.BigPictureStyle] photograph from a
 * notification, downsamples it to fit a configured byte cap, and
 * encodes it as base64 PNG suitable for inclusion in the JSON payload
 * PKJS watchapps consume.
 *
 * Why bother:
 *   `BigPictureStyle` is where photo-app notifications (Photos, Gallery,
 *   Instagram-style apps) and a notable subset of music/podcast players
 *   put album art at higher resolution than fits in `Notification.largeIcon`.
 *   Watchapps that mirror media playback or display visual notifications
 *   need this content; without it they'd be limited to the 32×32 largeIcon
 *   (sometimes a heavily compressed thumbnail).
 *
 * Cost & cap:
 *   Source bitmaps can be huge (1080×1080+). PNG-encoding those raw would
 *   produce 100KB+ blobs, which is a non-starter for Bluetooth delivery
 *   to a Pebble (typical sustained throughput ~3-4 KB/s). The extractor
 *   downsamples adaptively: tries [TARGET_PX_LARGE] first, then
 *   [TARGET_PX_MEDIUM], then [TARGET_PX_SMALL]. The first encoding that
 *   fits under [DEFAULT_BYTE_CAP] wins. If even the smallest size
 *   exceeds the cap (very rare — only on photographic content with high
 *   color complexity at small sizes), returns null.
 *
 *   Watchapps that need finer control can decode the base64 themselves
 *   and re-process; the source pixels are already at watchapp-renderable
 *   resolution after our downsample.
 *
 * Failure mode:
 *   Best-effort. Any failure (missing extras key, unsupported icon type,
 *   bitmap creation, PNG encode) is logged at trace and returns null.
 *   Never throws.
 */
internal object BigPictureExtractor {

    private val logger = Logger.withTag("BigPictureExtractor")

    /**
     * 8 KB upper bound on the encoded base64 PNG. Chosen to match the
     * cap used elsewhere in the notification payload pipeline; rough
     * upper limit on what's reasonable to ship over Bluetooth to a
     * watch on a per-notification basis.
     */
    const val DEFAULT_BYTE_CAP = 8192

    /**
     * Downsample target ladder. Tried in order: large first (best
     * fidelity), falling back to smaller sizes when the encoded result
     * exceeds the byte cap. 144 chosen to match Pebble Time Steel /
     * Aplite / Basalt screen width — watchapps render at this size
     * directly without further downscaling.
     */
    private const val TARGET_PX_LARGE = 144
    private const val TARGET_PX_MEDIUM = 96
    private const val TARGET_PX_SMALL = 64

    /**
     * Extract the BigPicture from a notification and encode it.
     *
     * @param context Used to resolve [Icon] references on API 31+ where
     *   BigPictureStyle's picture is sometimes stored as an Icon rather
     *   than a Bitmap. The notification listener service IS a Context.
     * @param notification The notification to extract from.
     * @param byteCap Maximum encoded base64 length; default
     *   [DEFAULT_BYTE_CAP]. Encodings larger than this trigger fallback
     *   to a smaller target size; if even the smallest exceeds the cap
     *   the extractor returns null.
     * @return Base64-encoded PNG, or null if the notification has no
     *   BigPicture, the picture couldn't be loaded, or no downsample
     *   target produced a result under [byteCap].
     */
    fun extract(
        context: Context,
        notification: Notification,
        byteCap: Int = DEFAULT_BYTE_CAP,
    ): String? {
        val source: Bitmap = loadBigPictureBitmap(context, notification) ?: return null
        return encodeWithFallback(source, byteCap)
    }

    /**
     * Pull the BigPicture out of the notification. Tries the API 31+
     * Icon-typed field first (`EXTRA_PICTURE_ICON`), then falls back to
     * the legacy Bitmap-typed field (`EXTRA_PICTURE`). Either may be
     * present on any given notification depending on which Notification
     * builder API the source app used.
     */
    @Suppress("DEPRECATION")
    private fun loadBigPictureBitmap(context: Context, notification: Notification): Bitmap? {        val extras: Bundle = notification.extras ?: return null
        // API 31+: BigPictureStyle.bigPicture(Icon) writes EXTRA_PICTURE_ICON.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
                } else {
                    extras.getParcelable<Icon>(Notification.EXTRA_PICTURE_ICON)
                }
                if (icon != null) {
                    val drawable = icon.loadDrawable(context)
                    if (drawable is BitmapDrawable && drawable.bitmap != null) {
                        return drawable.bitmap
                    }
                    // Non-bitmap drawables (vector etc.) — rasterize at
                    // intrinsic size.
                    val w = drawable?.intrinsicWidth ?: 0
                    val h = drawable?.intrinsicHeight ?: 0
                    if (drawable != null && w > 0 && h > 0) {
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, w, h)
                        drawable.draw(canvas)
                        return bmp
                    }
                }
            } catch (e: Exception) {
                logger.v(e) { "EXTRA_PICTURE_ICON load failed; falling back to EXTRA_PICTURE" }
            }
        }
        // Legacy: BigPictureStyle.bigPicture(Bitmap) writes
        // EXTRA_PICTURE directly. Available on all API levels we
        // support.
        return try {
            extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
        } catch (e: Exception) {
            logger.v(e) { "EXTRA_PICTURE load failed" }
            null
        }
    }

    /**
     * Encode [source] to base64 PNG, trying each target size in order
     * until one fits under [byteCap]. If none fit, return null.
     */
    private fun encodeWithFallback(source: Bitmap, byteCap: Int): String? {
        val targets = intArrayOf(TARGET_PX_LARGE, TARGET_PX_MEDIUM, TARGET_PX_SMALL)
        for (target in targets) {
            val encoded = downsampleAndEncode(source, target) ?: continue
            if (encoded.length <= byteCap) return encoded
        }
        logger.v {
            "all downsample targets exceeded byte cap (cap=$byteCap, " +
                    "source=${source.width}x${source.height})"
        }
        return null
    }

    /**
     * Downsample [source] to fit within a [target]×[target] box
     * (preserving aspect ratio) and encode as base64 PNG.
     */
    private fun downsampleAndEncode(source: Bitmap, target: Int): String? {
        return try {
            // Compute aspect-preserving destination size that fits in
            // target × target. We prefer a slightly-smaller-than-target
            // result to a stretched result — the watchapp can scale up
            // if it wants.
            val sw = source.width
            val sh = source.height
            if (sw <= 0 || sh <= 0) return null
            val scale = minOf(target.toFloat() / sw, target.toFloat() / sh, 1.0f)
            val dw = maxOf(1, (sw * scale).toInt())
            val dh = maxOf(1, (sh * scale).toInt())

            val dest = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dest)
            // Bilinear filtering — better than nearest for photographs;
            // PNG encode size is similar either way (random pixel-level
            // detail dominates the entropy).
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, Rect(0, 0, sw, sh), Rect(0, 0, dw, dh), paint)
            val encoded = bitmapToPngBase64(dest)
            dest.recycle()
            encoded
        } catch (e: Exception) {
            logger.v(e) { "downsample to $target failed" }
            null
        }
    }

    private fun bitmapToPngBase64(bmp: Bitmap): String? {
        return try {
            val baos = ByteArrayOutputStream()
            // 100 = lossless for PNG (the value is just ignored for PNG
            // but the docs say to pass 100 for "best quality").
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                logger.v { "PNG compress returned false" }
                return null
            }
            // NO_WRAP avoids the line breaks the default Base64 encoder
            // inserts at column 76, which JS doesn't need.
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            logger.v(e) { "PNG encode failed" }
            null
        }
    }
}
