package io.rebble.libpebblecommon.notification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.imaging.ImagingService
import io.rebble.libpebblecommon.imaging.NotificationImageProvider
import io.rebble.libpebblecommon.packets.Imaging
import io.rebble.libpebblecommon.imaging.encodeForWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.uuid.Uuid
import androidx.core.graphics.scale

/**
 * Disk cache of notification images, keyed by timeline item id. The watch pulls an image whenever
 * the card comes into view — long after the notification was posted, and possibly in a later
 * process — so the bytes have to outlive both.
 */
class NotificationImageStore(
    appContext: AppContext,
) : NotificationImageProvider {
    private val logger = Logger.withTag("NotificationImageStore")
    private val context = appContext.context
    private val dir = File(context.cacheDir, DIR_NAME)

    /** @return true if the image is cached, so the watch can be told to expect it. */
    suspend fun put(itemId: Uuid, image: NotificationImage): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = image.decode(context) ?: return@withContext false
            dir.mkdirs()
            fileFor(itemId).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
            logger.v { "cached ${bitmap.width}x${bitmap.height} image for $itemId" }
            prune()
            true
        } catch (e: Exception) {
            logger.w(e) { "failed to cache image for $itemId" }
            false
        }
    }

    override fun register(imagingService: ImagingService) {
        imagingService.registerHandler(Imaging.ImageType.NotificationImage) { request ->
            (request as? Imaging.NotificationImageRequest)?.let {
                image(it.itemId.get(), it.width.get().toInt(), it.height.get().toInt())
            }
        }
    }

    private suspend fun image(itemId: Uuid, width: Int, height: Int): EncodedImage? {
        val bitmap = withContext(Dispatchers.IO) {
            fileFor(itemId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        } ?: return null
        return withContext(Dispatchers.Default) { bitmap.encodeForWatch(width, height) }
    }

    private fun fileFor(itemId: Uuid) = File(dir, "$itemId.jpg")

    private fun prune() {
        val files = dir.listFiles() ?: return
        filesToEvict(files.toList(), MAX_CACHED).forEach { it.delete() }
    }

    companion object {
        private const val DIR_NAME = "notification-images"
        private const val JPEG_QUALITY = 85
        private const val MAX_CACHED = 32

        internal fun filesToEvict(files: List<File>, keep: Int): List<File> =
            files.sortedByDescending { it.lastModified() }.drop(keep)
    }
}

/**
 * The image as a bitmap no larger than [MAX_STORED_DIMENSION] on its longest side. Downscaling here
 * bounds both the cache on disk and the decode when the watch asks — the watch never wants more
 * than 300px and centre-crops anyway.
 */
private fun NotificationImage.decode(context: android.content.Context): Bitmap? {
    val sample = generateSequence(1) { it * 2 }
        .first { maxOf(width, height) / it <= MAX_STORED_DIMENSION }
    return when (source) {
        is NotificationImageSource.FromBitmap ->
            source.bitmap.takeIf { sample == 1 } ?: source.bitmap.scaledBy(sample)
        is NotificationImageSource.FromUri -> {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(source.uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            val orientation = context.contentResolver.openInputStream(source.uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            bitmap.applyExifOrientation(orientation)
        }
    }
}

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { setRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.scaledBy(sample: Int): Bitmap =
    scale(width / sample, height / sample)

private const val MAX_STORED_DIMENSION = 600
