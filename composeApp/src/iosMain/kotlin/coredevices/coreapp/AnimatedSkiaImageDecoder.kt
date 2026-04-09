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
import kotlinx.coroutines.sync.Mutex
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
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
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        return DecodeResult(
            image = AnimatedSkiaImage(codec = codec),
            isSampled = false,
        )
    }

    class Factory : Decoder.Factory {

        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!isGif(result.source.source())) return null
            return AnimatedSkiaImageDecoder(source = result.source)
        }
    }
}

private class AnimatedSkiaImage(
    private val codec: Codec,
) : coil3.Image {

    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + job)

    @OptIn(ExperimentalNativeApi::class)
    @Suppress("unused") // must be held to prevent cleaner itself from being collected
    private val cleaner = createCleaner(job) { it.cancel() }

    override val size: Long
        get() {
            var size = codec.imageInfo.computeMinByteSize().toLong()
            if (size <= 0L) size = 4L * codec.width * codec.height
            return size.coerceAtLeast(0)
        }

    override val width: Int get() = codec.width

    override val height: Int get() = codec.height

    override val shareable: Boolean get() = false

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

    // SkCodec resolves frame dependencies by recursively decoding all prior frames when
    // a non-sequential frame is requested. For a GIF with N frames each depending on the
    // previous, jumping to frame N causes O(N) recursion depth in SkCodec::handleFrameIndex,
    // which can exhaust the stack and corrupt the heap. Always decode sequentially to avoid
    // this. A mutex ensures the non-thread-safe Codec is never accessed concurrently.
    private val decodeMutex = Mutex()
    private var nextFrameToDecode = atomic(0)

    private val frameDurationsMs: List<Int> by lazy {
        codec.framesInfo.map { if (it.duration <= 0) DEFAULT_FRAME_DURATION else it.duration }
    }
    private var displayedFrameIndex = 0
    private var lastFrameAdvancedAt = TimeSource.Monotonic.markNow()

    private fun decodeNextFrame() = coroutineScope.launch(Dispatchers.Default) {
        if (!decodeMutex.tryLock()) return@launch
        try {
            val index = nextFrameToDecode.value
            val target = if (current.value) bitmapA else bitmapB
            codec.readPixels(target, index)
            target.notifyPixelsChanged()
            current.value = !current.value
            nextFrameToDecode.value = if (index == codec.frameCount - 1) 0 else index + 1
        } catch (e: Exception) {
            // ignore decode errors for individual frames
        } finally {
            decodeMutex.unlock()
        }
    }

    init {
        decodeNextFrame()
    }

    private var invalidateTick by mutableIntStateOf(0)

    override fun draw(canvas: Canvas) {
        if (codec.frameCount == 0) return

        canvas.drawImage(
            image = if (!current.value) imageA else imageB,
            left = 0f,
            top = 0f,
        )

        if (codec.frameCount > 1) {
            val frameDurationMs = frameDurationsMs.getOrElse(displayedFrameIndex) { DEFAULT_FRAME_DURATION }
            if (lastFrameAdvancedAt.elapsedNow().inWholeMilliseconds >= frameDurationMs) {
                displayedFrameIndex = if (displayedFrameIndex == codec.frameCount - 1) 0 else displayedFrameIndex + 1
                lastFrameAdvancedAt = TimeSource.Monotonic.markNow()
                decodeNextFrame()
            }
            invalidateTick++
        }
    }
}

private const val DEFAULT_FRAME_DURATION = 100

private val GIF_HEADER_87A = "GIF87a".encodeUtf8()
private val GIF_HEADER_89A = "GIF89a".encodeUtf8()

private fun isGif(source: BufferedSource): Boolean {
    return source.rangeEquals(0, GIF_HEADER_89A) ||
        source.rangeEquals(0, GIF_HEADER_87A)
}
