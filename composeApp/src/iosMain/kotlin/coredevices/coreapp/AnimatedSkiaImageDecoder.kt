package coredevices.coreapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use
import org.jetbrains.skia.AnimationFrameInfo
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.jetbrains.skia.Image as SkiaImage

/**
 * A Coil [Decoder] that uses Skia to decode and animate GIF images on non-Android platforms.
 * Based on https://github.com/coil-kt/coil/pull/2594#issuecomment-2780658070
 *
 * Uses double-buffered bitmaps with shared pixel memory for minimal allocation overhead.
 */
internal class AnimatedSkiaImageDecoder(
    private val source: ImageSource,
    private val timeSource: TimeSource,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        val scope = CoroutineScope(Dispatchers.Default + Job())
        return DecodeResult(
            image = AnimatedSkiaImage(
                codec = codec,
                coroutineScope = scope,
                timeSource = timeSource,
            ),
            isSampled = false,
        )
    }

    class Factory(
        private val timeSource: TimeSource = TimeSource.Monotonic,
    ) : Decoder.Factory {

        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!isGif(result.source.source())) return null
            return AnimatedSkiaImageDecoder(
                source = result.source,
                timeSource = timeSource,
            )
        }
    }
}

private class AnimatedSkiaImage(
    private val codec: Codec,
    private val coroutineScope: CoroutineScope,
    private val timeSource: TimeSource,
) : coil3.Image {

    override val size: Long
        get() {
            var size = codec.imageInfo.computeMinByteSize().toLong()
            if (size <= 0L) size = 4L * codec.width * codec.height
            return size.coerceAtLeast(0)
        }

    override val width: Int get() = codec.width

    override val height: Int get() = codec.height

    override val shareable: Boolean get() = false

    private val frameDurationsMs: List<Int> by lazy {
        codec.framesInfo.map { it.safeFrameDuration }
    }

    private val singleIterationDurationMs: Int by lazy {
        frameDurationsMs.sum()
    }

    // Double-buffer approach: mark bitmaps immutable so SkiaImage shares pixel memory,
    // then reuse them for decoding. notifyPixelsChanged() updates the shared image in-place.
    private val bitmapA = Bitmap().apply {
        allocPixels(codec.imageInfo)
        setImmutable()
    }
    private val bitmapB = Bitmap().apply {
        allocPixels(codec.imageInfo)
        setImmutable()
    }
    private val imageA = SkiaImage.makeFromBitmap(bitmapA)
    private val imageB = SkiaImage.makeFromBitmap(bitmapB)
    private var current = atomic(false)

    private fun decodeFrame(index: Int) = coroutineScope.launch(Dispatchers.Default) {
        val target = if (current.value) bitmapA else bitmapB
        codec.readPixels(target, index)
        target.notifyPixelsChanged()
        current.value = !current.value
    }

    init {
        decodeFrame(0)
    }

    private var invalidateTick by mutableIntStateOf(0)
    private var animationStartTime: TimeMark? = null

    override fun draw(canvas: Canvas) {
        if (codec.frameCount == 0) return

        if (codec.frameCount == 1) {
            canvas.drawImage(imageA, 0f, 0f)
            return
        }

        val startTime = animationStartTime
            ?: timeSource.markNow().also { animationStartTime = it }

        val totalElapsedMs = startTime.elapsedNow().inWholeMilliseconds

        val frameIndexToDraw = getFrameIndexToDraw(totalElapsedMs)

        canvas.drawImage(
            image = if (!current.value) imageA else imageB,
            left = 0f,
            top = 0f,
        )

        val nextFrameIndex = if (frameIndexToDraw == codec.frameCount - 1) 0 else frameIndexToDraw + 1
        decodeFrame(nextFrameIndex)
        invalidateTick++
    }

    private fun getFrameIndexToDraw(totalElapsedMs: Long): Int {
        if (singleIterationDurationMs <= 0) return 0
        val currentIterationElapsedMs = totalElapsedMs % singleIterationDurationMs
        var accumulated = 0
        for ((index, duration) in frameDurationsMs.withIndex()) {
            if (accumulated > currentIterationElapsedMs) {
                return (index - 1).coerceAtLeast(0)
            }
            accumulated += duration
        }
        return frameDurationsMs.lastIndex
    }
}

private val AnimationFrameInfo.safeFrameDuration: Int
    get() = duration.let { if (it <= 0) DEFAULT_FRAME_DURATION else it }

private const val DEFAULT_FRAME_DURATION = 100

private val GIF_HEADER_87A = "GIF87a".encodeUtf8()
private val GIF_HEADER_89A = "GIF89a".encodeUtf8()

private fun isGif(source: BufferedSource): Boolean {
    return source.rangeEquals(0, GIF_HEADER_89A) ||
        source.rangeEquals(0, GIF_HEADER_87A)
}
