package coredevices.util

import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

actual fun imageBitmapToPngBase64(bitmap: ImageBitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
