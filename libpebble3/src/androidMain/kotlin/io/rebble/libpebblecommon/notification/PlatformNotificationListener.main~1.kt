package io.rebble.libpebblecommon.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.image.PebbleBitmap
import io.rebble.libpebblecommon.util.toPebbleBitmap

actual fun iconFor(packageName: String, appContext: AppContext): PebbleBitmap? {
    return try {
        appContext.context.packageManager.getApplicationIcon(packageName).toBitmap().toPebbleBitmap()
    } catch (e: Exception) {
        null
    }
}

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        return this.bitmap
    }

    val bitmap = if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
        Bitmap.createBitmap(
            1,
            1,
            Bitmap.Config.ARGB_8888
        ) // Single color bitmap will be created of 1x1 pixel
    } else {
        Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
    }

    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)

    return bitmap
}
