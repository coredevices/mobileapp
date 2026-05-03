package io.rebble.libpebblecommon.notification

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.util.Base64
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

/**
 * Extracts structured media metadata from a [MediaStyle][Notification.MediaStyle]
 * notification by following the embedded [MediaSession.Token] and
 * querying the corresponding [MediaController].
 *
 * Why bother:
 *   `Notification.title` / `text` give you the song name and artist as
 *   loose strings, but watchapps (music control surfaces, album-art
 *   displays, scrubber UIs) typically want structured fields:
 *   `title` / `artist` / `album` separated, duration as a number,
 *   playback position, an explicit play/pause/stopped state, and album
 *   art at usable resolution. All of that lives on the MediaSession,
 *   not in the notification's surface text.
 *
 * What we surface (JSON object, all fields nullable):
 *   `title`         METADATA_KEY_TITLE
 *   `artist`        METADATA_KEY_ARTIST
 *   `album`         METADATA_KEY_ALBUM
 *   `durationMs`    METADATA_KEY_DURATION (Long, milliseconds)
 *   `positionMs`    PlaybackState.position (Long, milliseconds)
 *   `playbackState` String form of PlaybackState.state — "playing",
 *                   "paused", "stopped", "buffering", "fast_forwarding",
 *                   "rewinding", "skipping_to_next", "skipping_to_previous",
 *                   "skipping_to_queue_item", "error", "connecting",
 *                   "none". Returned as null when no PlaybackState is
 *                   available.
 *   `albumArtBase64` Base64-encoded PNG of the album art bitmap from
 *                   METADATA_KEY_ART (preferred), falling back to
 *                   METADATA_KEY_ALBUM_ART then METADATA_KEY_DISPLAY_ICON.
 *                   Downsampled to fit a configured byte cap; null if
 *                   absent or oversize.
 *
 * Cost & cap:
 *   Album art bitmaps are typically 300×300 to 1080×1080. Downsampled
 *   to fit [DEFAULT_BYTE_CAP] (8 KB) of base64 PNG; falls through size
 *   tiers same as [BigPictureExtractor].
 *
 * Failure mode:
 *   Best-effort. Any failure (no media session token in extras, can't
 *   open MediaController, no metadata, etc.) returns null. Never throws.
 */
internal object MediaSessionExtractor {

    private val logger = Logger.withTag("MediaSessionExtractor")

    /**
     * Same 8 KB upper bound used by [BigPictureExtractor]. Album art
     * compresses about as well as a typical BigPicture so the cap and
     * downsample ladder match.
     */
    const val DEFAULT_BYTE_CAP = 8192

    private const val ART_TARGET_PX_LARGE = 144
    private const val ART_TARGET_PX_MEDIUM = 96
    private const val ART_TARGET_PX_SMALL = 64

