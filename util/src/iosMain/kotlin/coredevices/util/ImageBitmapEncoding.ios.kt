package coredevices.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlin.io.encoding.Base64
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual fun imageBitmapToPngBase64(bitmap: ImageBitmap): String {
    val skiaBitmap = bitmap.asSkiaBitmap()
    skiaBitmap.setAlphaType(ColorAlphaType.OPAQUE)
    val data = Image.makeFromBitmap(skiaBitmap).encodeToData(EncodedImageFormat.PNG) ?: return ""
    return Base64.encode(data.bytes)
}
