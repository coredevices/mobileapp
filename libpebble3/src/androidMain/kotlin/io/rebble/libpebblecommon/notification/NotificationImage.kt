package io.rebble.libpebblecommon.notification

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.util.PrivateLogger

private val logger = Logger.withTag("NotificationImage")

sealed interface NotificationImageSource {
    data class FromBitmap(val bitmap: Bitmap) : NotificationImageSource
    data class FromUri(val uri: Uri) : NotificationImageSource
}

/** An image attached to a notification, with the dimensions of the original. */
data class NotificationImage(
    val source: NotificationImageSource,
    val width: Int,
    val height: Int,
)

/**
 * [caption] is filled in even when [image] isn't, because the sender's own words still read better
 * than the app's "Image" placeholder when images are turned off.
 */
data class NotificationAttachment(
    val image: NotificationImage? = null,
    val caption: CharSequence? = null,
    /**
     * Identifies the message this notification is about, so re-posts of it can be told apart from
     * the next message in the conversation. Null when there is no message to identify.
     *
     * Taken from the message rather than anything about the image: an app may hand out a fresh
     * content URI for the same photo every time it re-posts.
     */
    val messageKey: String? = null,
)

/**
 * The photo attached to this notification and the text sent with it. The photo is a BigPictureStyle
 * picture, or the newest image in a MessagingStyle conversation's latest batch — deliberately not
 * `EXTRA_LARGE_ICON`, which for messaging apps is the sender's avatar rather than anything they
 * sent.
 *
 * Pass `includeImage = false` to skip the photo entirely: reading it opens the sender's content
 * URI, which there's no reason to do when it won't be sent.
 *
 * Only the image's dimensions are decoded — the bytes are read later, and only for notifications
 * that survive filtering.
 */
fun StatusBarNotification.extractAttachment(
    context: Context,
    includeImage: Boolean,
    privateLogger: PrivateLogger,
): NotificationAttachment {
    val batch = messageBatch(privateLogger)
    val image = if (!includeImage) {
        null
    } else {
        bigPicture(context)?.asNotificationImage()
            ?: batch.firstOrNull { it.isImage() }?.dataUri?.asNotificationImage(context)
    }
    // Only caption a photo the notification is actually about; when the newest message is text, the
    // notification's own text already says what it is.
    val caption = if (batch.firstOrNull()?.isImage() == true) {
        batch.firstOrNull { it.dataUri == null && !it.text.isNullOrBlank() }?.text
    } else {
        null
    }
    val newest = batch.firstOrNull()
    return NotificationAttachment(
        image = image,
        caption = caption,
        messageKey = newest?.let { "${it.timestamp}|${it.text}" },
    )
}

private fun StatusBarNotification.bigPicture(context: Context): Bitmap? {
    val extras = notification.extras
    @Suppress("DEPRECATION")
    val bitmap = extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap
    if (bitmap != null) return bitmap
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    @Suppress("DEPRECATION")
    val icon = extras.getParcelable(Notification.EXTRA_PICTURE_ICON) as? Icon ?: return null
    return (icon.loadDrawable(context) as? BitmapDrawable)?.bitmap
}

private fun StatusBarNotification.messageBatch(privateLogger: PrivateLogger): List<NotificationCompat.MessagingStyle.Message> {
    val messages = NotificationCompat.MessagingStyle
        .extractMessagingStyleFromNotification(notification)
        ?.messages
    val newest = messages?.lastOrNull()
    if (newest == null) {
        logger.v { "${privateLogger.obfuscate(packageName)}: not a MessagingStyle notification" }
        return emptyList()
    }
    val batch = messages.asReversed().takeWhile {
        it.person?.key == newest.person?.key &&
                newest.timestamp - it.timestamp <= ATTACHMENT_BATCH_WINDOW_MS
    }
    return batch
}

private fun NotificationCompat.MessagingStyle.Message.isImage() =
    dataMimeType?.startsWith("image/") == true

// How far before the newest message an attachment can still belong to it.
private const val ATTACHMENT_BATCH_WINDOW_MS = 2_000L

private fun Bitmap.asNotificationImage(): NotificationImage? =
    if (width <= 0 || height <= 0) null
    else NotificationImage(NotificationImageSource.FromBitmap(this), width, height)

private fun Uri.asNotificationImage(context: Context): NotificationImage? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        val stream = context.contentResolver.openInputStream(this) ?: return null
        // In bounds-only mode decodeStream returns null by design; the size lands in options.
        stream.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (e: Exception) {
        // The listener only holds a read grant while the notification is posted, and the provider
        // can refuse regardless.
        logger.d(e) { "couldn't read notification image" }
        return null
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return NotificationImage(NotificationImageSource.FromUri(this), options.outWidth, options.outHeight)
}
