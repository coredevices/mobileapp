package io.rebble.libpebblecommon.notification

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.rebble.libpebblecommon.connection.AppContext
import kotlin.math.roundToInt

// Hardware canvas refuses to draw bitmaps over 100MB; some apps' icons rasterise far bigger.
private const val MAX_ICON_PX = 256

actual fun iconFor(packageName: String, appContext: AppContext): ImageBitmap? {
    return try {
        appContext.context.packageManager.getApplicationIcon(packageName)
            .toBoundedBitmap()
            .asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

private fun Drawable.toBoundedBitmap(): Bitmap {
    val largest = maxOf(intrinsicWidth, intrinsicHeight)
    if (largest <= 0) return toBitmap(MAX_ICON_PX, MAX_ICON_PX)
    val scale = minOf(1f, MAX_ICON_PX.toFloat() / largest)
    return toBitmap(
        width = (intrinsicWidth * scale).roundToInt().coerceAtLeast(1),
        height = (intrinsicHeight * scale).roundToInt().coerceAtLeast(1),
    )
}