    /**
     * Extract media metadata as a JSON object. Returns null if the
     * notification has no MediaSession token or extraction fails.
     *
     * @param context Used to construct the [MediaController]. The
     *   notification listener service IS a Context with the right
     *   permission to read media sessions referenced by notifications.
     * @param notification The notification being extracted from.
     * @param albumArtByteCap Cap for the encoded album-art base64
     *   length. Defaults to [DEFAULT_BYTE_CAP].
     */
    fun extract(
        context: Context,
        notification: Notification,
        albumArtByteCap: Int = DEFAULT_BYTE_CAP,
    ): JsonObject? {
        val token: MediaSession.Token = extractMediaSessionToken(notification) ?: return null

        val controller: MediaController = try {
            MediaController(context, token)
        } catch (e: Exception) {
            // MediaController construction can fail if the originating
            // session has been released between notification posting
            // and our extraction. Soft-fail.
            logger.v(e) { "MediaController construction failed" }
            return null
        }

        val metadata: MediaMetadata? = try { controller.metadata } catch (e: Exception) {
            logger.v(e) { "controller.metadata read failed" }
            null
        }
        val playback: PlaybackState? = try { controller.playbackState } catch (e: Exception) {
            logger.v(e) { "controller.playbackState read failed" }
            null
        }

        if (metadata == null && playback == null) return null

        return buildJsonObject {
            put("title", metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
            put("artist", metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST))
            put("album", metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM))

            val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            // METADATA_KEY_DURATION returns 0 when not set (per docs).
            // Surface as null in that case to keep the JSON shape
            // consistent ("present and known" vs "absent").
            if (durationMs > 0) put("durationMs", durationMs)
            else put("durationMs", JsonNull)

            // PlaybackState.position is the most-recently-reported play
            // head. It's an instantaneous snapshot — for live ETA the
            // watchapp should combine it with PlaybackState.lastPositionUpdateTime
            // and the playback rate, but for a typical "show track
            // position" UI the bare number is what watchapps use.
            val pos = playback?.position
            put("positionMs", if (pos != null && pos >= 0) JsonPrimitive(pos) else JsonNull)

            put("playbackState", playback?.state?.let(::playbackStateToString))

            put("albumArtBase64", extractAlbumArtBase64(metadata, albumArtByteCap))
        }
    }

    /**
     * Pull the MediaSession token from the notification's extras.
     * `Notification.EXTRA_MEDIA_SESSION` ("android.mediaSession") is the
     * key MediaStyle uses; same on all API levels we support.
     */
    @Suppress("DEPRECATION")
    private fun extractMediaSessionToken(notification: Notification): MediaSession.Token? {
        val extras: Bundle = notification.extras ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
            }
        } catch (e: Exception) {
            logger.v(e) { "EXTRA_MEDIA_SESSION read failed" }
            null
        }
    }

    /**
     * Pull album art Bitmap from the metadata, preferring the standard
     * keys in order, downsample to fit the byte cap, return base64 PNG
     * or null.
     *
     * Key preference: `METADATA_KEY_ART` is the highest-fidelity slot
     * (typically the original full-resolution album art). Falls back
     * to `METADATA_KEY_ALBUM_ART` (older key, often the same image)
     * and then `METADATA_KEY_DISPLAY_ICON` (small thumbnail intended
     * for compact UIs).
     */
    private fun extractAlbumArtBase64(metadata: MediaMetadata?, byteCap: Int): String? {
        if (metadata == null) return null
        val source: Bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        return encodeAlbumArtWithFallback(source, byteCap)
    }

    private fun encodeAlbumArtWithFallback(source: Bitmap, byteCap: Int): String? {
        val targets = intArrayOf(ART_TARGET_PX_LARGE, ART_TARGET_PX_MEDIUM, ART_TARGET_PX_SMALL)
        for (target in targets) {
            val encoded = downsampleAndEncode(source, target) ?: continue
            if (encoded.length <= byteCap) return encoded
        }
        logger.v {
            "all album-art downsample targets exceeded byte cap (cap=$byteCap, " +
                    "source=${source.width}x${source.height})"
        }
        return null
    }

    private fun downsampleAndEncode(source: Bitmap, target: Int): String? {
        return try {
            val sw = source.width
            val sh = source.height
            if (sw <= 0 || sh <= 0) return null
            val scale = minOf(target.toFloat() / sw, target.toFloat() / sh, 1.0f)
            val dw = maxOf(1, (sw * scale).toInt())
            val dh = maxOf(1, (sh * scale).toInt())
            val dest = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dest)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, Rect(0, 0, sw, sh), Rect(0, 0, dw, dh), paint)
            val baos = ByteArrayOutputStream()
            if (!dest.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                dest.recycle()
                return null
            }
            dest.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            logger.v(e) { "album-art downsample to $target failed" }
            null
        }
    }

    /**
     * PlaybackState.STATE_* constants → string form for JSON. Picked
     * lowercase-snake-case so JS consumers can do simple equality checks
     * (`if (state === 'playing')`) without dealing with magic numbers.
     */
    private fun playbackStateToString(state: Int): String = when (state) {
        PlaybackState.STATE_NONE -> "none"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_FAST_FORWARDING -> "fast_forwarding"
        PlaybackState.STATE_REWINDING -> "rewinding"
        PlaybackState.STATE_BUFFERING -> "buffering"
        PlaybackState.STATE_ERROR -> "error"
        PlaybackState.STATE_CONNECTING -> "connecting"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "skipping_to_previous"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "skipping_to_next"
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "skipping_to_queue_item"
        else -> "unknown"
    }
}
